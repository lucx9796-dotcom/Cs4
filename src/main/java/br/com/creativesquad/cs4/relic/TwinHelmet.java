package br.com.creativesquad.cs4.relic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Capacete Lendário dos Gêmeos: quem estiver com ele equipado divide a MESMA vida com os outros que também o usam
 * (dano e cura se espalham) e, se um morrer, os outros morrem com a mesma morte.
 */
public final class TwinHelmet {
    private static final Map<UUID, Float> LAST = new HashMap<>();
    private static final Set<UUID> KILLING = new HashSet<>();

    private TwinHelmet() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TwinHelmet::tick);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && !KILLING.contains(p.getUUID())) shareDeath(p, source);
            return true; // quem morreu primeiro morre normalmente
        });
    }

    private static boolean wears(ServerPlayer p) {
        return RelicRegistry.is(p.getItemBySlot(EquipmentSlot.HEAD), "capacete");
    }

    private static List<ServerPlayer> wearers(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isAlive() && !p.isSpectator() && !p.isCreative() && wears(p)) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- vida compartilhada

    private static void tick(MinecraftServer server) {
        List<ServerPlayer> w = wearers(server);
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer p : w) ids.add(p.getUUID());
        LAST.keySet().retainAll(ids);
        if (w.size() < 2) { LAST.clear(); return; }

        boolean fresh = false, damaged = false, healed = false;
        float low = Float.MAX_VALUE, high = -1f, lowest = Float.MAX_VALUE;
        for (ServerPlayer p : w) {
            float h = p.getHealth();
            lowest = Math.min(lowest, h);
            Float last = LAST.get(p.getUUID());
            if (last == null) { fresh = true; continue; }
            if (h < last - 0.001f) { damaged = true; low = Math.min(low, h); }
            else if (h > last + 0.001f) { healed = true; high = Math.max(high, h); }
        }

        float target;
        if (fresh) target = lowest;          // acabou de formar o par: todos ficam com a menor vida
        else if (damaged) target = low;      // dano em alguém: todos levam
        else if (healed) target = high;      // cura em alguém: todos recebem
        else target = -1f;

        for (ServerPlayer p : w) {
            if (target >= 0f) {
                float t = Math.min(target, p.getMaxHealth());
                float cur = p.getHealth();
                if (Math.abs(cur - t) > 0.001f) {
                    p.setHealth(t);
                    if (t < cur) ((ServerLevel) p.level()).broadcastEntityEvent(p, (byte) 2); // animação/som de dano
                }
            }
            LAST.put(p.getUUID(), p.getHealth());
        }
    }

    // ---------------------------------------------------------------- morte compartilhada

    private static void shareDeath(ServerPlayer dying, DamageSource source) {
        if (!wears(dying)) return;
        MinecraftServer server = ((ServerLevel) dying.level()).getServer();
        List<ServerPlayer> partners = new ArrayList<>();
        for (ServerPlayer q : wearers(server)) if (q != dying) partners.add(q);
        LAST.clear();
        for (ServerPlayer q : partners) {
            KILLING.add(q.getUUID());
            try {
                // mesma fonte de dano => mesma mensagem de morte ("X foi morto por Y")
                q.hurtServer((ServerLevel) q.level(), source, Float.MAX_VALUE);
                if (q.isAlive()) {                  // sobreviveu (totem?): a morte do gêmeo não pode ser evitada
                    q.setHealth(0.0f);
                    q.die(source);
                }
            } finally {
                KILLING.remove(q.getUUID());
            }
        }
    }
}
