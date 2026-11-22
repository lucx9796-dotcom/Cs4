package br.com.creativesquad.cs4.relic;

import br.com.creativesquad.cs4.Cs4;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As relíquias são definidas com a MESMA sintaxe do /give (1.21.5+), uma por linha: id|item[componentes].
 * A ordem das linhas = ordem dos slots da GUI do /relics (slot 1 = primeira linha).
 * Para editar uma relíquia, basta mudar a linha. O id vira custom_data={cs4_relic:"id"}, usado para identificar o item.
 *
 * Mudanças em relação aos /give antigos (1.21.4) que você mandou:
 *  - enchantments={levels:{x:1}}  ->  enchantments={x:1}
 *  - attribute_modifiers={modifiers:[...]}  ->  attribute_modifiers=[...]   (nomes sem "generic.")
 *  - show_in_tooltip:false  ->  tooltip_display={hidden_components:[...]}
 *  - fire_resistant={}  ->  damage_resistant={types:"#minecraft:is_fire"}
 *  - encantamentos com nível 0 (depth_strider:0, soul_speed:0, respiration:0) foram removidos: nível 0 é inválido.
 */
public final class RelicRegistry {
    public static final String KEY = "cs4_relic";
    /** Chave extra guardada no custom_data da Bússola: UUID do jogador rastreado. */
    public static final String TRACK_KEY = "cs4_track";

    private static final String FIRE_RES = ",damage_resistant={types:\"#minecraft:is_fire\"}";

    private static final List<ItemStack> STACKS = new ArrayList<>();
    private static final Map<String, ItemStack> BY_ID = new LinkedHashMap<>();

    private static final String DEFINITIONS = """
dragao|dragon_head[custom_name=["",{"text":"Dragão Lendário","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,item_model="cs4:dragao",enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
pena|feather[custom_name=["",{"text":"Pena Lendária","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
escudo|shield[custom_name=["",{"text":"Escudo Lendário","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
lampiao|soul_lantern[custom_name=["",{"text":"Lampião Lendário","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
batata|poisonous_potato[custom_name=["",{"text":"Batata do Bobo","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
relogio|clock[custom_name=["",{"text":"Relógio Lendário","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
capacete|turtle_helmet[custom_name=["",{"text":"Capacete Lendário dos Gemêos","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={aqua_affinity:1,blast_protection:4,efficiency:10,fire_protection:4,projectile_protection:4,protection:4,respiration:3,thorns:3},attribute_modifiers=[{type:"max_health",slot:head,amount:10,operation:add_value,id:"cs4:twin_health"},{type:"armor",slot:head,amount:3,operation:add_value,id:"cs4:twin_armor"}],unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
bota|diamond_boots[custom_name=["",{"text":"Bota Lendária","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={blast_protection:4,efficiency:10,feather_falling:4,fire_protection:4,frost_walker:2,projectile_protection:4,protection:4,soul_speed:3,thorns:3},attribute_modifiers=[{type:"armor",slot:feet,amount:3,operation:add_value,id:"cs4:boots_armor"}],unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
picareta|iron_pickaxe[custom_name=["",{"text":"Picareta Lendária","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
espada|golden_sword[custom_name=["",{"text":"Espada do Caçador","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10,knockback:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
bussola|compass[custom_name=["",{"text":"Bússola Lendária","italic":false,"color":"#ffff00","underlined":true,"bold":true}],enchantment_glint_override=true,enchantments={efficiency:10},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
pocao|splash_potion[potion_contents={custom_color:15925418},custom_name=["",{"text":"Poção Lendária","bold":true,"underlined":true,"italic":false,"color":"#fff000"}],enchantment_glint_override=true,unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,potion_contents,unbreakable]}]
tridente|trident[custom_name=["",{"text":"Tridente Lendario","bold":true,"underlined":true,"italic":false,"color":"#ffff00"}],enchantment_glint_override=true,enchantments={channeling:1,loyalty:3},unbreakable={},damage_resistant={types:"#minecraft:is_fire"},tooltip_display={hidden_components:[attribute_modifiers,enchantments,unbreakable]}]
""";

    private RelicRegistry() {}

    public static void load(HolderLookup.Provider lookup) {
        STACKS.clear();
        BY_ID.clear();
        ItemParser reader = new ItemParser(lookup);
        for (String raw : DEFINITIONS.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            int sep = line.indexOf('|');
            String id = line.substring(0, sep);
            String spec = line.substring(sep + 1).replaceFirst("\\[", "[custom_data={" + KEY + ":\"" + id + "\"},");
            ItemStack made = parse(reader, id, spec);
            if (made.isEmpty() && spec.contains(FIRE_RES)) {
                // damage_resistant é opcional (a indestrutibilidade vem do mixin): tenta de novo sem ele
                made = parse(reader, id, spec.replace(FIRE_RES, ""));
            }
            STACKS.add(made);
            if (!made.isEmpty()) BY_ID.put(id, made);
        }
        Cs4.LOGGER.info("{} relíquias carregadas.", BY_ID.size());
    }

    private static ItemStack parse(ItemParser reader, String id, String spec) {
        try {
            ItemInput r = reader.parse(new StringReader(spec));
            return r.createItemStack(1);
        } catch (Exception e) {
            Cs4.LOGGER.error("Relíquia '{}' inválida, slot ficará vazio: {}", id, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static List<ItemStack> stacks() { return STACKS; }

    /** Id da relíquia ("" se o item não for uma). */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        return data.copyTag().getStringOr(KEY, "");
    }

    public static boolean isRelic(ItemStack stack) { return !idOf(stack).isEmpty(); }

    public static boolean is(ItemStack stack, String id) { return id.equals(idOf(stack)); }

    /** Cópia nova da relíquia (ou ItemStack.EMPTY se o id não existir). */
    public static ItemStack copyOf(String id) {
        ItemStack s = BY_ID.get(id);
        return s == null ? ItemStack.EMPTY : s.copy();
    }
}
