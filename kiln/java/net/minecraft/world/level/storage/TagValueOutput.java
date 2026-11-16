package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;

/**
 * Kiln's stand-in for {@code TagValueOutput}: what an entity or block entity saves, into a compound of Kiln's tags
 * ({@code kiln.TagValues}), which {@link #buildResult} gives as the game's {@link CompoundTag} (D-173).
 */
public class TagValueOutput implements ValueOutput {
    private final Map<String, Object> map = new TreeMap<>();
    private final ValueOutput out = kiln.TagValues.output(map);

    protected TagValueOutput() {}

    public static TagValueOutput createWithContext(ProblemReporter reporter, HolderLookup.Provider lookup) {
        return new TagValueOutput();
    }

    public static TagValueOutput createWithoutContext(ProblemReporter reporter) {
        return new TagValueOutput();
    }

    /** Kiln's own: the compound it writes into (D-179). */
    public Map<String, Object> kiln$map() {
        return map;
    }

    public CompoundTag buildResult() {
        return (CompoundTag) kiln.NbtTags.tag(map);
    }

    @Override
    public <T> void store(String key, Codec<T> codec, T value) {
        out.store(key, codec, value);
    }

    @Override
    public <T> void storeNullable(String key, Codec<T> codec, T value) {
        out.storeNullable(key, codec, value);
    }

    @Override
    public <T> void store(MapCodec<T> codec, T value) {
        out.store(codec, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        out.putBoolean(key, value);
    }

    @Override
    public void putByte(String key, byte value) {
        out.putByte(key, value);
    }

    @Override
    public void putShort(String key, short value) {
        out.putShort(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        out.putInt(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        out.putLong(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        out.putFloat(key, value);
    }

    @Override
    public void putDouble(String key, double value) {
        out.putDouble(key, value);
    }

    @Override
    public void putString(String key, String value) {
        out.putString(key, value);
    }

    @Override
    public void putIntArray(String key, int[] value) {
        out.putIntArray(key, value);
    }

    @Override
    public ValueOutput child(String key) {
        return out.child(key);
    }

    @Override
    public ValueOutputList childrenList(String key) {
        return out.childrenList(key);
    }

    @Override
    public <T> TypedOutputList<T> list(String key, Codec<T> codec) {
        return out.list(key, codec);
    }

    @Override
    public void discard(String key) {
        out.discard(key);
    }

    @Override
    public boolean isEmpty() {
        return out.isEmpty();
    }
}
