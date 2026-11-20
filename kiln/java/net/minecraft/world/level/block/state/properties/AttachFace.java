package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code AttachFace}: where a button, lever or grindstone hangs, by the game's serialized names (a block state's text). */
public enum AttachFace implements StringRepresentable {
    FLOOR, WALL, CEILING;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
