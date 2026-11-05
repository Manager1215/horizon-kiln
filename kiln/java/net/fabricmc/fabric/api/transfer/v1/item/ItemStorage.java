package net.fabricmc.fabric.api.transfer.v1.item;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code ItemStorage}: where a block keeps items, found by its lookup. Kiln has no
 * fallback for the game's own containers yet: a mod's block finds only what mods registered.
 */
public final class ItemStorage {
    public static final BlockApiLookup<Storage<ItemVariant>, Direction> SIDED = BlockApiLookup.get(
            Identifier.fromNamespaceAndPath("fabric", "sided_item_storage"), Storage.asClass(), Direction.class);

    private ItemStorage() {}
}
