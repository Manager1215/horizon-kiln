package net.minecraft.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code CreativeModeTabs}: the game's tabs, by key. */
public final class CreativeModeTabs {
    public static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS = key("building_blocks");
    public static final ResourceKey<CreativeModeTab> COLORED_BLOCKS = key("colored_blocks");
    public static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS = key("natural_blocks");
    public static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = key("functional_blocks");
    public static final ResourceKey<CreativeModeTab> REDSTONE_BLOCKS = key("redstone_blocks");
    public static final ResourceKey<CreativeModeTab> HOTBAR = key("hotbar");
    public static final ResourceKey<CreativeModeTab> SEARCH = key("search");
    public static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES = key("tools_and_utilities");
    public static final ResourceKey<CreativeModeTab> COMBAT = key("combat");
    public static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS = key("food_and_drinks");
    public static final ResourceKey<CreativeModeTab> INGREDIENTS = key("ingredients");
    public static final ResourceKey<CreativeModeTab> SPAWN_EGGS = key("spawn_eggs");
    public static final ResourceKey<CreativeModeTab> OP_BLOCKS = key("op_blocks");
    public static final ResourceKey<CreativeModeTab> INVENTORY = key("inventory");

    private CreativeModeTabs() {}

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(name));
    }
}
