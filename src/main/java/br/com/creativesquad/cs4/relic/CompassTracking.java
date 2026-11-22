package br.com.creativesquad.cs4.relic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.Optional;
import java.util.UUID;

/**
 * Bússola Lendária: o ponteiro aponta para o jogador escolhido na GUI. O UUID do alvo fica guardado no próprio item
 * (custom_data.cs4_track) e a posição é atualizada a cada meio segundo usando o componente lodestone_tracker.
 */
public final class CompassTracking {
    private CompassTracking() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) update(server, p);
        });
    }

    /** Define o alvo da bússola (grava no item e já aponta). */
    public static void setTarget(ItemStack compass, ServerPlayer target) {
        CustomData data = compass.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString(RelicRegistry.TRACK_KEY, target.getUUID().toString());
        compass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        point(compass, target);
    }

    private static void update(MinecraftServer server, ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!RelicRegistry.is(s, "bussola")) continue;
            String uuid = s.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr(RelicRegistry.TRACK_KEY, "");
            if (uuid.isEmpty()) continue;
            try {
                ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(uuid));
                if (target != null) point(s, target);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static void point(ItemStack compass, ServerPlayer target) {
        GlobalPos pos = GlobalPos.of(target.level().dimension(), target.blockPosition());
        LodestoneTracker current = compass.get(DataComponents.LODESTONE_TRACKER);
        if (current != null && current.target().isPresent() && current.target().get().equals(pos)) return; // sem mudança: não reenvia o item
        compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(pos), false));
    }
}
