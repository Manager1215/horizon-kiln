package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code StructureMode}: a structure block's mode, by the game's serialized names (a block state's text). */
public enum StructureMode implements StringRepresentable {
    SAVE, LOAD, CORNER, DATA;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
