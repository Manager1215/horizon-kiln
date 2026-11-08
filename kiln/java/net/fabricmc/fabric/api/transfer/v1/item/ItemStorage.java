package net.fabricmc.fabric.api.transfer.v1.item;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;

/**
 * Kiln's stand-in for Fabric's {@code ItemStorage}: where a block keeps items, found by its lookup. As in Fabric, a
 * block entity that is a {@code Container} and that no mod gave a storage of its own is one through the face asked
 * ({@code ContainerStorage.of}): one machine's output reaches the next machine's input. Only mods' block entities so
 * far: the game's own containers (a chest, a hopper) aren't objects on Kiln's side, so a mod's block finds none there.
 */
public final class ItemStorage {
    public static final BlockApiLookup<Storage<ItemVariant>, Direction> SIDED = BlockApiLookup.get(
            Identifier.fromNamespaceAndPath("fabric", "sided_item_storage"), Storage.asClass(), Direction.class);

    static {
        SIDED.registerFallback((level, pos, state, entity, direction) ->
                entity instanceof Container container ? ContainerStorage.of(container, direction) : null);
    }

    private ItemStorage() {}
}
