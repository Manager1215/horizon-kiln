package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code PistonType}: a piston's kind, by the game's serialized names (a block state's text). */
public enum PistonType implements StringRepresentable {
    DEFAULT, STICKY;

    @Override
    public String getSerializedName() {
        return switch (this) {
            case DEFAULT -> "normal";
            default -> name().toLowerCase(Locale.ROOT);
        };
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
