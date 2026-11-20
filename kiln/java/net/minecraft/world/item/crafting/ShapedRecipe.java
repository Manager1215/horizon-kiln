package net.minecraft.world.item.crafting;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code ShapedRecipe}: a crafting recipe whose ingredients lie in a pattern. */
public class ShapedRecipe extends NormalCraftingRecipe {
    /**
     * As a data pack writes one: {@code group}, {@code category}, {@code key} and {@code pattern}, {@code result},
     * {@code show_notification}. A mod's own recipe made of a shaped one reads it with this (Tech Reborn's rolling
     * machine, D-165).
     */
    public static final com.mojang.serialization.MapCodec<ShapedRecipe> MAP_CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::group),
                    CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC)
                            .forGetter(ShapedRecipe::category),
                    ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
                    com.mojang.serialization.Codec.BOOL.optionalFieldOf("show_notification", true)
                            .forGetter(ShapedRecipe::showNotification))
                    .apply(i, (group, category, pattern, result, notification) -> new ShapedRecipe(
                            new Recipe.CommonInfo(notification), new CraftingRecipe.CraftingBookInfo(category, group),
                            pattern, result)));

    public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC,
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ShapedRecipe.SERIALIZER"));

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public ShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    protected ShapedRecipe() {
        this.pattern = null;
        this.result = null;
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return SERIALIZER;
    }

    public List<Optional<Ingredient>> getIngredients() {
        return pattern.ingredients();
    }

    /** Its pattern's slots, an empty one with no ingredient, as a recipe viewer places them (D-234). */
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.createFromOptionals(pattern.ingredients());
    }

    /** Kiln's own: its pattern. */
    public ShapedRecipePattern kiln$pattern() {
        return pattern;
    }

    /** Kiln's own: what it makes. */
    public ItemStackTemplate kiln$result() {
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    public int getWidth() {
        return pattern.width();
    }

    public int getHeight() {
        return pattern.height();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(pattern.width(), pattern.height(),
                pattern.ingredients().stream().map(i -> i.map(Ingredient::display)
                        .orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
