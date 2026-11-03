package net.minecraft.util;

import java.util.random.RandomGenerator;

/** Kiln's stand-in for {@code RandomSource}: the game's random numbers, over Java's. */
public interface RandomSource {
    static RandomSource create() {
        return new Of(RandomGenerator.of("L64X128MixRandom"));
    }

    int nextInt();

    int nextInt(int bound);

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    default int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return origin + nextInt(bound - origin);
    }

    /** Kiln's own: a source over one of Java's generators. */
    record Of(RandomGenerator generator) implements RandomSource {
        @Override
        public int nextInt() {
            return generator.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return generator.nextInt(bound);
        }

        @Override
        public boolean nextBoolean() {
            return generator.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return generator.nextFloat();
        }

        @Override
        public double nextDouble() {
            return generator.nextDouble();
        }
    }
}
