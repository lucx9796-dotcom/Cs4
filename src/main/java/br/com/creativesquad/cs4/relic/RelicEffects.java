package br.com.creativesquad.cs4.relic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Efeitos passivos das relíquias:
 *  - Pena: segurando (qualquer mão), após 13 ticks dá Levitação XV por 3s;
 *  - Lampião: segurando (qualquer mão) => Invisibilidade XI (a armadura e os itens das mãos somem no cliente na hora,
 *    o jogador some 10 ticks depois; ao largar, a armadura volta e o efeito acaba 10 ticks depois);
 *  - Batata do Bobo: no inventário => Lentidão V;
 *  - Capacete dos Gêmeos: equipado => Respiração Aquática II por 4s em loop (vida compartilhada: ver TwinHelmet);
 *  - Bota: equipada, após 15 ticks => Velocidade V por 5s em loop;
 *  - Espada do Caçador: na mão => Mau Presságio VI por 4s;
 *  - Tridente: ao acertar uma entidade => Fadiga de Mineração II por 10s.
 */
public final class RelicEffects {
    /** Pena: true = repete a levitação enquanto segurar; false = só uma vez a cada vez que pegar a pena. */
    private static final boolean FEATHER_LOOP = false;

    private static final int FEATHER_DELAY = 13;
    private static final int LANTERN_DELAY = 10;
    private static final int BOOTS_DELAY = 15;

    private static final Map<UUID, Integer> FEATHER_START = new HashMap<>();
    private static final Map<UUID, Integer> LANTERN_START = new HashMap<>();
    private static final Map<UUID, Integer> LANTERN_END = new HashMap<>();
    private static final Map<UUID, Integer> BOOTS_START = new HashMap<>();
    private static final Map<String, Set<UUID>> ACTIVE = new HashMap<>();

    private RelicEffects() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                feather(p, tick);
                lantern(p, tick);
                boots(p, tick);
                if (tick % 10 == 0) slowTick(p);
            }
        });

        // Tridente: acertar (corpo a corpo ou arremessado) uma entidade => Fadiga de Mineração II por 10s
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0) return;
            ItemStack weapon = source.getWeaponItem();
            boolean trident = weapon != null && RelicRegistry.is(weapon, "tridente");
            if (!trident && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
                trident = RelicRegistry.is(attacker.getMainHandItem(), "tridente");
            }
            if (trident) entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1));
        });
    }

    // ------------------------------------------------------------------ helpers

    static boolean holding(LivingEntity e, String id) {
        return RelicRegistry.is(e.getMainHandItem(), id) || RelicRegistry.is(e.getOffhandItem(), id);
    }

    static boolean inInventory(ServerPlayer p, String id) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (RelicRegistry.is(p.getInventory().getItem(i), id)) return true;
        }
        return false;
    }

    /**
     * Mantém o efeito enquanto a condição valer (a cada 10 ticks renova com a duração dada, sem piscar)
     * e, se {@code removeOnEnd}, tira o efeito quando a condição deixa de valer.
     */
    static void sustain(ServerPlayer p, String key, boolean cond, Holder<MobEffect> effect, int amp, int duration, boolean removeOnEnd) {
        Set<UUID> set = ACTIVE.computeIfAbsent(key, k -> new HashSet<>());
        if (cond) {
            p.addEffect(new MobEffectInstance(effect, duration, amp, false, false, true));
            set.add(p.getUUID());
        } else if (set.remove(p.getUUID()) && removeOnEnd) {
            p.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ a cada 10 ticks

    private static void slowTick(ServerPlayer p) {
        // Batata do Bobo: Lentidão V enquanto estiver no inventário
        sustain(p, "batata", inInventory(p, "batata"), MobEffects.SLOWNESS, 4, 40, true);
        // Espada do Caçador: Mau Presságio VI por 4s enquanto estiver na mão
        sustain(p, "espada", holding(p, "espada"), MobEffects.BAD_OMEN, 5, 80, false);
        // Capacete dos Gêmeos: Respiração Aquática II por 4s em loop
        sustain(p, "capacete", RelicRegistry.is(p.getItemBySlot(EquipmentSlot.HEAD), "capacete"), MobEffects.WATER_BREATHING, 1, 80, true);
    }

    // ------------------------------------------------------------------ Pena

    private static void feather(ServerPlayer p, int tick) {
        UUID id = p.getUUID();
        if (!holding(p, "pena")) { FEATHER_START.remove(id); return; }
        Integer start = FEATHER_START.get(id);
        if (start == null) { FEATHER_START.put(id, tick); return; }
        int held = tick - start;
        if (held == FEATHER_DELAY || (FEATHER_LOOP && held > FEATHER_DELAY && !p.hasEffect(MobEffects.LEVITATION))) {
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 14)); // nível XV por 3s
        }
    }

    // ------------------------------------------------------------------ Lampião (Invisibilidade XI)
    // A armadura/itens das mãos são escondidos no cliente (ver mixin client.AvatarRendererMixin e ArmorHider).

    private static void lantern(ServerPlayer p, int tick) {
        UUID id = p.getUUID();
        boolean held = holding(p, "lampiao");
        Integer start = LANTERN_START.get(id);
        if (held) {
            LANTERN_END.remove(id);
            if (start == null) { LANTERN_START.put(id, tick); return; }          // armadura/mãos somem já ao pegar
            if (tick - start >= LANTERN_DELAY && (tick % 20 == 0 || !p.hasEffect(MobEffects.INVISIBILITY))) {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 220, 10, false, false, false)); // 10 ticks depois: o jogador some
            }
        } else if (start != null) {
            LANTERN_START.remove(id);                                            // armadura volta ao largar o lampião
            LANTERN_END.put(id, tick + LANTERN_DELAY);                           // 10 ticks depois: acaba o efeito
        }
        Integer end = LANTERN_END.get(id);
        if (end != null && tick >= end) {
            LANTERN_END.remove(id);
            p.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    // ------------------------------------------------------------------ Bota (Velocidade V em loop)

    private static void boots(ServerPlayer p, int tick) {
        UUID id = p.getUUID();
        boolean worn = RelicRegistry.is(p.getItemBySlot(EquipmentSlot.FEET), "bota");
        Set<UUID> active = ACTIVE.computeIfAbsent("bota", k -> new HashSet<>());
        if (!worn) {
            BOOTS_START.remove(id);
            if (active.remove(id)) p.removeEffect(MobEffects.SPEED);
            return;
        }
        Integer start = BOOTS_START.get(id);
        if (start == null) { BOOTS_START.put(id, tick); return; }
        if (tick - start >= BOOTS_DELAY && (tick % 20 == 0 || !p.hasEffect(MobEffects.SPEED))) {
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 4, false, false, true)); // nível V por 5s, renovado em loop
            active.add(id);
        }
    }
}
