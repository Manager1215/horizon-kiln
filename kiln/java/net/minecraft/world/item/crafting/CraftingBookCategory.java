package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code CraftingBookCategory}: the recipe book's tab a crafting recipe shows in, by its data name. */
public enum CraftingBookCategory implements StringRepresentable {
    BUILDING("building"),
    REDSTONE("redstone"),
    EQUIPMENT("equipment"),
    MISC("misc");

    public static final Codec<CraftingBookCategory> CODEC = StringRepresentable.fromEnum(CraftingBookCategory::values);
    public static final IntFunction<CraftingBookCategory> BY_ID = i -> i >= 0 && i < values().length ? values()[i] : MISC;

    private final String name;

    CraftingBookCategory(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
