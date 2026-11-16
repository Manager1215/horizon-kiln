package net.minecraft.world.item.crafting;

/** Kiln's stand-in for {@code NormalCraftingRecipe}: a crafting recipe with its book's category and group. */
public abstract class NormalCraftingRecipe implements CraftingRecipe {
    private final Recipe.CommonInfo commonInfo;
    private final CraftingRecipe.CraftingBookInfo bookInfo;

    protected NormalCraftingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo) {
        this.commonInfo = commonInfo;
        this.bookInfo = bookInfo;
    }

    protected NormalCraftingRecipe() {
        this(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""));
    }

    @Override
    public CraftingBookCategory category() {
        return bookInfo.category();
    }

    public String group() {
        return bookInfo.group();
    }

    public boolean showNotification() {
        return commonInfo.showNotification();
    }

    public PlacementInfo placementInfo() {
        kiln.Missing.called("net.minecraft.world.item.crafting.NormalCraftingRecipe.placementInfo");
        return null;
    }
}
