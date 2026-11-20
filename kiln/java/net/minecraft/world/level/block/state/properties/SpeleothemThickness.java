package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code SpeleothemThickness}: a dripstone's thickness, by the game's serialized names (a block state's text). */
public enum SpeleothemThickness implements StringRepresentable {
    TIP_MERGE, TIP, FRUSTUM, MIDDLE, BASE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
