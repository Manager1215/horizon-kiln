package net.minecraft.world.level.block.state.properties;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code EnumProperty}: some or all of an enum's values, by their serialized names. */
public final class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {
    private final List<T> values;

    private EnumProperty(String name, Class<T> clazz, List<T> values) {
        super(name, clazz);
        this.values = List.copyOf(values);
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, List.of(clazz.getEnumConstants()));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz,
            Predicate<T> filter) {
        return create(name, clazz, Arrays.stream(clazz.getEnumConstants()).filter(filter).toList());
    }

    @SafeVarargs
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz,
            T... values) {
        return create(name, clazz, List.of(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz,
            List<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }

    @Override
    public List<T> getPossibleValues() {
        return values;
    }

    @Override
    public String getName(T value) {
        return value.getSerializedName();
    }

    @Override
    public Optional<T> getValue(String name) {
        return values.stream().filter(v -> v.getSerializedName().equals(name)).findFirst();
    }
}
