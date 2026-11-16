package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code FluidVariantAttributes}: a fluid's name, as Fabric's default gives it (its
 * block's name, {@code block.minecraft.water}). A mod's attribute handlers aren't asked yet.
 */
public final class FluidVariantAttributes {
    private FluidVariantAttributes() {}

    public static Component getName(FluidVariant variant) {
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        if (id == null || variant.isBlank()) {
            return Component.translatable("block.minecraft.air");
        }
        String path = id.getPath().startsWith("flowing_") ? id.getPath().substring(8) : id.getPath();
        return Component.translatable("block." + id.getNamespace() + "." + path);
    }

    public static boolean isLighterThanAir(FluidVariant variant) {
        return false;
    }

    public static int getLuminance(FluidVariant variant) {
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        return id != null && id.getPath().contains("lava") ? 15 : 0;
    }

    public static int getTemperature(FluidVariant variant) {
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        return id != null && id.getPath().contains("lava") ? 1300 : 300;
    }

    public static int getViscosity(FluidVariant variant, net.minecraft.world.level.Level level) {
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        return id != null && id.getPath().contains("lava") ? 6000 : 1000;
    }
}
