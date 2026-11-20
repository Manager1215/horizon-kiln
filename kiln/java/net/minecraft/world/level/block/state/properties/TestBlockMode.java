package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code TestBlockMode}: a test block's mode, by the game's serialized names (a block state's text). */
public enum TestBlockMode implements StringRepresentable {
    START, LOG, FAIL, ACCEPT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
