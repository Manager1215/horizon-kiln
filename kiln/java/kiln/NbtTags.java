package kiln;

import java.util.List;
import java.util.Map;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** The game's tag classes and Kiln's tags ({@link Snbt}): one made from the other, and as SNBT text. */
public final class NbtTags {
    private NbtTags() {}

    /** A tag as the game writes it in text. */
    public static String text(Tag tag) {
        Object value = tag.kiln$value();
        return value == null ? "END" : Snbt.write(value);
    }

    /** The game's tag for a value of Kiln's tags. */
    @SuppressWarnings("unchecked")
    public static Tag tag(Object value) {
        return switch (value) {
            case null -> EndTag.INSTANCE;
            case Byte b -> ByteTag.valueOf(b);
            case Short s -> ShortTag.valueOf(s);
            case Integer i -> IntTag.valueOf(i);
            case Long l -> LongTag.valueOf(l);
            case Float f -> FloatTag.valueOf(f);
            case Double d -> DoubleTag.valueOf(d);
            case String s -> StringTag.valueOf(s);
            case byte[] a -> new ByteArrayTag(a.clone());
            case int[] a -> new IntArrayTag(a.clone());
            case long[] a -> new LongArrayTag(a.clone());
            case List<?> list -> {
                ListTag tags = new ListTag();
                list.forEach(v -> tags.add(tag(v)));
                yield tags;
            }
            case Map<?, ?> map -> {
                CompoundTag compound = new CompoundTag();
                ((Map<String, Object>) map).forEach((k, v) -> compound.put(k, tag(v)));
                yield compound;
            }
            default -> throw new IllegalArgumentException("not a tag: " + value.getClass().getName());
        };
    }
}
