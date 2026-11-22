package br.com.creativesquad.cs4.client;

import br.com.creativesquad.cs4.relic.RelicRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lampião Lendário (Invisibilidade XI). Esconde a armadura e os itens das mãos no render state sem depender dos
 * nomes dos campos (que mudam entre versões): varre os campos do tipo ItemStack (armadura) e ItemStackRenderState
 * de mão. Elytra, abóbora e cabeças ficam visíveis. A visão em primeira pessoa não passa por aqui.
 */
public final class ArmorHider {
    private static final Map<Class<?>, List<Field>> STACK_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> HAND_FIELDS = new ConcurrentHashMap<>();

    private ArmorHider() {}

    /** Quem segura o Lampião Lendário (em qualquer mão) está "encoberto". */
    public static boolean isCloaked(LivingEntity e) {
        return RelicRegistry.is(e.getItemBySlot(EquipmentSlot.MAINHAND), "lampiao")
                || RelicRegistry.is(e.getItemBySlot(EquipmentSlot.OFFHAND), "lampiao");
    }

    private static boolean keeps(ItemStack s) {
        return s.is(Items.ELYTRA) || s.is(Items.CARVED_PUMPKIN) || s.is(ItemTags.SKULLS);
    }

    /** No cliente, quem está encoberto "não tem" armadura (exceto elytra, abóbora e cabeças). */
    public static ItemStack filter(LivingEntity e, EquipmentSlot slot, ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return original;
        if (keeps(original)) return original;
        Level w = e.level();
        if (w == null || !w.isClientSide()) return original;
        return isCloaked(e) ? ItemStack.EMPTY : original;
    }

    private static List<Field> fields(Map<Class<?>, List<Field>> cache, Class<?> type, boolean hand) {
        return cache.computeIfAbsent(type, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    boolean ok = hand
                            ? f.getType().getSimpleName().equals("ItemStackRenderState") && f.getName().toLowerCase(Locale.ROOT).contains("hand")
                            : f.getType() == ItemStack.class;
                    if (!ok) continue;
                    try { f.setAccessible(true); out.add(f); } catch (Exception ignored) {}
                }
            }
            return out;
        });
    }

    public static void hide(Object state) {
        // armadura
        for (Field f : fields(STACK_FIELDS, state.getClass(), false)) {
            try {
                ItemStack cur = (ItemStack) f.get(state);
                if (cur == null || cur.isEmpty() || keeps(cur)) continue;
                Equippable eq = cur.get(DataComponents.EQUIPPABLE);
                if (eq == null) continue;
                EquipmentSlot s = eq.slot();
                if (s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET) {
                    f.set(state, ItemStack.EMPTY);
                }
            } catch (Exception ignored) {}
        }
        // itens das mãos (ItemStackRenderState.clear())
        for (Field f : fields(HAND_FIELDS, state.getClass(), true)) {
            try {
                Object itemState = f.get(state);
                if (itemState == null) continue;
                Method clear = itemState.getClass().getMethod("clear");
                clear.invoke(itemState);
            } catch (Exception ignored) {}
        }
    }
}
