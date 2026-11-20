package net.minecraft.util;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Kiln's stand-in for {@code ByIdMap}: a value by its id, as the game's network codecs find an enum's constant by its
 * number ({@code ByteBufCodecs.idMapper}, REI's display sync's kind, D-234).
 */
public class ByIdMap {
    public ByIdMap() {}

    /**
     * The values by their ids, which run from 0 without a gap ({@code IllegalArgumentException} otherwise): an id out
     * of them gives the first value (ZERO), the one it wraps to (WRAP) or the nearest (CLAMP).
     */
    public static <T> IntFunction<T> continuous(ToIntFunction<T> toId, T[] values, OutOfBoundsStrategy strategy) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty value list");
        }
        @SuppressWarnings("unchecked")
        T[] byId = (T[]) new Object[values.length];
        for (T value : values) {
            int id = toId.applyAsInt(value);
            if (id < 0 || id >= values.length) {
                throw new IllegalArgumentException("Values are not continuous, found index " + id + " for value: " + value);
            }
            if (byId[id] != null) {
                throw new IllegalArgumentException("Duplicate entry on id " + id + ": current=" + byId[id] + ", added=" + value);
            }
            byId[id] = value;
        }
        return id -> {
            if (id >= 0 && id < byId.length) {
                return byId[id];
            }
            return switch (strategy) {
                case ZERO -> byId[0];
                case WRAP -> byId[Math.floorMod(id, byId.length)];
                case CLAMP -> byId[id < 0 ? 0 : byId.length - 1];
            };
        };
    }

    public static enum OutOfBoundsStrategy {
        ZERO, WRAP, CLAMP;
    }
}
