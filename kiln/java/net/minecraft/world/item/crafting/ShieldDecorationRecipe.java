package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code ShieldDecorationRecipe} ({@code minecraft:crafting_special_shielddecoration}): a banner
 * and a shield without patterns make the shield with the banner's patterns and color, as the game makes it (D-197).
 */
public class ShieldDecorationRecipe extends CustomRecipe {
    public static final MapCodec<ShieldDecorationRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.ShieldDecorationRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldDecorationRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ShieldDecorationRecipe.STREAM_CODEC");
    public static final RecipeSerializer<ShieldDecorationRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient banner;
    public final Ingredient target;
    public final ItemStackTemplate result;

    public ShieldDecorationRecipe(Ingredient banner, Ingredient target, ItemStackTemplate result) {
        this.banner = banner;
        this.target = target;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack shield = ItemStack.EMPTY;
        ItemStack flag = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (banner.test(stack)) {
                if (!flag.isEmpty()) {
                    return false;
                }
                flag = stack;
            } else if (target.test(stack)) {
                if (!shield.isEmpty() || kiln$layers(stack) > 0) {
                    return false;
                }
                shield = stack;
            } else {
                return false;
            }
        }
        return !shield.isEmpty() && !flag.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack shield = kiln$first(input, target);
        ItemStack flag = kiln$first(input, banner);
        if (shield.isEmpty() || flag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack decorated = result.apply(shield.getComponentsPatch());
        decorated.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, flag.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS));
        if (flag.getItem() instanceof net.minecraft.world.item.BannerItem item) {
            decorated.set(net.minecraft.core.component.DataComponents.BASE_COLOR, item.getColor());
        }
        return decorated;
    }

    @Override
    public RecipeSerializer<ShieldDecorationRecipe> getSerializer() {
        return SERIALIZER;
    }
}
