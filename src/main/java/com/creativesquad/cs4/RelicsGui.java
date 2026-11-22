package com.creativesquad.cs4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public final class RelicsGui {
    private RelicsGui() {}

    /** Menu de bau onde TODO clique e interceptado (nada sai do lugar). */
    public static class ClickMenu extends ChestMenu {
        private final IntConsumer onSlot;
        private final int size;

        public ClickMenu(MenuType<?> type, int id, Inventory inv, Container c, int rows, IntConsumer onSlot) {
            super(type, id, inv, c, rows);
            this.onSlot = onSlot;
            this.size = rows * 9;
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (slotId >= 0 && slotId < size && !"QUICK_CRAFT".equals(input.name())) {
                onSlot.accept(slotId); // botao esquerdo, direito, shift... todos valem
            }
            this.sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

        @Override
        public boolean canDragTo(Slot slot) { return false; }
    }

    private static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** /relics : 18 slots. Clicar da uma copia; o item continua na GUI. */
    public static void openRelics(ServerPlayer p) {
        Component title = Component.empty()
            .append(Component.literal("CS").withStyle(s -> s.withBold(true).withItalic(false).withColor(0xFBF0FF)))
            .append(Component.literal("»").withStyle(s -> s.withItalic(false).withColor(0xAAAAAA)))
            .append(Component.literal(" Relíquia").withStyle(s -> s.withItalic(false).withColor(0x2E2E2E)));

        SimpleContainer c = new SimpleContainer(18);
        int i = 0;
        for (String id : Relics.ORDER) c.setItem(i++, Relics.create(id, p.level().registryAccess()));

        p.openMenu(new SimpleMenuProvider((syncId, inv, pl) ->
            new ClickMenu(MenuType.GENERIC_9x2, syncId, inv, c, 2, slot -> {
                ItemStack t = c.getItem(slot);
                if (t.isEmpty() || !(pl instanceof ServerPlayer sp)) return;
                ItemStack copy = t.copy();
                if (!sp.getInventory().add(copy)) sp.drop(copy, false);
            }), title));
    }

    /** GUI com a cabeca de todos os jogadores online (9 slots por linha, cresce sozinha). */
    public static void openPlayerSelector(ServerPlayer opener, BiConsumer<ServerPlayer, ServerPlayer> onPick) {
        MinecraftServer server = opener.level().getServer();
        List<ServerPlayer> list = new ArrayList<>(server.getPlayerList().getPlayers());
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(list.size() / 9.0)));

        SimpleContainer c = new SimpleContainer(rows * 9);
        for (int i = 0; i < list.size() && i < rows * 9; i++) {
            ServerPlayer sp = list.get(i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(sp.getGameProfile()));
            head.set(DataComponents.CUSTOM_NAME, sp.getName().copy().withStyle(s -> s.withItalic(false)));
            c.setItem(i, head);
        }

        Component title = Component.literal("Selecione um Jogador ")
            .withStyle(s -> s.withItalic(false).withColor(0x2E2E2E));

        final int r = rows;
        opener.openMenu(new SimpleMenuProvider((syncId, inv, pl) ->
            new ClickMenu(type(r), syncId, inv, c, r, slot -> {
                if (slot < list.size() && pl instanceof ServerPlayer sp) {
                    ServerPlayer target = list.get(slot);
                    sp.closeContainer();
                    onPick.accept(sp, target);
                }
            }), title));
    }
}
