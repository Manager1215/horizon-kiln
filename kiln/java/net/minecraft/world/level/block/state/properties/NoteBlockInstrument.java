package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code NoteBlockInstrument}: a note block's sound, by the game's serialized names (a block state's text). */
public enum NoteBlockInstrument implements StringRepresentable {
    HARP, BASEDRUM, SNARE, HAT, BASS, FLUTE, BELL, GUITAR, CHIME, XYLOPHONE, IRON_XYLOPHONE, COW_BELL, DIDGERIDOO, BIT, BANJO, PLING, TRUMPET, TRUMPET_EXPOSED, TRUMPET_OXIDIZED, TRUMPET_WEATHERED, ZOMBIE, SKELETON, CREEPER, DRAGON, WITHER_SKELETON, PIGLIN, CUSTOM_HEAD;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }

    /** Whether a note block of this sound plays the 25 notes (the mob heads' and the custom head's don't). */
    public boolean isTunable() {
        return ordinal() < ZOMBIE.ordinal();
    }
}
