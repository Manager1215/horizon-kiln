package net.minecraft.nbt;

/** Kiln's stand-in for {@code CollectionTag}: a list of tags. */
public interface CollectionTag extends Tag {
    Tag get(int index);

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }
}
