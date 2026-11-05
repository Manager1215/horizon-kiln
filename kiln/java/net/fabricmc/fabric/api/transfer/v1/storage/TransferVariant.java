package net.fabricmc.fabric.api.transfer.v1.storage;

import net.minecraft.core.TypedInstance;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;

/** Kiln's stand-in for Fabric's {@code TransferVariant}: a kind of resource (an item, a fluid) and its components. */
public interface TransferVariant<O> extends DataComponentHolder, TypedInstance<O> {
    boolean isBlank();

    O getObject();

    DataComponentPatch getComponentsPatch();

    @Override
    DataComponentMap getComponents();

    default boolean hasComponents() {
        return !getComponentsPatch().isEmpty();
    }

    default boolean componentsMatch(DataComponentPatch patch) {
        return getComponentsPatch().equals(patch);
    }

    default boolean isOf(O object) {
        return getObject() == object;
    }

    default TransferVariant<O> withComponents(DataComponentPatch patch) {
        return this;
    }
}
