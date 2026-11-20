package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code Half}: the half of a block a stair or trapdoor takes, by the game's serialized names (a block state's text). */
public enum Half implements StringRepresentable {
    TOP, BOTTOM;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
