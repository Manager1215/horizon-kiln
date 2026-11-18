package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code DyedItemColor} ({@code minecraft:dyed_color}): the RGB a dyed item (leather armour) is
 * tinted with, saved as the game saves it, a number. {@link #applyDyes} mixes dyes in as the game's crafting does
 * (D-197): each color's channels averaged, then scaled so the brightest channel is the average of each one's
 * brightest.
 */
public record DyedItemColor(int rgb) {
    public static final Codec<DyedItemColor> CODEC = Codec.INT.xmap(DyedItemColor::new, DyedItemColor::rgb);

    /** The stack's dyed color, else {@code otherwise}, with full alpha. */
    public static int getOrDefault(ItemStack stack, int otherwise) {
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed != null ? ARGB.opaque(dyed.rgb()) : otherwise;
    }

    /** A copy of one of {@code stack} with {@code dyes} mixed into its color. */
    public static ItemStack applyDyes(ItemStack stack, List<DyeColor> dyes) {
        ItemStack dyed = stack.copyWithCount(1);
        dyed.set(DataComponents.DYED_COLOR, applyDyes(stack.get(DataComponents.DYED_COLOR), dyes));
        return dyed;
    }

    /** {@code current} (null for none) with {@code dyes} mixed in. */
    public static DyedItemColor applyDyes(DyedItemColor current, List<DyeColor> dyes) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int brightest = 0;
        int n = 0;
        if (current != null) {
            int r = ARGB.red(current.rgb());
            int g = ARGB.green(current.rgb());
            int b = ARGB.blue(current.rgb());
            brightest += Math.max(r, Math.max(g, b));
            red += r;
            green += g;
            blue += b;
            n++;
        }
        for (DyeColor dye : dyes) {
            int color = dye.getTextureDiffuseColor();
            int r = ARGB.red(color);
            int g = ARGB.green(color);
            int b = ARGB.blue(color);
            brightest += Math.max(r, Math.max(g, b));
            red += r;
            green += g;
            blue += b;
            n++;
        }
        if (n == 0) {
            return current;
        }
        red /= n;
        green /= n;
        blue /= n;
        float average = (float) brightest / n;
        float max = Math.max(red, Math.max(green, blue));
        if (max > 0) {
            red = (int) (red * average / max);
            green = (int) (green * average / max);
            blue = (int) (blue * average / max);
        }
        return new DyedItemColor(ARGB.color(0, red, green, blue));
    }
}
