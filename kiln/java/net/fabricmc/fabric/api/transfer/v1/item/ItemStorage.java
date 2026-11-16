package net.fabricmc.fabric.api.transfer.v1.item;

import kiln.GameContainers;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;

/**
 * Kiln's stand-in for Fabric's {@code ItemStorage}: where a block keeps items, found by its lookup. As in Fabric, a
 * block entity that is a {@code Container} and that no mod gave a storage of its own is one through the face asked
 * ({@code ContainerStorage.of}): one machine's output reaches the next machine's input. The game's own containers (a
 * chest, a double chest, a barrel, a hopper, a dispenser, a dropper, a shulker box) aren't objects on Kiln's side:
 * where no block entity of a mod is, {@code kiln.GameContainers} makes one of the game's slots for the call (D-144).
 */
public final class ItemStorage {
    public static final BlockApiLookup<Storage<ItemVariant>, Direction> SIDED = BlockApiLookup.get(
            Identifier.fromNamespaceAndPath("fabric", "sided_item_storage"), Storage.asClass(), Direction.class);

    static {
        SIDED.registerFallback((level, pos, state, entity, direction) -> {
            if (entity instanceof Container container) {
                return ContainerStorage.of(container, direction);
            }
            Container game = entity == null ? GameContainers.at(level, pos, state) : null;
            return game != null ? ContainerStorage.of(game, direction) : null;
        });
    }

    private ItemStorage() {}
}
