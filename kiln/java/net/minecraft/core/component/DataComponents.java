package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code DataComponents}: the game's component types a mod names. Those with a codec here read
 * their value from the stack as the game saved it; the others ({@code kiln.Constants.component}) live on the stack only.
 */
public class DataComponents {
    /** A stack's own tags, which only mods and commands read; saved with it. */
    public static final DataComponentType<net.minecraft.world.item.component.CustomData> CUSTOM_DATA =
            persistent("custom_data", net.minecraft.world.item.component.CustomData.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> UNBREAKABLE = kiln.Constants.component("unbreakable");
    public static final DataComponentType<net.minecraft.world.item.enchantment.ItemEnchantments> ENCHANTMENTS =
            persistent("enchantments", net.minecraft.world.item.enchantment.ItemEnchantments.CODEC);
    public static final DataComponentType<net.minecraft.world.item.enchantment.ItemEnchantments> STORED_ENCHANTMENTS =
            persistent("stored_enchantments", net.minecraft.world.item.enchantment.ItemEnchantments.CODEC);
    /** What the item adds to its holder's attributes (D-177): saved as the game saves it, so its tooltip lines are written. */
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemAttributeModifiers> ATTRIBUTE_MODIFIERS =
            persistent("attribute_modifiers", net.minecraft.world.item.component.ItemAttributeModifiers.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.TooltipDisplay> TOOLTIP_DISPLAY =
            persistent("tooltip_display", net.minecraft.world.item.component.TooltipDisplay.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Tool> TOOL = kiln.Constants.component("tool");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Weapon> WEAPON = kiln.Constants.component("weapon");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.equipment.Equippable> EQUIPPABLE = kiln.Constants.component("equippable");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.core.component.BlockTransformer>> BLOCK_TRANSFORMER = kiln.Constants.component("block_transformer");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>>> BLOCK_ENTITY_DATA = kiln.Constants.component("block_entity_data");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.CookingFuel> COOKING_FUEL = kiln.Constants.component("cooking_fuel");
    public static final DataComponentType<net.minecraft.world.item.component.ItemContainerContents> CONTAINER =
            persistent("container", net.minecraft.world.item.component.ItemContainerContents.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.BundleContents> BUNDLE_CONTENTS =
            persistent("bundle_contents", net.minecraft.world.item.component.BundleContents.CODEC);
    public static final DataComponentType<net.minecraft.world.item.alchemy.PotionContents> POTION_CONTENTS =
            persistent("potion_contents", net.minecraft.world.item.alchemy.PotionContents.CODEC);


    /**
     * What a recipe viewer (JEI) reads of a stack to tell its kinds apart (D-152): on the stack only, as the others
     * made by {@code kiln.Constants.component}. Those of a value class Kiln hasn't yet are typed {@code Object}: the
     * game's type is erased, so a mod's code that names them still links, and their value is the saved JSON
     * ({@code kiln.Constants.erased}, D-172).
     */
    public static final DataComponentType<Object> BANNER_PATTERNS = kiln.Constants.erased("banner_patterns");
    public static final DataComponentType<net.minecraft.world.item.DyeColor> BASE_COLOR = kiln.Constants.component("base_color");
    public static final DataComponentType<Object> BLOCK_STATE = kiln.Constants.erased("block_state");
    public static final DataComponentType<net.minecraft.world.item.component.Compostable> COMPOSTABLE =
            kiln.Constants.component("compostable");
    public static final DataComponentType<net.minecraft.world.item.DyeColor> DYE = kiln.Constants.component("dye");
    public static final DataComponentType<net.minecraft.world.item.component.Fireworks> FIREWORKS =
            persistent("fireworks", net.minecraft.world.item.component.Fireworks.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.FireworkExplosion> FIREWORK_EXPLOSION =
            persistent("firework_explosion", net.minecraft.world.item.component.FireworkExplosion.CODEC);
    public static final DataComponentType<Object> INSTRUMENT = kiln.Constants.erased("instrument");
    public static final DataComponentType<Object> OMINOUS_BOTTLE_AMPLIFIER = kiln.Constants.erased("ominous_bottle_amplifier");
    public static final DataComponentType<Object> PAINTING_VARIANT = kiln.Constants.erased("painting/variant");
    public static final DataComponentType<Object> POT_DECORATIONS = kiln.Constants.erased("pot_decorations");
    public static final DataComponentType<net.minecraft.world.item.enchantment.Repairable> REPAIRABLE =
            kiln.Constants.component("repairable");
    public static final DataComponentType<net.minecraft.world.item.enchantment.Enchantable> ENCHANTABLE =
            kiln.Constants.component("enchantable");
    public static final DataComponentType<Integer> REPAIR_COST = kiln.Constants.component("repair_cost");
    public static final DataComponentType<Object> SUSPICIOUS_STEW_EFFECTS = kiln.Constants.erased("suspicious_stew_effects");
    public static final DataComponentType<Identifier> TOOLTIP_STYLE = kiln.Constants.component("tooltip_style");

    /** A name given to the stack (an anvil's, a mod's), shown in italics in its tooltip, as vanilla's. */
    public static final DataComponentType<net.minecraft.network.chat.Component> CUSTOM_NAME =
            persistent("custom_name", net.minecraft.network.chat.ComponentSerialization.CODEC);
    /** The item's own name in place of its translation, not italic. */
    public static final DataComponentType<net.minecraft.network.chat.Component> ITEM_NAME =
            persistent("item_name", net.minecraft.network.chat.ComponentSerialization.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.ItemLore> LORE =
            persistent("lore", net.minecraft.world.item.component.ItemLore.CODEC);
    /** Whether the stack glints, whatever its enchantments say. */
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE =
            persistent("enchantment_glint_override", Codec.BOOL);
    /** The model the game draws the stack with, in place of its item's. */
    public static final DataComponentType<Identifier> ITEM_MODEL = persistent("item_model", Identifier.CODEC);

    public DataComponents() {}

    private static <T> DataComponentType<T> persistent(String id, Codec<T> codec) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.withDefaultNamespace(id),
                DataComponentType.<T>builder().persistent(codec).build());
    }
}
