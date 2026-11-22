package com.creativesquad.cs4;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class Relics {
    public static final String TAG = "cs4_relic";
    public static final String TARGET_TAG = "cs4_target";
    public static final int POTION_COLOR = 15925418;

    public static final List<String> ORDER = List.of(
        "hunter_sword", "legendary_feather", "legendary_shield", "legendary_potion",
        "legendary_trident", "legendary_pickaxe", "twin_helmet", "legendary_boots",
        "dragon_head", "soul_lantern", "poison_potato", "legendary_clock", "legendary_compass");

    private Relics() {}

    /** Id da reliquia ou "" se o item nao for uma. */
    public static String idOf(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        return cd.copyTag().getStringOr(TAG, "");
    }

    /** Qualquer item com a tag unbreakable e indestrutivel como entidade. */
    public static boolean isIndestructible(ItemStack s) {
        return s != null && !s.isEmpty() && s.has(DataComponents.UNBREAKABLE);
    }

    private static Map<ResourceKey<Enchantment>, Integer> e(Object... kv) {
        Map<ResourceKey<Enchantment>, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> k = (ResourceKey<Enchantment>) kv[i];
            m.put(k, (Integer) kv[i + 1]);
        }
        return m;
    }

    public static ItemStack create(String id, HolderLookup.Provider reg) {
        return switch (id) {
            case "dragon_head" -> build(Items.DRAGON_HEAD, id, "Dragão Lendário", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "legendary_feather" -> build(Items.FEATHER, id, "Pena Lendária", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "legendary_shield" -> build(Items.SHIELD, id, "Escudo Lendário", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "soul_lantern" -> build(Items.SOUL_LANTERN, id, "Lampião Lendário", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "poison_potato" -> build(Items.POISONOUS_POTATO, id, "Batata do Bobo", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "legendary_clock" -> build(Items.CLOCK, id, "Relógio Lendário", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "legendary_compass" -> build(Items.COMPASS, id, "Bússola Lendária", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "legendary_pickaxe" -> build(Items.IRON_PICKAXE, id, "Picareta Lendária", reg, null,
                e(Enchantments.EFFICIENCY, 10));
            case "hunter_sword" -> build(Items.GOLDEN_SWORD, id, "Espada do Caçador", reg, null,
                e(Enchantments.EFFICIENCY, 10, Enchantments.KNOCKBACK, 10));
            case "legendary_trident" -> build(Items.TRIDENT, id, "Tridente Lendário", reg, null,
                e(Enchantments.CHANNELING, 1, Enchantments.LOYALTY, 3));
            case "twin_helmet" -> build(Items.TURTLE_HELMET, id, "Capacete Lendário dos Gemêos", reg,
                ItemAttributeModifiers.builder()
                    .add(Attributes.MAX_HEALTH, new AttributeModifier(
                        Identifier.fromNamespaceAndPath("cs4", "twin_health"), 10,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                    .add(Attributes.ARMOR, new AttributeModifier(
                        Identifier.fromNamespaceAndPath("cs4", "twin_armor"), 3,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                    .build(),
                e(Enchantments.AQUA_AFFINITY, 1, Enchantments.BLAST_PROTECTION, 4,
                  Enchantments.EFFICIENCY, 10, Enchantments.FIRE_PROTECTION, 4,
                  Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.PROTECTION, 4,
                  Enchantments.RESPIRATION, 3, Enchantments.THORNS, 3));
            case "legendary_boots" -> build(Items.DIAMOND_BOOTS, id, "Bota Lendária", reg,
                ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(
                        Identifier.fromNamespaceAndPath("cs4", "boots_armor"), 3,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
                    .build(),
                e(Enchantments.BLAST_PROTECTION, 4, Enchantments.EFFICIENCY, 10,
                  Enchantments.FEATHER_FALLING, 4, Enchantments.FIRE_PROTECTION, 4,
                  Enchantments.FROST_WALKER, 2, Enchantments.PROJECTILE_PROTECTION, 4,
                  Enchantments.PROTECTION, 4, Enchantments.SOUL_SPEED, 3, Enchantments.THORNS, 3));
            case "legendary_potion" -> {
                ItemStack s = build(Items.SPLASH_POTION, id, "Poção Lendária", reg, null, e());
                s.set(DataComponents.POTION_CONTENTS,
                    new PotionContents(Optional.empty(), Optional.of(POTION_COLOR), List.of(), Optional.empty()));
                yield s;
            }
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack build(Item item, String id, String name, HolderLookup.Provider reg,
                                   ItemAttributeModifiers attrs, Map<ResourceKey<Enchantment>, Integer> ench) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(
            st -> st.withColor(0xFFFF00).withBold(true).withUnderlined(true).withItalic(false)));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        if (!ench.isEmpty()) {
            var lookup = reg.lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (var en : ench.entrySet()) {
                if (en.getValue() > 0) m.set(lookup.getOrThrow(en.getKey()), en.getValue());
            }
            s.set(DataComponents.ENCHANTMENTS, m.toImmutable());
        }
        if (attrs != null) s.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);

        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(reg.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE)));
        s.set(DataComponents.MAX_STACK_SIZE, 1);

        LinkedHashSet<DataComponentType<?>> hidden = new LinkedHashSet<>();
        hidden.add(DataComponents.ENCHANTMENTS);
        hidden.add(DataComponents.ATTRIBUTE_MODIFIERS);
        hidden.add(DataComponents.UNBREAKABLE);
        s.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, hidden));

        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, id);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }
}
