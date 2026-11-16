package net.fabricmc.fabric.api.transfer.v1.client.fluid;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;

/**
 * Kiln's stand-in for Fabric's {@code FluidVariantRendering}: a fluid's tooltip and tint, as Fabric's defaults give
 * them (water the game's default water color, other fluids untinted). Horizon draws the fluid's sprites.
 */
public class FluidVariantRendering {
    private static final int WATER = 0xFF3F76E4;

    protected FluidVariantRendering() {}

    /** Its name, and with advanced tooltips its id. */
    public static List<Component> getTooltip(FluidVariant variant, TooltipFlag flag) {
        List<Component> lines = new ArrayList<>();
        lines.add(FluidVariantAttributes.getName(variant));
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        if (flag != null && flag.isAdvanced() && id != null) {
            lines.add(Component.literal(id.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public static List<Component> getTooltip(FluidVariant variant) {
        return getTooltip(variant, null);
    }

    /** The color the fluid is tinted with, as ARGB: water's, or none (white). */
    public static int getColor(FluidVariant variant) {
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        return id != null && id.getPath().contains("water") ? WATER : -1;
    }
}
