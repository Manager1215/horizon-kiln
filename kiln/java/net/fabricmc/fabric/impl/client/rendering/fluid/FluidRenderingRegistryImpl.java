package net.fabricmc.fabric.impl.client.rendering.fluid;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.material.Fluid;

/** Kiln's stand-in for Fabric's {@code FluidRenderingRegistryImpl}: the fluid models the mods registered (D-168). */
public class FluidRenderingRegistryImpl {
    private static final Map<Fluid, FluidModel.Unbaked> MODELS = new ConcurrentHashMap<>();

    protected FluidRenderingRegistryImpl() {}

    public static Map<Fluid, FluidModel.Unbaked> getUnbakedModels() {
        return MODELS;
    }
}
