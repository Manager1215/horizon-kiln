package net.fabricmc.fabric.api.transfer.v1.fluid.base;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * Kiln's stand-in for Fabric's {@code EmptyItemFluidStorage}: an empty item (a bottle) that takes a set amount of one
 * fluid at once and becomes its full form. It shows nothing to take.
 */
public final class EmptyItemFluidStorage implements Storage<FluidVariant> {
    private final ContainerItemContext context;
    private final Item emptyItem;
    private final Function<ItemVariant, ItemVariant> emptyToFull;
    private final Fluid fluid;
    private final long amount;

    public EmptyItemFluidStorage(ContainerItemContext context, Item fullItem, Fluid fluid, long amount) {
        this(context, empty -> ItemVariant.of(fullItem), fluid, amount);
    }

    public EmptyItemFluidStorage(ContainerItemContext context, Function<ItemVariant, ItemVariant> emptyToFull,
            Fluid fluid, long amount) {
        StoragePreconditions.notNegative(amount);
        this.context = context;
        this.emptyItem = context.getItemVariant().getItem();
        this.emptyToFull = emptyToFull;
        this.fluid = fluid;
        this.amount = amount;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!context.getItemVariant().isOf(emptyItem) || !resource.isOf(fluid) || maxAmount < amount) {
            return 0;
        }
        return context.exchange(emptyToFull.apply(context.getItemVariant()), 1, transaction) == 1 ? amount : 0;
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
