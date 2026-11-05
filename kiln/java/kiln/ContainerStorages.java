package kiln;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric's {@code ContainerStorage.of}: one wrapper per container, as Fabric keeps them, so every storage that reaches
 * a slot in a transaction shares its snapshots. A slot's stack is the container's ({@code getItem}, {@code setItem});
 * what it takes is the container's say ({@code canPlaceItem}, {@code getMaxStackSize}); a committed change marks the
 * container changed.
 */
public final class ContainerStorages {
    private static final Map<Container, Whole> WRAPPED = Collections.synchronizedMap(new WeakHashMap<>());

    private ContainerStorages() {}

    public static ContainerStorage of(Container container, Direction face) {
        Whole whole = WRAPPED.computeIfAbsent(container, Whole::new);
        whole.resize();
        if (face != null && container instanceof WorldlyContainer worldly) {
            return new Face(whole, worldly, face);
        }
        return whole;
    }

    /** One slot of a container. */
    private static final class Slot extends SingleStackStorage {
        private final Container container;
        private final int slot;

        Slot(Container container, int slot) {
            this.container = container;
            this.slot = slot;
        }

        @Override
        protected ItemStack getStack() {
            return container.getItem(slot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            container.setItem(slot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            return container.canPlaceItem(slot, variant.toStack());
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            return container.getMaxStackSize(variant.toStack());
        }

        @Override
        protected void onFinalCommit() {
            container.setChanged();
        }
    }

    /** Every slot of a container, in order. */
    private static final class Whole implements ContainerStorage {
        private final Container container;
        private final List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
        private final List<SingleSlotStorage<ItemVariant>> view = Collections.unmodifiableList(slots);

        Whole(Container container) {
            this.container = container;
        }

        void resize() {
            while (slots.size() < container.getContainerSize()) {
                slots.add(new Slot(container, slots.size()));
            }
        }

        @Override
        public List<SingleSlotStorage<ItemVariant>> getSlots() {
            return view;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long amount = 0;
            for (SingleSlotStorage<ItemVariant> slot : slots) {
                amount += slot.insert(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    break;
                }
            }
            return amount;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long amount = 0;
            for (SingleSlotStorage<ItemVariant> slot : slots) {
                amount += slot.extract(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    break;
                }
            }
            return amount;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return new ArrayList<StorageView<ItemVariant>>(slots).iterator();
        }

        @Override
        public String toString() {
            return "ContainerStorage[" + container + "]";
        }
    }

    /** A worldly container seen from one face: the slots that face reaches, as the container lets items through. */
    private static final class Face implements ContainerStorage {
        private final Whole whole;
        private final List<SingleSlotStorage<ItemVariant>> reached = new ArrayList<>();

        Face(Whole whole, WorldlyContainer container, Direction face) {
            this.whole = whole;
            for (int slot : container.getSlotsForFace(face)) {
                if (slot >= 0 && slot < whole.slots.size()) {
                    reached.add(new FaceSlot(whole.slots.get(slot), container, slot, face));
                }
            }
        }

        @Override
        public List<SingleSlotStorage<ItemVariant>> getSlots() {
            return whole.getSlots();
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long amount = 0;
            for (SingleSlotStorage<ItemVariant> slot : reached) {
                amount += slot.insert(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    break;
                }
            }
            return amount;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long amount = 0;
            for (SingleSlotStorage<ItemVariant> slot : reached) {
                amount += slot.extract(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    break;
                }
            }
            return amount;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return new ArrayList<StorageView<ItemVariant>>(reached).iterator();
        }
    }

    /** A slot as a face of its container reaches it. */
    private record FaceSlot(SingleSlotStorage<ItemVariant> slot, WorldlyContainer container, int index, Direction face)
            implements SingleSlotStorage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return container.canPlaceItemThroughFace(index, resource.toStack(), face)
                    ? slot.insert(resource, maxAmount, transaction) : 0;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return container.canTakeItemThroughFace(index, resource.toStack(), face)
                    ? slot.extract(resource, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return slot.isResourceBlank();
        }

        @Override
        public ItemVariant getResource() {
            return slot.getResource();
        }

        @Override
        public long getAmount() {
            return slot.getAmount();
        }

        @Override
        public long getCapacity() {
            return slot.getCapacity();
        }

        @Override
        public StorageView<ItemVariant> getUnderlyingView() {
            return slot;
        }
    }
}
