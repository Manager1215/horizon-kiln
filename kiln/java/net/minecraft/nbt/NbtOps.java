package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/** Kiln's stand-in for {@code NbtOps}: codecs writing and reading the game's tags. */
public final class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {}

    @Override
    public Tag empty() {
        return EndTag.INSTANCE;
    }

    @Override
    public <U> U convertTo(DynamicOps<U> out, Tag input) {
        return switch (input) {
            case EndTag e -> out.empty();
            case ByteTag b -> out.createByte(b.value());
            case ShortTag s -> out.createShort(s.value());
            case IntTag i -> out.createInt(i.value());
            case LongTag l -> out.createLong(l.value());
            case FloatTag f -> out.createFloat(f.value());
            case DoubleTag d -> out.createDouble(d.value());
            case StringTag s -> out.createString(s.value());
            case ByteArrayTag a -> out.createByteList(ByteBuffer.wrap(a.getAsByteArray()));
            case IntArrayTag a -> out.createIntList(IntStream.of(a.getAsIntArray()));
            case LongArrayTag a -> out.createLongList(LongStream.of(a.getAsLongArray()));
            case ListTag l -> convertList(out, l);
            case CompoundTag c -> convertMap(out, c);
            default -> out.empty();
        };
    }

    @Override
    public DataResult<Number> getNumberValue(Tag input) {
        return input instanceof NumericTag n ? DataResult.success(n.box()) : DataResult.error(() -> "not a number");
    }

    @Override
    public Tag createNumeric(Number value) {
        return DoubleTag.valueOf(value.doubleValue());
    }

    @Override
    public Tag createByte(byte value) {
        return ByteTag.valueOf(value);
    }

    @Override
    public Tag createShort(short value) {
        return ShortTag.valueOf(value);
    }

    @Override
    public Tag createInt(int value) {
        return IntTag.valueOf(value);
    }

    @Override
    public Tag createLong(long value) {
        return LongTag.valueOf(value);
    }

    @Override
    public Tag createFloat(float value) {
        return FloatTag.valueOf(value);
    }

    @Override
    public Tag createDouble(double value) {
        return DoubleTag.valueOf(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(Tag input) {
        return input instanceof NumericTag n ? DataResult.success(n.byteValue() != 0)
                : DataResult.error(() -> "not a boolean");
    }

    @Override
    public Tag createBoolean(boolean value) {
        return ByteTag.valueOf(value);
    }

    @Override
    public DataResult<String> getStringValue(Tag input) {
        return input instanceof StringTag s ? DataResult.success(s.value()) : DataResult.error(() -> "not a string");
    }

    @Override
    public Tag createString(String value) {
        return StringTag.valueOf(value);
    }

    @Override
    public DataResult<Tag> mergeToList(Tag list, Tag value) {
        if (!(list instanceof ListTag) && list != EndTag.INSTANCE) {
            return DataResult.error(() -> "not a list: " + list);
        }
        ListTag merged = list instanceof ListTag l ? l.copy() : new ListTag();
        merged.add(value);
        return DataResult.success(merged);
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && map != EndTag.INSTANCE) {
            return DataResult.error(() -> "not a map: " + map);
        }
        if (!(key instanceof StringTag k)) {
            return DataResult.error(() -> "a key that isn't a string: " + key);
        }
        CompoundTag merged = map instanceof CompoundTag c ? c.copy() : new CompoundTag();
        merged.put(k.value(), value);
        return DataResult.success(merged);
    }

    @Override
    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag input) {
        if (!(input instanceof CompoundTag c)) {
            return DataResult.error(() -> "not a map: " + input);
        }
        List<Pair<Tag, Tag>> pairs = new ArrayList<>();
        for (String key : c.keySet()) {
            pairs.add(Pair.of(StringTag.valueOf(key), c.get(key)));
        }
        return DataResult.success(pairs.stream());
    }

    @Override
    public Tag createMap(Stream<Pair<Tag, Tag>> map) {
        CompoundTag c = new CompoundTag();
        map.forEach(p -> c.put(((StringTag) p.getFirst()).value(), p.getSecond()));
        return c;
    }

    @Override
    public DataResult<Stream<Tag>> getStream(Tag input) {
        return switch (input) {
            case ListTag l -> DataResult.success(new ArrayList<>(l).stream());
            case ByteArrayTag a -> DataResult.success(IntStream.range(0, a.size())
                    .mapToObj(i -> ByteTag.valueOf(a.getAsByteArray()[i])));
            case IntArrayTag a -> DataResult.success(IntStream.of(a.getAsIntArray()).mapToObj(IntTag::valueOf));
            case LongArrayTag a -> DataResult.success(LongStream.of(a.getAsLongArray()).mapToObj(LongTag::valueOf));
            default -> DataResult.error(() -> "not a list: " + input);
        };
    }

    @Override
    public Tag createList(Stream<Tag> input) {
        ListTag list = new ListTag();
        input.forEach(list::add);
        return list;
    }

    @Override
    public Tag remove(Tag input, String key) {
        if (input instanceof CompoundTag c) {
            CompoundTag copy = c.copy();
            copy.remove(key);
            return copy;
        }
        return input;
    }

    @Override
    public Tag emptyMap() {
        return new CompoundTag();
    }

    @Override
    public Tag emptyList() {
        return new ListTag();
    }

    @Override
    public String toString() {
        return "NBT";
    }

    /** Kiln's own: a map's entries by key, for code that walks one. */
    static Map<String, Tag> entries(CompoundTag c) {
        Map<String, Tag> entries = new java.util.TreeMap<>();
        c.keySet().forEach(k -> entries.put(k, c.get(k)));
        return entries;
    }
}
