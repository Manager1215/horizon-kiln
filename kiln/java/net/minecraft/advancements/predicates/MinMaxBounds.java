package net.minecraft.advancements.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * Kiln's stand-in for {@code MinMaxBounds}: a range of numbers with an optional low and high end, as the game's data
 * writes one, a number ({@code 3}) or an object ({@code {"min": 1, "max": 8}}). {@link Ints} is what the game's
 * recipes take: how many maps a map is cloned onto, the book generations that can be copied (D-197).
 */
public interface MinMaxBounds<T extends Number & Comparable<T>> {
    Bounds<T> bounds();

    default Optional<T> min() {
        return bounds().min();
    }

    default Optional<T> max() {
        return bounds().max();
    }

    default boolean isAny() {
        return bounds().min().isEmpty() && bounds().max().isEmpty();
    }

    record Bounds<T extends Number & Comparable<T>>(Optional<T> min, Optional<T> max) {
        public static <N extends Number & Comparable<N>> Bounds<N> any() {
            return new Bounds<N>(Optional.<N>empty(), Optional.<N>empty());
        }

        public static <N extends Number & Comparable<N>> Bounds<N> exactly(N value) {
            return new Bounds<N>(Optional.of(value), Optional.of(value));
        }

        public static <N extends Number & Comparable<N>> Bounds<N> between(N min, N max) {
            return new Bounds<N>(Optional.of(min), Optional.of(max));
        }

        public static <N extends Number & Comparable<N>> Bounds<N> atLeast(N min) {
            return new Bounds<N>(Optional.of(min), Optional.<N>empty());
        }

        public static <N extends Number & Comparable<N>> Bounds<N> atMost(N max) {
            return new Bounds<N>(Optional.<N>empty(), Optional.of(max));
        }

        /** A number is both ends; an object names either. */
        static <N extends Number & Comparable<N>> Codec<Bounds<N>> codec(Codec<N> number) {
            Codec<Bounds<N>> object = RecordCodecBuilder.create(i -> i.group(
                    number.optionalFieldOf("min").forGetter(Bounds<N>::min),
                    number.optionalFieldOf("max").forGetter(Bounds<N>::max)).apply(i, (min, max) -> new Bounds<N>(min, max)));
            return Codec.either(number, object).xmap(
                    e -> e.map(n -> Bounds.<N>exactly(n), b -> b),
                    b -> b.min().isPresent() && b.min().equals(b.max())
                            ? com.mojang.datafixers.util.Either.left(b.min().get())
                            : com.mojang.datafixers.util.Either.right(b));
        }

        boolean contains(T value) {
            return min.map(m -> m.compareTo(value) <= 0).orElse(true) && max.map(m -> m.compareTo(value) >= 0).orElse(true);
        }

        <R extends Number & Comparable<R>> Bounds<R> map(java.util.function.Function<T, R> f) {
            return new Bounds<R>(min.map(f), max.map(f));
        }
    }

    record Ints(Bounds<Integer> bounds, Bounds<Long> boundsSqr) implements MinMaxBounds<Integer> {
        public static final Ints ANY = new Ints(Bounds.<Integer>any());
        public static final Codec<Ints> CODEC = Bounds.codec(Codec.INT).xmap(b -> new Ints(b), Ints::bounds);

        private Ints(Bounds<Integer> bounds) {
            this(bounds, bounds.map(i -> (long) i * i));
        }

        public static Ints exactly(int value) {
            return new Ints(Bounds.<Integer>exactly(value));
        }

        public static Ints between(int min, int max) {
            return new Ints(Bounds.<Integer>between(min, max));
        }

        public static Ints atLeast(int min) {
            return new Ints(Bounds.<Integer>atLeast(min));
        }

        public static Ints atMost(int max) {
            return new Ints(Bounds.<Integer>atMost(max));
        }

        public boolean matches(int value) {
            return bounds.contains(value);
        }

        public boolean matchesSqr(long value) {
            return boundsSqr.contains(value);
        }
    }

    record Doubles(Bounds<Double> bounds, Bounds<Double> boundsSqr) implements MinMaxBounds<Double> {
        public static final Doubles ANY = new Doubles(Bounds.<Double>any());
        public static final Codec<Doubles> CODEC = Bounds.codec(Codec.DOUBLE).xmap(b -> new Doubles(b), Doubles::bounds);

        private Doubles(Bounds<Double> bounds) {
            this(bounds, bounds.map(d -> d * d));
        }

        public static Doubles exactly(double value) {
            return new Doubles(Bounds.<Double>exactly(value));
        }

        public static Doubles between(double min, double max) {
            return new Doubles(Bounds.<Double>between(min, max));
        }

        public static Doubles atLeast(double min) {
            return new Doubles(Bounds.<Double>atLeast(min));
        }

        public static Doubles atMost(double max) {
            return new Doubles(Bounds.<Double>atMost(max));
        }

        public boolean matches(double value) {
            return bounds.contains(value);
        }

        public boolean matchesSqr(double value) {
            return boundsSqr.contains(value);
        }
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public static net.minecraft.advancements.predicates.MinMaxBounds.Doubles fromReader(com.mojang.brigadier.StringReader p0) { kiln.Missing.called("net.minecraft.advancements.predicates.MinMaxBounds.Doubles.fromReader"); return new net.minecraft.advancements.predicates.MinMaxBounds.Doubles((net.minecraft.advancements.predicates.MinMaxBounds.Bounds) null, (net.minecraft.advancements.predicates.MinMaxBounds.Bounds) null); }
        // End of what tools/kiln-stubs.py generated.
    }
}
