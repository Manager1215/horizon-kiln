package net.minecraft.client.resources.model;

/**
 * Kiln's stand-in for {@code ModelManager}: the fluids' models (D-168), which a recipe viewer reads for the sprite it
 * draws a fluid with. The blocks' and items' models are Horizon's own: their set stays the generated stub's.
 */
public class ModelManager implements net.minecraft.server.packs.resources.PreparableReloadListener {
    public ModelManager(net.minecraft.client.color.block.BlockColors p0,
            net.minecraft.client.resources.model.sprite.AtlasManager p1,
            net.minecraft.client.renderer.PlayerSkinRenderCache p2) {}

    public ModelManager() {}

    /** Each fluid's model: vanilla's water and lava, and the ones mods registered with Fabric's registry. */
    public net.minecraft.client.renderer.block.FluidStateModelSet getFluidStateModelSet() {
        return net.minecraft.client.renderer.block.FluidStateModelSet.kiln$current();
    }

    public net.minecraft.client.renderer.block.BlockStateModelSet getBlockStateModelSet() {
        kiln.Missing.called("net.minecraft.client.resources.model.ModelManager.getBlockStateModelSet");
        return null;
    }

    public java.util.concurrent.CompletableFuture<java.lang.Void> reload(
            net.minecraft.server.packs.resources.PreparableReloadListener.SharedState p0, java.util.concurrent.Executor p1,
            net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier p2,
            java.util.concurrent.Executor p3) {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
