package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code FireworkStarRecipe} ({@code minecraft:crafting_special_firework_star}): gunpowder, dyes
 * and at most one shape item, trail item and twinkle item make a firework star, its explosion as the game makes it
 * (D-197). A recipe viewer reads its fields.
 */
public class FireworkStarRecipe extends CustomRecipe {
    public static final MapCodec<FireworkStarRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.FireworkStarRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, FireworkStarRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.FireworkStarRecipe.STREAM_CODEC");
    public static final RecipeSerializer<FireworkStarRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Map<FireworkExplosion.Shape, Ingredient> shapes;
    public final Ingredient trail;
    public final Ingredient twinkle;
    public final Ingredient fuel;
    public final Ingredient dye;
    public final ItemStackTemplate result;

    public FireworkStarRecipe(Map<FireworkExplosion.Shape, Ingredient> shapes, Ingredient trail, Ingredient twinkle,
            Ingredient fuel, Ingredient dye, ItemStackTemplate result) {
        this.shapes = Map.copyOf(shapes);
        this.trail = trail;
        this.twinkle = twinkle;
        this.fuel = fuel;
        this.dye = dye;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean shaped = false;
        boolean trailed = false;
        boolean twinkled = false;
        boolean fueled = false;
        boolean dyed = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (shapeOf(stack) != null) {
                if (shaped) {
                    return false;
                }
                shaped = true;
            } else if (twinkle.test(stack)) {
                if (twinkled) {
                    return false;
                }
                twinkled = true;
            } else if (trail.test(stack)) {
                if (trailed) {
                    return false;
                }
                trailed = true;
            } else if (fuel.test(stack)) {
                if (fueled) {
                    return false;
                }
                fueled = true;
            } else if (dye.test(stack)) {
                dyed = true;
            } else {
                return false;
            }
        }
        return fueled && dyed;
    }

    /** Kiln's own: the shape a stack gives the explosion, or null when it is no shape item. */
    private FireworkExplosion.Shape shapeOf(ItemStack stack) {
        for (Map.Entry<FireworkExplosion.Shape, Ingredient> shape : shapes.entrySet()) {
            if (shape.getValue().test(stack)) {
                return shape.getKey();
            }
        }
        return null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.SMALL_BALL;
        boolean trailed = false;
        boolean twinkled = false;
        it.unimi.dsi.fastutil.ints.IntList colors = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            FireworkExplosion.Shape own = shapeOf(stack);
            if (own != null) {
                shape = own;
            } else if (twinkle.test(stack)) {
                twinkled = true;
            } else if (trail.test(stack)) {
                trailed = true;
            } else if (dye.test(stack)) {
                net.minecraft.world.item.DyeColor color = stack.get(net.minecraft.core.component.DataComponents.DYE);
                if (color != null) {
                    colors.add(color.getFireworkColor());
                }
            }
        }
        ItemStack star = result.create();
        star.set(net.minecraft.core.component.DataComponents.FIREWORK_EXPLOSION,
                new FireworkExplosion(shape, colors, it.unimi.dsi.fastutil.ints.IntList.of(), trailed, twinkled));
        return star;
    }

    @Override
    public RecipeSerializer<FireworkStarRecipe> getSerializer() {
        return SERIALIZER;
    }
}
