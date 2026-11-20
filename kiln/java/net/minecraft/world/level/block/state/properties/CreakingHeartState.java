package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code CreakingHeartState}: a creaking heart's state, by the game's serialized names (a block state's text). */
public enum CreakingHeartState implements StringRepresentable {
    UPROOTED, DORMANT, AWAKE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
