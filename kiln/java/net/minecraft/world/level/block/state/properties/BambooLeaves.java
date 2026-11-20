package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code BambooLeaves}: a bamboo stalk's leaves, by the game's serialized names (a block state's text). */
public enum BambooLeaves implements StringRepresentable {
    NONE, SMALL, LARGE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
