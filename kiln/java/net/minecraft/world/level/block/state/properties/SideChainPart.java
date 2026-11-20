package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code SideChainPart}: a shelf's place in a row, by the game's serialized names (a block state's text). */
public enum SideChainPart implements StringRepresentable {
    UNCONNECTED, RIGHT, CENTER, LEFT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
