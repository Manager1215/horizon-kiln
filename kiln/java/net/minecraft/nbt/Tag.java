package net.minecraft.nbt;

import kiln.NbtTags;

/**
 * Kiln's stand-in for {@code Tag}: a value of the game's NBT. Each kind is a class of its own, as in the game; as text
 * each is the SNBT the game writes.
 */
public interface Tag {
    byte TAG_END = 0;
    byte TAG_BYTE = 1;
    byte TAG_SHORT = 2;
    byte TAG_INT = 3;
    byte TAG_LONG = 4;
    byte TAG_FLOAT = 5;
    byte TAG_DOUBLE = 6;
    byte TAG_BYTE_ARRAY = 7;
    byte TAG_STRING = 8;
    byte TAG_LIST = 9;
    byte TAG_COMPOUND = 10;
    byte TAG_INT_ARRAY = 11;
    byte TAG_LONG_ARRAY = 12;

    byte getId();

    Tag copy();

    /** Kiln's own: the value as Kiln's tags have it ({@code kiln.Snbt}). */
    Object kiln$value();

    static String kiln$text(Tag tag) {
        return NbtTags.text(tag);
    }
}
