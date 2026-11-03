package net.minecraft.world.level.block.state.properties;

import java.util.List;
import java.util.Optional;

/** Kiln's stand-in for {@code Property}: a block state's named property and its possible values. */
public abstract class Property<T extends Comparable<T>> {
    private final String name;
    private final Class<T> clazz;

    protected Property(String name, Class<T> clazz) {
        this.name = name;
        this.clazz = clazz;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueClass() {
        return clazz;
    }

    public abstract List<T> getPossibleValues();

    /** A value as the game's data writes it ({@code true}, {@code 3}, {@code north}). */
    public abstract String getName(T value);

    public abstract Optional<T> getValue(String name);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name=" + name + ", values=" + getPossibleValues() + "}";
    }
}
