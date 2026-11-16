package net.minecraft.world.item.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code Enchantments}: the game's enchantments' keys mods name. */
public class Enchantments {
    public static final ResourceKey<Enchantment> SILK_TOUCH = key("silk_touch");
    public static final ResourceKey<Enchantment> UNBREAKING = key("unbreaking");
    public static final ResourceKey<Enchantment> FORTUNE = key("fortune");
    public static final ResourceKey<Enchantment> MENDING = key("mending");
    public static final ResourceKey<Enchantment> EFFICIENCY = key("efficiency");
    public static final ResourceKey<Enchantment> SHARPNESS = key("sharpness");
    public static final ResourceKey<Enchantment> LOOTING = key("looting");
    public static final ResourceKey<Enchantment> PROTECTION = key("protection");

    public Enchantments() {}

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(name));
    }
}
