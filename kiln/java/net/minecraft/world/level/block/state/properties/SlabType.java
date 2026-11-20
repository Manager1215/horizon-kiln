package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code SlabType}: which half a slab fills, or both, by the game's serialized names (a block state's text). */
public enum SlabType implements StringRepresentable {
    TOP, BOTTOM, DOUBLE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
