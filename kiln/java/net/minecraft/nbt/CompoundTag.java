package net.minecraft.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import kiln.NbtTags;

/** Kiln's stand-in for {@code CompoundTag}: named tags. */
public final class CompoundTag implements Tag {
    public static final Codec<CompoundTag> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue() instanceof CompoundTag c
                    ? DataResult.success(c) : DataResult.error(() -> "not a compound: " + dynamic),
            c -> new Dynamic<>(NbtOps.INSTANCE, c));
    private final Map<String, Tag> tags;

    public CompoundTag() {
        this(new TreeMap<>());
    }

    private CompoundTag(Map<String, Tag> tags) {
        this.tags = tags;
    }

    public Set<String> keySet() {
        return tags.keySet();
    }

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public boolean contains(String key) {
        return tags.containsKey(key);
    }

    public Tag get(String key) {
        return tags.get(key);
    }

    public Tag put(String key, Tag tag) {
        return tags.put(key, tag);
    }

    public Tag remove(String key) {
        return tags.remove(key);
    }

    public void putByte(String key, byte value) {
        put(key, ByteTag.valueOf(value));
    }

    public void putShort(String key, short value) {
        put(key, ShortTag.valueOf(value));
    }

    public void putInt(String key, int value) {
        put(key, IntTag.valueOf(value));
    }

    public void putLong(String key, long value) {
        put(key, LongTag.valueOf(value));
    }

    public void putFloat(String key, float value) {
        put(key, FloatTag.valueOf(value));
    }

    public void putDouble(String key, double value) {
        put(key, DoubleTag.valueOf(value));
    }

    public void putString(String key, String value) {
        put(key, StringTag.valueOf(value));
    }

    public void putBoolean(String key, boolean value) {
        put(key, ByteTag.valueOf(value));
    }

    public void putIntArray(String key, int[] value) {
        put(key, new IntArrayTag(value));
    }

    public void putLongArray(String key, long[] value) {
        put(key, new LongArrayTag(value));
    }

    public void putByteArray(String key, byte[] value) {
        put(key, new ByteArrayTag(value));
    }

    private Optional<Number> number(String key) {
        return tags.get(key) instanceof NumericTag n ? Optional.of(n.box()) : Optional.empty();
    }

    public Optional<Byte> getByte(String key) {
        return number(key).map(Number::byteValue);
    }

    public byte getByteOr(String key, byte fallback) {
        return getByte(key).orElse(fallback);
    }

    public Optional<Short> getShort(String key) {
        return number(key).map(Number::shortValue);
    }

    public short getShortOr(String key, short fallback) {
        return getShort(key).orElse(fallback);
    }

    public Optional<Integer> getInt(String key) {
        return number(key).map(Number::intValue);
    }

    public int getIntOr(String key, int fallback) {
        return getInt(key).orElse(fallback);
    }

    public Optional<Long> getLong(String key) {
        return number(key).map(Number::longValue);
    }

    public long getLongOr(String key, long fallback) {
        return getLong(key).orElse(fallback);
    }

    public Optional<Float> getFloat(String key) {
        return number(key).map(Number::floatValue);
    }

    public float getFloatOr(String key, float fallback) {
        return getFloat(key).orElse(fallback);
    }

    public Optional<Double> getDouble(String key) {
        return number(key).map(Number::doubleValue);
    }

    public double getDoubleOr(String key, double fallback) {
        return getDouble(key).orElse(fallback);
    }

    public Optional<Boolean> getBoolean(String key) {
        return getByte(key).map(b -> b != 0);
    }

    public boolean getBooleanOr(String key, boolean fallback) {
        return getBoolean(key).orElse(fallback);
    }

    public Optional<String> getString(String key) {
        return tags.get(key) instanceof StringTag s ? Optional.of(s.value()) : Optional.empty();
    }

    public String getStringOr(String key, String fallback) {
        return getString(key).orElse(fallback);
    }

    public Optional<int[]> getIntArray(String key) {
        return tags.get(key) instanceof IntArrayTag a ? Optional.of(a.getAsIntArray()) : Optional.empty();
    }

    public Optional<long[]> getLongArray(String key) {
        return tags.get(key) instanceof LongArrayTag a ? Optional.of(a.getAsLongArray()) : Optional.empty();
    }

    public Optional<byte[]> getByteArray(String key) {
        return tags.get(key) instanceof ByteArrayTag a ? Optional.of(a.getAsByteArray()) : Optional.empty();
    }

    public Optional<CompoundTag> getCompound(String key) {
        return tags.get(key) instanceof CompoundTag c ? Optional.of(c) : Optional.empty();
    }

    public CompoundTag getCompoundOrEmpty(String key) {
        return getCompound(key).orElseGet(CompoundTag::new);
    }

    public Optional<ListTag> getList(String key) {
        return tags.get(key) instanceof ListTag l ? Optional.of(l) : Optional.empty();
    }

    public ListTag getListOrEmpty(String key) {
        return getList(key).orElseGet(ListTag::new);
    }

    public CompoundTag merge(CompoundTag other) {
        for (Map.Entry<String, Tag> e : other.tags.entrySet()) {
            if (e.getValue() instanceof CompoundTag c && tags.get(e.getKey()) instanceof CompoundTag mine) {
                mine.merge(c);
            } else {
                tags.put(e.getKey(), e.getValue().copy());
            }
        }
        return this;
    }

    public <T> void store(String key, Codec<T> codec, T value) {
        store(key, codec, NbtOps.INSTANCE, value);
    }

    public <T> void store(String key, Codec<T> codec, DynamicOps<Tag> ops, T value) {
        codec.encodeStart(ops, value).result().ifPresent(tag -> put(key, tag));
    }

    public <T> Optional<T> read(String key, Codec<T> codec) {
        return read(key, codec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> read(String key, Codec<T> codec, DynamicOps<Tag> ops) {
        Tag tag = tags.get(key);
        return tag == null ? Optional.empty() : codec.parse(ops, tag).result();
    }

    @Override
    public byte getId() {
        return TAG_COMPOUND;
    }

    @Override
    public CompoundTag copy() {
        Map<String, Tag> copy = new TreeMap<>();
        tags.forEach((k, v) -> copy.put(k, v.copy()));
        return new CompoundTag(copy);
    }

    @Override
    public Object kiln$value() {
        Map<String, Object> values = new TreeMap<>();
        tags.forEach((k, v) -> values.put(k, v.kiln$value()));
        return values;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompoundTag other && tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
