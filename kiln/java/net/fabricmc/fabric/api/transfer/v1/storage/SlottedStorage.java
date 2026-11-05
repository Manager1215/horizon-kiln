package net.fabricmc.fabric.api.transfer.v1.storage;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;

/** Kiln's stand-in for Fabric's {@code SlottedStorage}: a storage made of numbered slots. */
public interface SlottedStorage<T> extends Storage<T> {
    int getSlotCount();

    SingleSlotStorage<T> getSlot(int slot);

    default List<SingleSlotStorage<T>> getSlots() {
        List<SingleSlotStorage<T>> slots = new ArrayList<>();
        for (int i = 0; i < getSlotCount(); i++) {
            slots.add(getSlot(i));
        }
        return slots;
    }
}
