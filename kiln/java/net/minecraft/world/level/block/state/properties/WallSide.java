package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code WallSide}: a wall's side: none, low or tall, by the game's serialized names (a block state's text). */
public enum WallSide implements StringRepresentable {
    NONE, LOW, TALL;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
