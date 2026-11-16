package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;

/**
 * Kiln's stand-in for {@code TagValueInput}: a compound read as an entity or block entity reads its data, over
 * Kiln's tags ({@code kiln.TagValues}, D-173).
 */
public class TagValueInput implements ValueInput {
    private final ValueInput in;

    protected TagValueInput() {
        this(new java.util.TreeMap<>());
    }

    private TagValueInput(Map<String, Object> map) {
        this.in = kiln.TagValues.input(map);
    }

    @SuppressWarnings("unchecked")
    public static ValueInput create(ProblemReporter reporter, HolderLookup.Provider lookup, CompoundTag tag) {
        Object value = tag == null ? null : tag.kiln$value();
        return new TagValueInput(value instanceof Map<?, ?> map ? (Map<String, Object>) map : new java.util.TreeMap<>());
    }

    /** Kiln's own: the compound it reads (D-179). */
    public Map<String, Object> kiln$map() {
        return kiln.TagValues.map(in);
    }

    @Override
    public <T> Optional<T> read(String key, Codec<T> codec) {
        return in.read(key, codec);
    }

    @Override
    public <T> Optional<T> read(MapCodec<T> codec) {
        return in.read(codec);
    }

    @Override
    public Optional<ValueInput> child(String key) {
        return in.child(key);
    }

    @Override
    public ValueInput childOrEmpty(String key) {
        return in.childOrEmpty(key);
    }

    @Override
    public Optional<ValueInputList> childrenList(String key) {
        return in.childrenList(key);
    }

    @Override
    public ValueInputList childrenListOrEmpty(String key) {
        return in.childrenListOrEmpty(key);
    }

    @Override
    public <T> Optional<TypedInputList<T>> list(String key, Codec<T> codec) {
        return in.list(key, codec);
    }

    @Override
    public <T> TypedInputList<T> listOrEmpty(String key, Codec<T> codec) {
        return in.listOrEmpty(key, codec);
    }

    @Override
    public boolean getBooleanOr(String key, boolean fallback) {
        return in.getBooleanOr(key, fallback);
    }

    @Override
    public byte getByteOr(String key, byte fallback) {
        return in.getByteOr(key, fallback);
    }

    @Override
    public int getShortOr(String key, short fallback) {
        return in.getShortOr(key, fallback);
    }

    @Override
    public Optional<Integer> getInt(String key) {
        return in.getInt(key);
    }

    @Override
    public int getIntOr(String key, int fallback) {
        return in.getIntOr(key, fallback);
    }

    @Override
    public long getLongOr(String key, long fallback) {
        return in.getLongOr(key, fallback);
    }

    @Override
    public Optional<Long> getLong(String key) {
        return in.getLong(key);
    }

    @Override
    public float getFloatOr(String key, float fallback) {
        return in.getFloatOr(key, fallback);
    }

    @Override
    public double getDoubleOr(String key, double fallback) {
        return in.getDoubleOr(key, fallback);
    }

    @Override
    public Optional<String> getString(String key) {
        return in.getString(key);
    }

    @Override
    public String getStringOr(String key, String fallback) {
        return in.getStringOr(key, fallback);
    }

    @Override
    public Optional<int[]> getIntArray(String key) {
        return in.getIntArray(key);
    }

    @Override
    public HolderLookup.Provider lookup() {
        return in.lookup();
    }
}
