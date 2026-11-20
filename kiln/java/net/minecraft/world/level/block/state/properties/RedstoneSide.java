package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code RedstoneSide}: how redstone wire joins a side, by the game's serialized names (a block state's text). */
public enum RedstoneSide implements StringRepresentable {
    UP, SIDE, NONE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
