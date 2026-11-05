package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.HashMap;
import java.util.Map;

/** Kiln's stand-in for {@code ExtraCodecs}: the game's codecs beyond DataFixerUpper's, as mods' codecs use them. */
public class ExtraCodecs {
    /** An int above 0. */
    public static final Codec<Integer> POSITIVE_INT = intAbove(0);
    /** An int of 0 or more. */
    public static final Codec<Integer> NON_NEGATIVE_INT = intAbove(-1);

    public ExtraCodecs() {}

    private static Codec<Integer> intAbove(int bound) {
        return Codec.INT.validate(value -> value > bound ? DataResult.success(value)
                : DataResult.error(() -> "Value must be above " + bound + ": " + value));
    }

    /** Ids and values a codec maps between, filled after it is made (as registries are). */
    public static class LateBoundIdMapper<I, V> {
        private final Map<I, V> byId = new HashMap<>();
        private final Map<V, I> byValue = new HashMap<>();

        public LateBoundIdMapper() {}

        public LateBoundIdMapper<I, V> put(I id, V value) {
            byId.put(id, value);
            byValue.put(value, id);
            return this;
        }

        public Codec<V> codec(Codec<I> ids) {
            return ids.comapFlatMap(id -> byId.containsKey(id) ? DataResult.success(byId.get(id))
                    : DataResult.error(() -> "unknown " + id), byValue::get);
        }
    }
}
