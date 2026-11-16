package net.minecraft.data.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code BootstrapContext}: where a registry's entries are registered as it is built. */
public interface BootstrapContext<T> extends BootstrapContextAccess {
    Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle);

    default Holder.Reference<T> register(ResourceKey<T> key, T value) {
        return register(key, value, Lifecycle.stable());
    }
}
