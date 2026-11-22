package br.com.creativesquad.cs4.relic;

import br.com.creativesquad.cs4.Cs4;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Relíquias com habilidade de botão direito:
 *  - Dragão Lendário (cabeça de dragão): bafo de dragão;
 *  - Escudo Lendário: rajada sônica do Warden (cooldown 10s);
 *  - Poção Lendária: lança uma poção persistente de Cegueira por 10s (cooldown 5s);
 *  - Relógio Lendário: GUI para virar outro jogador;
 *  - Bússola Lendária: GUI para rastrear um jogador.
 */
public final class RelicAbilities {
    private static final Set<String> ACTIVE = Set.of("dragao", "escudo", "pocao", "relogio", "bussola");

    /** Cooldowns em ticks (20 ticks = 1s). */
    private static final int DRAGON_COOLDOWN = 60;   // não pedido: evita spam do bafo (coloque 0 para tirar)
    private static final int SHIELD_COOLDOWN = 200;  // 10s
    private static final int POTION_COOLDOWN = 100;  // 5s

    /** Dragão: dano a cada 20 ticks para quem está na nuvem, duração e raio da nuvem. */
    private static final float BREATH_DAMAGE = 4.0f;
    private static final int BREATH_DURATION = 200;
    private static final double BREATH_RADIUS = 3.0;

