package net.fabricmc.fabric.api.transfer.v1.item;

import java.util.List;
import kiln.ContainerStorages;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;

/**
 * Kiln's stand-in for Fabric's {@code ContainerStorage}: a {@code Container}'s slots as item storages. Seen from a
 * face of a {@code WorldlyContainer}, inserting and extracting reach only the slots that face reaches, as the
 * container allows them; {@code getSlots} is every slot either way.
 */
public interface ContainerStorage extends SlottedStorage<ItemVariant> {
    static ContainerStorage of(Container container, Direction direction) {
        return ContainerStorages.of(container, direction);
    }

    List<SingleSlotStorage<ItemVariant>> getSlots();

    @Override
    default int getSlotCount() {
        return getSlots().size();
    }

    @Override
    default SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return getSlots().get(slot);
    }
}
