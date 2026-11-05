package net.minecraft.world.item.crafting;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code CookingBookCategory}: where a cooking recipe shows in the recipe book. */
public enum CookingBookCategory implements StringRepresentable {
    FOOD("food"),
    BLOCKS("blocks"),
    MISC("misc");

    private final String name;

    CookingBookCategory(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