    /** DRAGON_BREATH agora é ParticleType<PowerParticleOption>: precisa de uma opção (força 1.0). */
    private static final PowerParticleOption BREATH = PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f);

    private record Fireball(ServerLevel level, UUID owner, double[] pos, Vec3 dir, int[] age) {}
    private record Cloud(ServerLevel level, UUID owner, Vec3 center, int[] age) {}
    private static final List<Fireball> FIREBALLS = new ArrayList<>();
    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private RelicAbilities() {}

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> use(player, world, hand));

        // Cabeça de dragão e lampião são blocos: não podem ser colocados.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack s = player.getItemInHand(hand);
            String id = RelicRegistry.idOf(s);
            if (id.isEmpty() || !(s.getItem() instanceof BlockItem)) return InteractionResult.PASS;
            if (id.equals("dragao")) return use(player, world, hand);   // clicar num bloco também solta o bafo
            return InteractionResult.FAIL;
        });

        ServerTickEvents.END_SERVER_TICK.register(RelicAbilities::tickProjectiles);
    }

    // ------------------------------------------------------------------ uso com botão direito

    private static InteractionResult use(Player p, Level w, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        String id = RelicRegistry.idOf(s);
        if (!ACTIVE.contains(id)) return InteractionResult.PASS;
        if (p.getCooldowns().isOnCooldown(s)) return InteractionResult.FAIL;
        if (w.isClientSide()) return InteractionResult.SUCCESS;

        ServerPlayer sp = (ServerPlayer) p;
        ServerLevel sw = (ServerLevel) w;
        MinecraftServer server = sw.getServer();
        switch (id) {
            case "dragao" -> {
                launchBreath(sp, sw);
                if (DRAGON_COOLDOWN > 0) sp.getCooldowns().addCooldown(s, DRAGON_COOLDOWN);
            }
            case "escudo" -> {
                sonicBoom(sp, sw);
                sp.getCooldowns().addCooldown(s, SHIELD_COOLDOWN);
            }
            case "pocao" -> {
                throwLingeringBlindness(sp, sw, server);
                sp.getCooldowns().addCooldown(s, POTION_COOLDOWN);
            }
            case "relogio" -> PlayerSelectMenu.open(sp, (clicker, target) -> {
                Disguise.set(server, clicker, target);
                clicker.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        clicker.getUUID().equals(target.getUUID())
                                ? "§eVocê voltou a ser você mesmo."
                                : "§eVocê virou §f" + target.getGameProfile().name() + "§e."));
            });
            case "bussola" -> PlayerSelectMenu.open(sp, (clicker, target) -> {
                ItemStack compass = clicker.getItemInHand(hand);
                if (!RelicRegistry.is(compass, "bussola")) compass = findCompass(clicker);
                if (compass.isEmpty()) return;
                CompassTracking.setTarget(compass, target);
                // som de XP para todos por perto
                ServerLevel lvl = (ServerLevel) clicker.level();
                lvl.playSound(null, clicker.getX(), clicker.getY(), clicker.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                clicker.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§eRastreando §f" + target.getGameProfile().name() + "§e."));
            });
            default -> { return InteractionResult.PASS; }
        }
        return InteractionResult.SUCCESS;
    }

    private static ItemStack findCompass(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (RelicRegistry.is(s, "bussola")) return s;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ Escudo: rajada do Warden

    private static void sonicBoom(ServerPlayer p, ServerLevel w) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(15));
        for (int i = 1; i <= 15; i++) {
            Vec3 pt = eye.add(look.scale(i));
            w.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 1.0f);
        AABB area = p.getBoundingBox().expandTowards(look.scale(15)).inflate(1.0);
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, area, x -> x != p && x.isAlive())) {
            if (e.getBoundingBox().inflate(0.6).clip(eye, end).isPresent()) {
                // mesmo dano (10) e empurrão da rajada do Warden
                e.hurtServer(w, w.damageSources().sonicBoom(p), 10.0f);
                double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                e.push(look.x * 2.5 * res, look.y * 0.5 * res, look.z * 2.5 * res);
                if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
            }
        }
    }

    // ------------------------------------------------------------------ Poção: lingering de cegueira

    private static final int POTION_COLOR = 15925418; // mesma cor da Poção Lendária

    private static void throwLingeringBlindness(ServerPlayer p, ServerLevel w, MinecraftServer server) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 start = eye.add(look.scale(0.5)).add(0, -0.15, 0);
        Vec3 motion = look.scale(0.7).add(0, 0.1, 0);
        int[] owner = uuidInts(p.getUUID());
        // A poção persistente reduz a duração dos efeitos a 1/4 na nuvem: 800 ticks na poção = 10s de Cegueira na nuvem.
        String cmd = String.format(Locale.ROOT,
                "execute in %s run summon minecraft:lingering_potion %.3f %.3f %.3f "
                        + "{Item:{id:\"minecraft:lingering_potion\",count:1,components:{\"minecraft:potion_contents\":"
                        + "{custom_color:%d,custom_effects:[{id:\"minecraft:blindness\",duration:800,amplifier:0}]}}},"
                        + "Owner:[I;%d,%d,%d,%d],Motion:[%.4fd,%.4fd,%.4fd]}",
                w.dimension().identifier(), start.x, start.y, start.z, POTION_COLOR,
                owner[0], owner[1], owner[2], owner[3], motion.x, motion.y, motion.z);
        try {
            server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack().withSuppressedOutput());
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5f, 0.4f);
        } catch (Exception e) {
            Cs4.LOGGER.error("Falha ao lançar a poção persistente: {}", e.getMessage());
        }
    }

    private static int[] uuidInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    // ------------------------------------------------------------------ Dragão: bafo (bola que vira nuvem)

    private static void launchBreath(ServerPlayer p, ServerLevel w) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getViewVector(1.0f);
        FIREBALLS.add(new Fireball(w, p.getUUID(), new double[]{eye.x + dir.x, eye.y + dir.y - 0.2, eye.z + dir.z}, dir, new int[]{0}));
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void tickProjectiles(MinecraftServer server) {
        // bolas voando
        for (int i = FIREBALLS.size() - 1; i >= 0; i--) {
            Fireball f = FIREBALLS.get(i);
            ServerLevel w = f.level();
            boolean hit = false;
            for (int step = 0; step < 2 && !hit; step++) {          // 1 bloco por passo, 2 passos por tick
                double nx = f.pos()[0] + f.dir().x * 0.5, ny = f.pos()[1] + f.dir().y * 0.5, nz = f.pos()[2] + f.dir().z * 0.5;
                BlockPos bp = BlockPos.containing(nx, ny, nz);
                BlockState st = w.getBlockState(bp);
                if (!st.getCollisionShape(w, bp).isEmpty()) { hit = true; break; }
                f.pos()[0] = nx; f.pos()[1] = ny; f.pos()[2] = nz;
                AABB box = new AABB(nx - 0.6, ny - 0.6, nz - 0.6, nx + 0.6, ny + 0.6, nz + 0.6);
                if (!w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.getUUID().equals(f.owner())).isEmpty()) hit = true;
            }
            w.sendParticles(BREATH, f.pos()[0], f.pos()[1], f.pos()[2], 6, 0.15, 0.15, 0.15, 0.01);
            f.age()[0]++;
            if (hit || f.age()[0] > 40) {
                Vec3 c = new Vec3(f.pos()[0], f.pos()[1], f.pos()[2]);
                CLOUDS.add(new Cloud(w, f.owner(), c, new int[]{0}));
                w.playSound(null, c.x, c.y, c.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.0f);
                FIREBALLS.remove(i);
            }
        }
        // nuvens no chão
        for (int i = CLOUDS.size() - 1; i >= 0; i--) {
            Cloud c = CLOUDS.get(i);
            ServerLevel w = c.level();
            int age = c.age()[0]++;
            double radius = BREATH_RADIUS * (1.0 - 0.5 * age / (double) BREATH_DURATION);   // encolhe até a metade
            if (age % 2 == 0) {
                for (int n = 0; n < 8; n++) {
                    double a = w.getRandom().nextDouble() * Math.PI * 2, d = Math.sqrt(w.getRandom().nextDouble()) * radius;
                    w.sendParticles(BREATH, c.center().x + Math.cos(a) * d, c.center().y + 0.1, c.center().z + Math.sin(a) * d,
                            1, 0, 0.02, 0, 0.005);
                }
            }
            if (age % 20 == 0) {
                AABB box = new AABB(c.center().x - radius, c.center().y - 1.0, c.center().z - radius,
                        c.center().x + radius, c.center().y + 2.0, c.center().z + radius);
                ServerPlayer owner = server.getPlayerList().getPlayer(c.owner());
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !x.getUUID().equals(c.owner()))) {
                    double dx = e.getX() - c.center().x, dz = e.getZ() - c.center().z;
                    if (dx * dx + dz * dz > radius * radius) continue;
                    e.hurtServer(w, owner != null ? w.damageSources().indirectMagic(owner, owner) : w.damageSources().magic(), BREATH_DAMAGE);
                }
            }
            if (age >= BREATH_DURATION) CLOUDS.remove(i);
        }
    }
}
