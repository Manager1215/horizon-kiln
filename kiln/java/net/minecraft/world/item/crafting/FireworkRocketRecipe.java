package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code FireworkRocketRecipe} ({@code minecraft:crafting_special_firework_rocket}): paper, one to
 * three gunpowder and any firework stars make rockets that fly as long as the gunpowder says and burst as the stars
 * do, as the game makes them (D-197). A recipe viewer reads its fields.
 */
public class FireworkRocketRecipe extends CustomRecipe {
    public static final MapCodec<FireworkRocketRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.FireworkRocketRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, FireworkRocketRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.FireworkRocketRecipe.STREAM_CODEC");
    public static final RecipeSerializer<FireworkRocketRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient shell;
    public final Ingredient fuel;
    public final Ingredient star;
    public final ItemStackTemplate result;

    public FireworkRocketRecipe(Ingredient shell, Ingredient fuel, Ingredient star, ItemStackTemplate result) {
        this.shell = shell;
        this.fuel = fuel;
        this.star = star;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean shelled = false;
        int fuels = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (shell.test(stack)) {
                if (shelled) {
                    return false;
                }
                shelled = true;
            } else if (fuel.test(stack)) {
                if (++fuels > 3) {
                    return false;
                }
            } else if (!star.test(stack)) {
                return false;
            }
        }
        return shelled && fuels >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        java.util.List<net.minecraft.world.item.component.FireworkExplosion> explosions = new java.util.ArrayList<>();
        int fuels = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (fuel.test(stack)) {
                fuels++;
            } else if (star.test(stack)) {
                net.minecraft.world.item.component.FireworkExplosion explosion =
                        stack.get(net.minecraft.core.component.DataComponents.FIREWORK_EXPLOSION);
                if (explosion != null) {
                    explosions.add(explosion);
                }
            }
        }
        ItemStack rockets = result.create();
        rockets.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                new net.minecraft.world.item.component.Fireworks(fuels, explosions));
        return rockets;
    }

    @Override
    public RecipeSerializer<FireworkRocketRecipe> getSerializer() {
        return SERIALIZER;
    }
}
