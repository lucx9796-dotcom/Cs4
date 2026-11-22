package com.creativesquad.cs4;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Disfarce do Relogio. O jogador mantem o UUID real, mas todos (inclusive ele) passam a receber
 * um GameProfile com o NOME e a SKIN (propriedade "textures") do alvo.
 * - tab, /tell, report e tab-complete do chat leem o PlayerInfo -> ja mostram o nome falso
 * - nametag e skin do corpo exigem recriar a entidade para os observadores (repair)
 * - nome no chat / mensagens de morte: PlayerNameMixin
 * - /tell NomeFalso: PlayerListMixin
 */
public final class Disguises {
    private static final Map<UUID, GameProfile> FAKE = new ConcurrentHashMap<>();

    private Disguises() {}

    public static GameProfile fakeProfile(UUID id) { return FAKE.get(id); }

    public static String fakeName(UUID id) {
        GameProfile g = FAKE.get(id);
        return g == null ? null : g.name();
    }

    public static void clear(UUID id) { FAKE.remove(id); }

    /** target == null remove o disfarce. */
    public static void set(ServerPlayer p, ServerPlayer target) {
        if (target == null) {
            FAKE.remove(p.getUUID());
        } else {
            GameProfile real = target.getGameProfile();
            FAKE.put(p.getUUID(), new GameProfile(p.getUUID(), real.name(), real.properties()));
        }
        refresh(p);
    }

    private static void refresh(ServerPlayer p) {
        var server = p.level().getServer();
        ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID()));
        // Entry(ServerPlayer) e redirecionado pelo PlayerInfoEntryMixin -> usa o perfil falso
        ClientboundPlayerInfoUpdatePacket update = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p));
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            o.connection.send(remove);
            o.connection.send(update);
        }
        repair(p);
    }

    /** Forca os observadores a "ver o jogador de novo": a entidade e recriada com o perfil novo. */
    private static void repair(ServerPlayer p) {
        try {
            ServerLevel sl = (ServerLevel) p.level();
            ChunkMap cm = sl.getChunkSource().chunkMap;
            Field f = ChunkMap.class.getDeclaredField("entityMap");
            f.setAccessible(true);
            Object te = ((Int2ObjectMap<?>) f.get(cm)).get(p.getId());
            if (te == null) return;
            Method remove = te.getClass().getDeclaredMethod("removePlayer", ServerPlayer.class);
            Method update = te.getClass().getDeclaredMethod("updatePlayer", ServerPlayer.class);
            remove.setAccessible(true);
            update.setAccessible(true);
            for (ServerPlayer o : sl.players()) {
                if (o == p) continue;
                remove.invoke(te, o);
                update.invoke(te, o);
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            System.err.println("[cs4] nao consegui recriar a entidade do disfarce: " + ex);
        }
    }
}
