package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code FireworkStarFadeRecipe} ({@code minecraft:crafting_special_firework_star_fade}): a
 * firework star and dyes make the star fading to their colors, as the game makes it (D-197). A recipe viewer reads its
 * fields.
 */
public class FireworkStarFadeRecipe extends CustomRecipe {
    public static final MapCodec<FireworkStarFadeRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.FireworkStarFadeRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, FireworkStarFadeRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.FireworkStarFadeRecipe.STREAM_CODEC");
    public static final RecipeSerializer<FireworkStarFadeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient target;
    public final Ingredient dye;
    public final ItemStackTemplate result;

    public FireworkStarFadeRecipe(Ingredient target, Ingredient dye, ItemStackTemplate result) {
        this.target = target;
        this.dye = dye;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return kiln$only(input, target, dye) && kiln$count(input, target) == 1 && kiln$count(input, dye) >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack star = kiln$first(input, target);
        if (star.isEmpty()) {
            return ItemStack.EMPTY;
        }
        it.unimi.dsi.fastutil.ints.IntList fade = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (net.minecraft.world.item.DyeColor color : kiln$dyes(input, dye)) {
            fade.add(color.getFireworkColor());
        }
        ItemStack faded = result.apply(star.getComponentsPatch());
        // The star's own, read from it: a patch carries a typed component's saved form, not always its value.
        net.minecraft.world.item.component.FireworkExplosion explosion =
                star.get(net.minecraft.core.component.DataComponents.FIREWORK_EXPLOSION);
        faded.set(net.minecraft.core.component.DataComponents.FIREWORK_EXPLOSION,
                (explosion != null ? explosion : net.minecraft.world.item.component.FireworkExplosion.DEFAULT)
                        .withFadeColors(fade));
        return faded;
    }

    @Override
    public RecipeSerializer<FireworkStarFadeRecipe> getSerializer() {
        return SERIALIZER;
    }
}
