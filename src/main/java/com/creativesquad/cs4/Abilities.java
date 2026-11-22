package com.creativesquad.cs4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Abilities {
    private Abilities() {}

    // ---- estado por jogador ----
    private static final Map<UUID, Integer> featherTicks = new HashMap<>();
    private static final Map<UUID, Integer> bootsTicks = new HashMap<>();
    private static final Map<UUID, Float> lastHealth = new HashMap<>();
    private static final Map<UUID, Cloak> cloaks = new HashMap<>();
    private static boolean syncing = false;

    private static final class Cloak { boolean active; int ticks; int release = -1; }

    public static void cleanup(UUID id) {
        featherTicks.remove(id); bootsTicks.remove(id); lastHealth.remove(id); cloaks.remove(id);
        Disguises.clear(id);
    }

    // ---- helpers ----
    private static boolean holds(Player p, String id) {
        return Relics.idOf(p.getMainHandItem()).equals(id) || Relics.idOf(p.getOffhandItem()).equals(id);
    }

    private static void refresh(ServerPlayer p, Holder<MobEffect> eff, int dur, int amp) {
        MobEffectInstance cur = p.getEffect(eff);
        if (cur == null || cur.getAmplifier() != amp || cur.getDuration() < 20) {
            p.addEffect(new MobEffectInstance(eff, dur, amp, false, false, true));
        }
    }

    // ---- botao direito ----
    public static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = Relics.idOf(stack);
        if (id.isEmpty()) return InteractionResult.PASS;

        switch (id) {
            case "dragon_head", "legendary_shield", "legendary_potion", "legendary_clock", "legendary_compass" -> {
                if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl
                        && !sp.getCooldowns().isOnCooldown(stack)) {
                    switch (id) {
                        case "dragon_head" -> dragonBreath(sp, sl, stack);
                        case "legendary_shield" -> sonicBoom(sp, sl, stack);
                        case "legendary_potion" -> lingeringBlindness(sp, sl, stack);
                        case "legendary_clock" -> RelicsGui.openPlayerSelector(sp, Abilities::pickDisguise);
                        case "legendary_compass" -> RelicsGui.openPlayerSelector(sp,
                            (me, target) -> pickCompassTarget(me, hand, target));
                        default -> { }
                    }
                }
                return InteractionResult.SUCCESS; // cancela equipar cabeca / levantar escudo / arremessar pocao
            }
            default -> { return InteractionResult.PASS; }
        }
    }

    private static void dragonBreath(ServerPlayer p, ServerLevel sl, ItemStack stack) {
        Vec3 pos = p.getEyePosition().add(p.getLookAngle().scale(3));
        AreaEffectCloud cloud = new AreaEffectCloud(sl, pos.x, pos.y, pos.z);
        cloud.setOwner(p);
        cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
        cloud.setRadius(3.0F);
        cloud.setDuration(100);
        cloud.setRadiusPerTick(-3.0F / 100);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
        sl.addFreshEntity(cloud);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1f, 1f);
        p.getCooldowns().addCooldown(stack, 40);
    }

    private static void sonicBoom(ServerPlayer p, ServerLevel sl, ItemStack stack) {
        final double range = 15.0;
        Vec3 start = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        for (int i = 1; i <= (int) range; i++) {
            Vec3 v = start.add(dir.scale(i));
            sl.sendParticles(ParticleTypes.SONIC_BOOM, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        AABB box = p.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.5);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(start);
            double proj = to.dot(dir);
            if (proj < 0 || proj > range) continue;
            if (to.subtract(dir.scale(proj)).length() > 1.5) continue;
            t.hurtServer(sl, sl.damageSources().sonicBoom(p), 10.0F);
            double res = 1.0 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            t.push(dir.x * 2.5 * res, dir.y * 0.5 * res, dir.z * 2.5 * res);
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 1f);
        p.getCooldowns().addCooldown(stack, 200); // 10s
    }

    private static void lingeringBlindness(ServerPlayer p, ServerLevel sl, ItemStack stack) {
        ItemStack thrown = new ItemStack(Items.LINGERING_POTION);
        thrown.set(DataComponents.POTION_CONTENTS, new PotionContents(
            Optional.empty(), Optional.of(Relics.POTION_COLOR),
            List.of(new MobEffectInstance(MobEffects.BLINDNESS, 200)), Optional.empty()));
        Projectile.spawnProjectileFromRotation(ThrownLingeringPotion::new, sl, thrown, p, -20.0F, 0.5F, 1.0F);
        p.getCooldowns().addCooldown(stack, 100); // 5s
    }

    // ---- bussola ----
    private static void pickCompassTarget(ServerPlayer me, InteractionHand hand, ServerPlayer target) {
        ItemStack s = me.getItemInHand(hand);
        if (!Relics.idOf(s).equals("legendary_compass")) {
            s = Relics.idOf(me.getMainHandItem()).equals("legendary_compass") ? me.getMainHandItem()
              : me.getOffhandItem();
        }
        if (!Relics.idOf(s).equals("legendary_compass")) return;

        CompoundTag tag = s.get(DataComponents.CUSTOM_DATA).copyTag();
        tag.putString(Relics.TARGET_TAG, target.getUUID().toString());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateTracker(s, target);
        me.sendSystemMessage(Component.literal("Rastreando " + target.getGameProfile().name()));
        // som de XP para todos por perto
        me.level().playSound(null, me.getX(), me.getY(), me.getZ(),
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void updateTracker(ItemStack compass, ServerPlayer target) {
        compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
            Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition())), false));
    }

    // ---- relogio: disfarce de skin + nome ----
    private static void pickDisguise(ServerPlayer me, ServerPlayer target) {
        if (target.getUUID().equals(me.getUUID())) {
            Disguises.set(me, null);
            me.sendSystemMessage(Component.literal("Disfarce removido."));
        } else {
            Disguises.set(me, target);
            me.sendSystemMessage(Component.literal("Você agora é " + target.getGameProfile().name()
                + ". Clique na sua própria cabeça para voltar ao normal."));
        }
    }

    // ---- tick ----
    public static void tick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer p : players) {
            UUID id = p.getUUID();

            // Pena: 13 ticks na mao -> Levitacao 15 por 3s
            if (holds(p, "legendary_feather")) {
                int t = featherTicks.merge(id, 1, Integer::sum);
                if (t >= 13) refresh(p, MobEffects.LEVITATION, 60, 14);
            } else featherTicks.remove(id);

            // Espada: na mao -> Mau Agouro 6 por 4s
            if (Relics.idOf(p.getMainHandItem()).equals("hunter_sword")) refresh(p, MobEffects.BAD_OMEN, 80, 5);

            // Batata: no inventario -> Lentidao 5
            if (inventoryHas(p, "poison_potato")) refresh(p, MobEffects.SLOWNESS, 40, 4);

            // Botas: 15 ticks equipadas -> Velocidade 5 por 5s em loop
            if (Relics.idOf(p.getItemBySlot(EquipmentSlot.FEET)).equals("legendary_boots")) {
                int t = bootsTicks.merge(id, 1, Integer::sum);
                if (t >= 15) refresh(p, MobEffects.SPEED, 100, 4);
            } else bootsTicks.remove(id);

            // Capacete: Respiracao Aquatica 2 por 4s em loop
            if (isTwin(p)) refresh(p, MobEffects.WATER_BREATHING, 80, 1);

            tickCloak(p);

            // Bussola: segue o alvo
            if (server.getTickCount() % 10 == 0) {
                for (ItemStack s : new ItemStack[] { p.getMainHandItem(), p.getOffhandItem() }) {
                    if (!Relics.idOf(s).equals("legendary_compass")) continue;
                    String u = s.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr(Relics.TARGET_TAG, "");
                    if (u.isEmpty()) continue;
                    try {
                        ServerPlayer tgt = server.getPlayerList().getPlayer(UUID.fromString(u));
                        if (tgt != null) updateTracker(s, tgt);
                    } catch (IllegalArgumentException ignored) { }
                }
            }

        }

        tickTwins(players);
    }

    private static boolean inventoryHas(Player p, String id) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (Relics.idOf(inv.getItem(i)).equals(id)) return true;
        }
        return false;
    }

    // ---- Lampiao: invisibilidade 11 + equipamento escondido ----
    private static void tickCloak(ServerPlayer p) {
        Cloak c = cloaks.computeIfAbsent(p.getUUID(), k -> new Cloak());
        if (holds(p, "soul_lantern")) {
            c.release = -1;
            if (!c.active) { c.active = true; c.ticks = 0; sendGear(p, true); } // armadura e maos somem
            c.ticks++;
            if (c.ticks >= 10) {                                                // 10 ticks depois, o jogador some
                MobEffectInstance cur = p.getEffect(MobEffects.INVISIBILITY);
                if (cur == null || cur.getAmplifier() != 10 || cur.getDuration() < 20) {
                    p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 10, false, false, false));
                }
            }
            if (c.ticks % 20 == 0) sendGear(p, true); // reaplica (novos observadores, troca de item)
        } else if (c.active) {
            if (c.release < 0) { c.release = 0; sendGear(p, false); } // armadura volta primeiro
            if (++c.release >= 10) {                                  // depois o efeito acaba
                c.active = false;
                p.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    private static boolean keepHead(ItemStack s) {
        return s.is(Items.CARVED_PUMPKIN) || s.is(Items.PLAYER_HEAD) || s.is(Items.ZOMBIE_HEAD)
            || s.is(Items.SKELETON_SKULL) || s.is(Items.WITHER_SKELETON_SKULL)
            || s.is(Items.CREEPER_HEAD) || s.is(Items.DRAGON_HEAD) || s.is(Items.PIGLIN_HEAD);
    }

    /** hide=true: esconde armadura e itens das maos (exceto elytra, cabecas de mob/jogador e abobora).
     *  Para o proprio jogador so a armadura some; os itens da mao continuam normais na tela dele. */
    private static void sendGear(ServerPlayer p, boolean hide) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack e = ItemStack.EMPTY;

        ItemStack h = hide && !keepHead(head) ? e : head.copy();
        ItemStack c = hide && !chest.is(Items.ELYTRA) ? e : chest.copy();
        ItemStack l = hide ? e : p.getItemBySlot(EquipmentSlot.LEGS).copy();
        ItemStack f = hide ? e : p.getItemBySlot(EquipmentSlot.FEET).copy();

        List<Pair<EquipmentSlot, ItemStack>> others = new ArrayList<>();
        others.add(Pair.of(EquipmentSlot.MAINHAND, hide ? e : p.getMainHandItem().copy()));
        others.add(Pair.of(EquipmentSlot.OFFHAND, hide ? e : p.getOffhandItem().copy()));
        others.add(Pair.of(EquipmentSlot.HEAD, h));
        others.add(Pair.of(EquipmentSlot.CHEST, c));
        others.add(Pair.of(EquipmentSlot.LEGS, l));
        others.add(Pair.of(EquipmentSlot.FEET, f));

        List<Pair<EquipmentSlot, ItemStack>> self = new ArrayList<>();
        self.add(Pair.of(EquipmentSlot.HEAD, h));
        self.add(Pair.of(EquipmentSlot.CHEST, c));
        self.add(Pair.of(EquipmentSlot.LEGS, l));
        self.add(Pair.of(EquipmentSlot.FEET, f));

        p.connection.send(new ClientboundSetEquipmentPacket(p.getId(), self));
        ClientboundSetEquipmentPacket pk = new ClientboundSetEquipmentPacket(p.getId(), others);
        for (ServerPlayer o : ((ServerLevel) p.level()).players()) {
            if (o != p && o.distanceToSqr(p) < 96 * 96) o.connection.send(pk);
        }
    }

    // ---- Capacete dos Gemeos ----
    private static boolean isTwin(Player p) {
        return Relics.idOf(p.getItemBySlot(EquipmentSlot.HEAD)).equals("twin_helmet");
    }

    private static void tickTwins(List<ServerPlayer> players) {
        List<ServerPlayer> twins = players.stream().filter(Abilities::isTwin).toList();
        lastHealth.keySet().retainAll(twins.stream().map(ServerPlayer::getUUID).toList());

        if (twins.size() >= 2) {
            Map<UUID, Float> gain = new HashMap<>();
            for (ServerPlayer w : twins) {
                Float last = lastHealth.get(w.getUUID());
                gain.put(w.getUUID(), last == null ? 0f : Math.max(0f, w.getHealth() - last));
            }
            for (ServerPlayer o : twins) {
                float best = 0f;
                for (ServerPlayer w : twins) if (w != o) best = Math.max(best, gain.get(w.getUUID()));
                float extra = best - gain.get(o.getUUID());
                if (extra > 0f) o.setHealth(Math.min(o.getMaxHealth(), o.getHealth() + extra));
            }
        }
        for (ServerPlayer w : twins) lastHealth.put(w.getUUID(), w.getHealth());
    }

    // ---- dano: vida compartilhada + efeito do tridente ----
    public static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
        // Tridente: fadiga 2 por 10s
        ItemStack w = source.getWeaponItem();
        if ((w == null || w.isEmpty()) && source.getEntity() instanceof LivingEntity a) w = a.getMainHandItem();
        if (w != null && Relics.idOf(w).equals("legendary_trident")) {
            entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1));
        }

        // Gemeos: o dano se espalha
        if (blocked || syncing || taken <= 0f) return;
        if (entity instanceof ServerPlayer sp && isTwin(sp) && sp.level() instanceof ServerLevel sl) {
            syncing = true;
            try {
                for (ServerPlayer o : sl.getServer().getPlayerList().getPlayers()) {
                    if (o != sp && isTwin(o)) o.hurtServer((ServerLevel) o.level(), source, taken);
                }
            } finally { syncing = false; }
        }
    }

    /** Gemeos: se um morre, o outro morre com a mesma morte. */
    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (syncing) return true;
        if (entity instanceof ServerPlayer sp && isTwin(sp) && sp.level() instanceof ServerLevel sl) {
            syncing = true;
            try {
                for (ServerPlayer o : sl.getServer().getPlayerList().getPlayers()) {
                    if (o == sp || !isTwin(o)) continue;
                    ServerLevel ol = (ServerLevel) o.level();
                    o.hurtServer(ol, source, Float.MAX_VALUE);
                    if (o.isAlive()) o.kill(ol);
                }
            } finally { syncing = false; }
        }
        return true;
    }
}
