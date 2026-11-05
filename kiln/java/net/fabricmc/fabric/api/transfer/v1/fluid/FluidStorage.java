package net.fabricmc.fabric.api.transfer.v1.fluid;

import java.util.Collections;
import java.util.Iterator;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * Kiln's stand-in for Fabric's {@code FluidStorage}: where a block or an item keeps fluids, found by its lookups. As
 * Fabric's, it knows the buckets: a full one gives its bucket of fluid and is left empty, the empty one takes a bucket
 * of any fluid that has one.
 */
public final class FluidStorage {
    public static final BlockApiLookup<Storage<FluidVariant>, Direction> SIDED = BlockApiLookup.get(
            Identifier.fromNamespaceAndPath("fabric", "sided_fluid_storage"), Storage.asClass(), Direction.class);
    public static final ItemApiLookup<Storage<FluidVariant>, ContainerItemContext> ITEM = ItemApiLookup.get(
            Identifier.fromNamespaceAndPath("fabric", "item_fluid_storage"), Storage.asClass(),
            ContainerItemContext.class);

    static {
        ITEM.registerForItems((stack, context) -> new EmptyBucket(context), Items.BUCKET);
        ITEM.registerFallback((stack, context) -> {
            // A bucket whose fluid's bucket is itself: the game's, and a mod's for its own fluid.
            if (context.getItemVariant().getItem() instanceof BucketItem bucket) {
                Fluid fluid = bucket.kiln$content();
                if (fluid != null && !FluidVariant.of(fluid).isBlank() && fluid.getBucket() == bucket) {
                    return new FullItemFluidStorage(context, Items.BUCKET, FluidVariant.of(fluid),
                            FluidConstants.BUCKET);
                }
            }
            return null;
        });
    }

    private FluidStorage() {}

    /** Kiln's own: the empty bucket, which a bucket of a fluid fills to that fluid's bucket. */
    private record EmptyBucket(ContainerItemContext context) implements Storage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            Item full = resource.getFluid().getBucket();
            if (!context.getItemVariant().isOf(Items.BUCKET) || maxAmount < FluidConstants.BUCKET
                    || !(full instanceof BucketItem bucket) || bucket.kiln$content() != resource.getFluid()) {
                return 0;
            }
            return context.exchange(ItemVariant.of(full), 1, transaction) == 1 ? FluidConstants.BUCKET : 0;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return Collections.emptyIterator();
        }
    }
}
