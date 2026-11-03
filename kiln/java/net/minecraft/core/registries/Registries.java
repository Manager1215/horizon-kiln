package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

/** Kiln's stand-in for {@code Registries}: the registries' keys Kiln knows. */
public final class Registries {
    public static final ResourceKey<Registry<Attribute>> ATTRIBUTE = key("attribute");
    public static final ResourceKey<Registry<Block>> BLOCK = key("block");
    public static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TAB = key("creative_mode_tab");
    public static final ResourceKey<Registry<Level>> DIMENSION = key("dimension");
    public static final ResourceKey<Registry<Item>> ITEM = key("item");
    public static final ResourceKey<Registry<LootTable>> LOOT_TABLE = key("loot_table");
    public static final ResourceKey<Registry<SoundEvent>> SOUND_EVENT = key("sound_event");

    private Registries() {}

    private static <T> ResourceKey<Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(Identifier.withDefaultNamespace(name));
    }
}
