package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code ChestType}: a chest's half of a double chest, by the game's serialized names (a block state's text). */
public enum ChestType implements StringRepresentable {
    SINGLE, LEFT, RIGHT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }

    /** The other half of a double chest. */
    public ChestType getOpposite() {
        return switch (this) {
            case SINGLE -> SINGLE;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
