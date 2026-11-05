package net.minecraft.nbt;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.DynamicOps;
import kiln.NbtTags;
import kiln.Snbt;

/** Kiln's stand-in for {@code TagParser}: SNBT text read into tags, then into {@code ops}' values. */
public final class TagParser<T> {
    private final DynamicOps<T> ops;

    private TagParser(DynamicOps<T> ops) {
        this.ops = ops;
    }

    public static <T> TagParser<T> create(DynamicOps<T> ops) {
        return new TagParser<>(ops);
    }

    public static CompoundTag parseCompoundFully(String text) {
        return (CompoundTag) NbtTags.tag(Snbt.compound(text));
    }

    /** What is left of {@code reader}, as one value. */
    public T parseFully(StringReader reader) {
        String rest = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());
        Tag tag = NbtTags.tag(Snbt.value(rest));
        return NbtOps.INSTANCE.convertTo(ops, tag);
    }
}
