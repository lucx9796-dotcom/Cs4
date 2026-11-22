package br.com.creativesquad.cs4.relic;

import br.com.creativesquad.cs4.Cs4;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relógio Lendário: o jogador "vira" outro jogador.
 *
 *  - skin, nome na cabeça, lista de jogadores (tab), painel de report e sugestões de nome do chat (/tell etc.)
 *    vêm da entrada do jogador na lista de jogadores do cliente. Então re-enviamos essa entrada (remove + add) para
 *    todo mundo com o perfil do alvo (nome + texturas) no lugar do perfil real, e re-spawnamos a entidade
 *    para quem está por perto (o cliente só lê o nome do perfil quando a entidade nasce);
 *  - nome em chat, /tell, mensagens de morte etc.: o servidor usa Entity.getDisplayName(); ver EntityDisplayNameMixin.
 *  - clicar na própria cabeça (ou sair do jogo) desfaz o disfarce.
 */
public final class Disguise {
    private static final Map<UUID, GameProfile> FAKE = new ConcurrentHashMap<>();
    private static boolean warnedRespawn = false;

    private Disguise() {}

    public static void init() {
        // quem entra agora precisa receber a lista com os perfis falsos dos disfarçados
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                GameProfile fake = FAKE.get(p.getUUID());
                if (fake == null || p == joined) continue;
                joined.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID())));
                joined.connection.send(infoPacket(p, fake));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FAKE.remove(handler.getPlayer().getUUID()));
    }

    /** Nome do disfarce de um jogador (null se não estiver disfarçado). Usado pelo mixin de getDisplayName. */
    public static String nameOf(UUID player) {
        GameProfile fake = FAKE.get(player);
        return fake == null ? null : fake.name();
    }

    public static boolean isDisguised(UUID player) { return FAKE.containsKey(player); }

    /** Transforma {@code p} em {@code target}. Escolher a si mesmo desfaz o disfarce. */
    public static void set(MinecraftServer server, ServerPlayer p, ServerPlayer target) {
        if (p.getUUID().equals(target.getUUID())) {
            clear(server, p);
            return;
        }
        GameProfile t = target.getGameProfile();
        GameProfile fake = new GameProfile(p.getUUID(), t.name(), t.properties());
        FAKE.put(p.getUUID(), fake);
        apply(server, p, fake);
    }

    public static void clear(MinecraftServer server, ServerPlayer p) {
        if (FAKE.remove(p.getUUID()) != null) apply(server, p, null);
    }

    // ---------------------------------------------------------------------------------------------

    private static void apply(MinecraftServer server, ServerPlayer p, GameProfile fake) {
        Object tracked = trackedEntity(p);
        for (ServerPlayer viewer : new ArrayList<>(server.getPlayerList().getPlayers())) {
            boolean self = viewer == p;
            if (!self) invoke(tracked, "removePlayer", viewer);                       // some da tela de quem via
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID())));
            viewer.connection.send(infoPacket(p, fake));                              // entrada nova (skin/nome)
            if (!self) invoke(tracked, "updatePlayer", viewer);                       // nasce de novo com o perfil novo
        }
    }

    private static ClientboundPlayerInfoUpdatePacket infoPacket(ServerPlayer p, GameProfile fake) {
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p));
        if (fake != null) swapProfile(packet, fake);
        return packet;
    }

    /** Troca o GameProfile de cada entrada do pacote (record) usando reflexão, sem depender dos nomes internos. */
    private static void swapProfile(Object packet, GameProfile fake) {
        try {
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                List<?> entries = (List<?>) f.get(packet);
                List<Object> out = new ArrayList<>();
                for (Object entry : entries) {
                    RecordComponent[] comps = entry.getClass().getRecordComponents();
                    Object[] values = new Object[comps.length];
                    Class<?>[] types = new Class<?>[comps.length];
                    for (int i = 0; i < comps.length; i++) {
                        Method accessor = comps[i].getAccessor();
                        accessor.setAccessible(true);
                        values[i] = comps[i].getType() == GameProfile.class ? fake : accessor.invoke(entry);
                        types[i] = comps[i].getType();
                    }
                    Constructor<?> c = entry.getClass().getDeclaredConstructor(types);
                    c.setAccessible(true);
                    out.add(c.newInstance(values));
                }
                f.set(packet, out);
                return;
            }
        } catch (Exception e) {
            Cs4.LOGGER.warn("Não consegui trocar o perfil no pacote de jogadores: {}", e.toString());
        }
    }

    // ---- acesso ao rastreador de entidades do servidor (para re-spawnar o jogador para quem está perto) ----

    private static Object trackedEntity(ServerPlayer p) {
        try {
            ServerLevel level = (ServerLevel) p.level();
            Object cache = level.getChunkSource();
            Object chunkMap = null;
            for (Class<?> c = cache.getClass(); c != null && chunkMap == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (ChunkMap.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        chunkMap = f.get(cache);
                        break;
                    }
                }
            }
            if (chunkMap == null) return null;
            for (Field f : ChunkMap.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = ((Map<?, ?>) f.get(chunkMap)).get(p.getId());
                if (v != null && v.getClass().getSimpleName().equals("TrackedEntity")) return v;
            }
        } catch (Throwable t) {
            warnRespawn(t.toString());
        }
        return null;
    }

    private static void invoke(Object tracked, String name, ServerPlayer viewer) {
        if (tracked == null) { warnRespawn("rastreador da entidade não encontrado"); return; }
        try {
            for (Method m : tracked.getClass().getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == ServerPlayer.class) {
                    m.setAccessible(true);
                    m.invoke(tracked, viewer);
                    return;
                }
            }
            warnRespawn("método " + name + " não encontrado");
        } catch (Throwable t) {
            warnRespawn(t.toString());
        }
    }

    private static void warnRespawn(String why) {
        if (warnedRespawn) return;
        warnedRespawn = true;
        Cs4.LOGGER.warn("Relógio: não consegui re-spawnar o jogador para quem está perto ({}). "
                + "O nome/skin novos aparecem na lista e no chat, mas na cabeça só depois que a entidade for recarregada.", why);
    }
}
