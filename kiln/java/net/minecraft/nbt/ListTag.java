package net.minecraft.nbt;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kiln.NbtTags;

/** Kiln's stand-in for {@code ListTag}: a list of tags. */
public final class ListTag extends AbstractList<Tag> implements CollectionTag {
    private final List<Tag> list;

    public ListTag() {
        this(new ArrayList<>());
    }

    public ListTag(List<Tag> list) {
        this.list = list;
    }

    @Override
    public Tag get(int index) {
        return list.get(index);
    }

    @Override
    public Tag set(int index, Tag tag) {
        return list.set(index, tag);
    }

    @Override
    public void add(int index, Tag tag) {
        list.add(index, tag);
    }

    @Override
    public Tag remove(int index) {
        return list.remove(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    public Optional<CompoundTag> getCompound(int index) {
        return index < size() && get(index) instanceof CompoundTag c ? Optional.of(c) : Optional.empty();
    }

    public CompoundTag getCompoundOrEmpty(int index) {
        return getCompound(index).orElseGet(CompoundTag::new);
    }

    public Optional<String> getString(int index) {
        return index < size() && get(index) instanceof StringTag s ? Optional.of(s.value()) : Optional.empty();
    }

    public String getStringOr(int index, String fallback) {
        return getString(index).orElse(fallback);
    }

    @Override
    public byte getId() {
        return TAG_LIST;
    }

    @Override
    public ListTag copy() {
        List<Tag> copy = new ArrayList<>();
        for (Tag t : list) {
            copy.add(t.copy());
        }
        return new ListTag(copy);
    }

    @Override
    public Object kiln$value() {
        List<Object> values = new ArrayList<>();
        for (Tag t : list) {
            values.add(t.kiln$value());
        }
        return values;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
