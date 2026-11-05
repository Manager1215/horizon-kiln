package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code StringTag}: a string. */
public record StringTag(String value) implements Tag {
    public static StringTag valueOf(String value) {
        return new StringTag(value);
    }

    @Override
    public byte getId() {
        return TAG_STRING;
    }

    @Override
    public StringTag copy() {
        return this;
    }

    @Override
    public Object kiln$value() {
        return value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
