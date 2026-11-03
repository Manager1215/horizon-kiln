package net.minecraft.world.level.block.state.properties;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** Kiln's stand-in for {@code IntegerProperty}: whole numbers from {@code min} to {@code max}. */
public final class IntegerProperty extends Property<Integer> {
    public final int min;
    public final int max;
    private final List<Integer> values;

    private IntegerProperty(String name, int min, int max) {
        super(name, Integer.class);
        this.min = min;
        this.max = max;
        this.values = IntStream.rangeClosed(min, max).boxed().toList();
    }

    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }

    @Override
    public List<Integer> getPossibleValues() {
        return values;
    }

    @Override
    public String getName(Integer value) {
        return value.toString();
    }

    @Override
    public Optional<Integer> getValue(String name) {
        try {
            int n = Integer.parseInt(name);
            return n >= min && n <= max ? Optional.of(n) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
