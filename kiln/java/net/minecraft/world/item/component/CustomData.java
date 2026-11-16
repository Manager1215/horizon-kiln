package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;

/**
 * Kiln's stand-in for {@code CustomData}: a stack's own tags ({@code minecraft:custom_data}), which only mods and
 * commands read. It is saved with the stack as the game saves it, so it goes back to the game whole.
 */
public final class CustomData {
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = CompoundTag.CODEC.xmap(CustomData::new, d -> d.tag);

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    /** {@code tag}'s copy; {@link #EMPTY} when it holds nothing. */
    public static CustomData of(CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : new CustomData(tag.copy());
    }

    /** A copy of its tags, to change and set back. */
    public CompoundTag copyTag() {
        return tag.copy();
    }

    public boolean isEmpty() {
        return tag.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CustomData d && d.tag.equals(tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
