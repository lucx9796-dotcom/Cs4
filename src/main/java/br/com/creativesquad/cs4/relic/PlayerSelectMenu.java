package br.com.creativesquad.cs4.relic;

import br.com.creativesquad.cs4.Cs4;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI "Selecione um Jogador " com a cabeça de cada jogador online. Começa com 9 slots e cresce de 9 em 9
 * conforme a quantidade de jogadores (10 jogadores = 18 slots). Máximo de 54 slots (6 linhas).
 */
public class PlayerSelectMenu extends ChestMenu {

    @FunctionalInterface
    public interface Picker {
        void pick(ServerPlayer clicker, ServerPlayer target);
    }

    private final int slots;
    private final List<UUID> targets;
    private final Picker picker;

    private PlayerSelectMenu(MenuType<?> type, int syncId, Inventory playerInv, Container container, int rows,
                             List<UUID> targets, Picker picker) {
        super(type, syncId, playerInv, container, rows);
        this.slots = rows * 9;
        this.targets = targets;
        this.picker = picker;
    }

    private static Component title() {
        return Component.literal("Selecione um Jogador ").setStyle(Style.EMPTY.withItalic(false).withColor(0x2E2E2E));
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static void open(ServerPlayer player, Picker picker) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
        int shown = Math.min(54, online.size());
        int rows = Math.max(1, Math.min(6, (shown + 8) / 9));

        SimpleContainer inv = new SimpleContainer(rows * 9);
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            ServerPlayer p = online.get(i);
            inv.setItem(i, head(p));
            targets.add(p.getUUID());
        }
        MenuType<?> type = typeFor(rows);
        final int r = rows;
        player.openMenu(new SimpleMenuProvider(
                (syncId, pInv, p) -> new PlayerSelectMenu(type, syncId, pInv, inv, r, targets, picker), title()));
    }

    private static ItemStack head(ServerPlayer p) {
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(p.getGameProfile().name())
                .setStyle(Style.EMPTY.withItalic(false).withColor(0xFFFFFF)));
        ResolvableProfile rp = resolvable(p.getGameProfile());
        if (rp != null) s.set(DataComponents.PROFILE, rp);
        return s;
    }

    /** ResolvableProfile mudou de API entre versões; tenta as formas conhecidas por reflexão. */
    private static ResolvableProfile resolvable(GameProfile gp) {
        try {
            for (Method m : ResolvableProfile.class.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getName().equals("createResolved")
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == GameProfile.class) {
                    return (ResolvableProfile) m.invoke(null, gp);
                }
            }
            for (Constructor<?> c : ResolvableProfile.class.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == GameProfile.class) {
                    return (ResolvableProfile) c.newInstance(gp);
                }
            }
        } catch (Throwable t) {
            Cs4.LOGGER.warn("Não consegui montar o perfil da cabeça de {}: {}", gp.name(), t.toString());
        }
        return null;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < slots && slotIndex < targets.size()
                && clickType == ContainerInput.PICKUP && (button == 0 || button == 1)
                && player instanceof ServerPlayer clicker) {
            MinecraftServer server = ((ServerLevel) clicker.level()).getServer();
            ServerPlayer target = server.getPlayerList().getPlayer(targets.get(slotIndex));
            clicker.closeContainer();
            if (target == null) {
                clicker.sendSystemMessage(Component.literal("§cEsse jogador não está mais online."));
            } else {
                picker.pick(clicker, target);
            }
            return;
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
