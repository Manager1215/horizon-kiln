package net.minecraft.core.registries;

import net.minecraft.data.worldgen.BootstrapContext;

/** Kiln's stand-in for {@code SingleRegistryBootstrap}: what registers one registry's entries as it is built. */
@FunctionalInterface
public interface SingleRegistryBootstrap<T> {
    void run(BootstrapContext<T> context);
}
