package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code Tilt}: a big dripleaf's tilt, by the game's serialized names (a block state's text). */
public enum Tilt implements StringRepresentable {
    NONE, UNSTABLE, PARTIAL, FULL;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
