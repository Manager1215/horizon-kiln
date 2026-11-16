package net.fabricmc.fabric.api.client.render.fluid.v1;

import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderingRegistryImpl;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.material.Fluid;

/**
 * Kiln's stand-in for Fabric's {@code FluidRenderingRegistry}: a mod's fluid gets its model (its still, flowing and
 * overlay textures, its tint). Horizon draws a mod's fluid in the world from its import; these models are what the
 * mods read back through {@code ModelManager.getFluidStateModelSet()} (a recipe viewer's tanks, D-168).
 */
public class FluidRenderingRegistry {
    protected FluidRenderingRegistry() {}

    public static void register(Fluid fluid, FluidModel.Unbaked model) {
        if (fluid != null && model != null) {
            FluidRenderingRegistryImpl.getUnbakedModels().put(fluid, model);
        }
    }

    /** The source and its flowing fluid with one model, as Fabric's two-fluid form. */
    public static void register(Fluid still, Fluid flowing, FluidModel.Unbaked model) {
        register(still, model);
        register(flowing, model);
    }
}
