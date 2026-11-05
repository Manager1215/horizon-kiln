package net.fabricmc.fabric.api.transfer.v1.fluid.base;

import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;

/**
 * Kiln's stand-in for Fabric's {@code FullItemFluidStorage}: an item full of a fluid (a water bucket), which gives all
 * of it at once and becomes its empty form.
 */
public final class FullItemFluidStorage implements SingleSlotStorage<FluidVariant> {
    private final ContainerItemContext context;
    private final Item fullItem;
    private final Function<ItemVariant, ItemVariant> fullToEmpty;
    private final FluidVariant fluid;
    private final long amount;

    public FullItemFluidStorage(ContainerItemContext context, Item emptyItem, FluidVariant fluid, long amount) {
        this(context, full -> ItemVariant.of(emptyItem), fluid, amount);
    }

    public FullItemFluidStorage(ContainerItemContext context, Function<ItemVariant, ItemVariant> fullToEmpty,
            FluidVariant fluid, long amount) {
        StoragePreconditions.notBlankNotNegative(fluid, amount);
        this.context = context;
        this.fullItem = context.getItemVariant().getItem();
        this.fullToEmpty = fullToEmpty;
        this.fluid = fluid;
        this.amount = amount;
    }

    @Override
    public boolean supportsInsertion() {
        return false;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!context.getItemVariant().isOf(fullItem) || !resource.equals(fluid) || maxAmount < amount) {
            return 0;
        }
        return context.exchange(fullToEmpty.apply(context.getItemVariant()), 1, transaction) == 1 ? amount : 0;
    }

    @Override
    public boolean isResourceBlank() {
        return false;
    }

    @Override
    public FluidVariant getResource() {
        return fluid;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public long getCapacity() {
        return amount;
    }
}
