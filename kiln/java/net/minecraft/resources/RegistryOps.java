package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;

/**
 * Kiln's stand-in for {@code RegistryOps}: the ops a codec is given with the game's registries, so an item or a
 * component can be written as the registry-aware codecs write them. Kiln's codecs need no registry to read a name,
 * so this hands every call to the ops it was made over.
 */
public class RegistryOps<T> implements DynamicOps<T> {
    private final DynamicOps<T> delegate;

    protected RegistryOps(DynamicOps<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> ops, HolderLookup.Provider lookup) {
        return new RegistryOps<>(ops);
    }

    /** The same registries over other ops (JEI writes a stack's components to NBT so, D-171). */
    public <U> RegistryOps<U> withParent(DynamicOps<U> ops) {
        return new RegistryOps<>(ops);
    }

    @Override
    public T empty() {
        return delegate.empty();
    }

    @Override
    public T emptyMap() {
        return delegate.emptyMap();
    }

    @Override
    public T emptyList() {
        return delegate.emptyList();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> out, T input) {
        return delegate.convertTo(out, input);
    }

    @Override
    public DataResult<Number> getNumberValue(T input) {
        return delegate.getNumberValue(input);
    }

    @Override
    public T createNumeric(Number value) {
        return delegate.createNumeric(value);
    }

    @Override
    public T createByte(byte value) {
        return delegate.createByte(value);
    }

    @Override
    public T createShort(short value) {
        return delegate.createShort(value);
    }

    @Override
    public T createInt(int value) {
        return delegate.createInt(value);
    }

    @Override
    public T createLong(long value) {
        return delegate.createLong(value);
    }

    @Override
    public T createFloat(float value) {
        return delegate.createFloat(value);
    }

    @Override
    public T createDouble(double value) {
        return delegate.createDouble(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(T input) {
        return delegate.getBooleanValue(input);
    }

    @Override
    public T createBoolean(boolean value) {
        return delegate.createBoolean(value);
    }

    @Override
    public DataResult<String> getStringValue(T input) {
        return delegate.getStringValue(input);
    }

    @Override
    public T createString(String value) {
        return delegate.createString(value);
    }

    @Override
    public DataResult<T> mergeToList(T list, T value) {
        return delegate.mergeToList(list, value);
    }

    @Override
    public DataResult<T> mergeToMap(T map, T key, T value) {
        return delegate.mergeToMap(map, key, value);
    }

    @Override
    public DataResult<Stream<Pair<T, T>>> getMapValues(T input) {
        return delegate.getMapValues(input);
    }

    @Override
    public T createMap(Stream<Pair<T, T>> map) {
        return delegate.createMap(map);
    }

    @Override
    public DataResult<Stream<T>> getStream(T input) {
        return delegate.getStream(input);
    }

    @Override
    public T createList(Stream<T> input) {
        return delegate.createList(input);
    }

    @Override
    public T remove(T input, String key) {
        return delegate.remove(input, key);
    }
}
