package net.minecraft.world.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code Items}: air, the empty stack's item, and a few of the game's items mods name, registered
 * in {@code BuiltInRegistries.ITEM} as vanilla's are. Horizon has them all; other game items Kiln makes by id when it
 * meets them.
 */
public final class Items {
    public static final Item AIR = register("air");
    public static final Item APPLE = register("apple");
    public static final Item BREAD = register("bread");
    public static final Item DIAMOND = register("diamond");
    public static final Item EMERALD = register("emerald");
    public static final Item GOLD_INGOT = register("gold_ingot");
    public static final Item IRON_INGOT = register("iron_ingot");
    public static final Item NETHER_STAR = register("nether_star");
    public static final Item STICK = register("stick");

    private Items() {}

    private static Item register(String name) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return Registry.register(BuiltInRegistries.ITEM, id,
                new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
