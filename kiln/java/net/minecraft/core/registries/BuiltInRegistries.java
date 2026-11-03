package net.minecraft.core.registries;

import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Kiln's stand-in for {@code BuiltInRegistries}: what the Java mods register. A mod's item meets Horizon's import of
 * the mod's data by its id: Kiln runs the item's code when a player uses Horizon's item of that id.
 */
public final class BuiltInRegistries {
    public static final DefaultedRegistry<Block> BLOCK = new DefaultedMappedRegistry<>("minecraft:air", Registries.BLOCK);
    public static final DefaultedRegistry<Item> ITEM = new DefaultedMappedRegistry<>("minecraft:air", Registries.ITEM);
    public static final Registry<SoundEvent> SOUND_EVENT = new MappedRegistry<>(Registries.SOUND_EVENT);

    static {
        // Vanilla's Bootstrap fills the registries before any mod runs: the game's blocks and items Kiln names are in
        // them too.
        // Started from Items itself, this returns at once (its class is being made on this thread).
        try {
            Class.forName(Blocks.class.getName());
            Class.forName(Items.class.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private BuiltInRegistries() {}
}
