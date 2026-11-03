package net.minecraft.world.level.block.state;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Kiln's stand-in for {@code StateHolder}: an owner (a block) and a value for each of its properties. Kiln keeps every
 * value as the game's data writes it, the owner's declared properties and the ones only the game knows (a mod block's
 * template's) alike, so a state goes to the game and back unchanged.
 */
public abstract class StateHolder<O, S> {
    protected final O owner;
    private final SortedMap<String, String> values;

    protected StateHolder(O owner, SortedMap<String, String> values) {
        this.owner = owner;
        this.values = values;
    }

    /** Kiln's own: this state's owner with {@code values} instead. */
    protected abstract S kiln$with(SortedMap<String, String> values);

    /** Kiln's own: the properties the owner declares. */
    protected abstract List<Property<?>> kiln$declared();

    /** Kiln's own: every value, by property name. */
    public SortedMap<String, String> kiln$values() {
        return values;
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        return getOptionalValue(property).orElseThrow(
                () -> new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + owner));
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        String value = values.get(property.getName());
        return value == null ? Optional.empty() : property.getValue(value);
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        if (!values.containsKey(property.getName())) {
            throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + owner);
        }
        return trySetValue(property, value);
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        if (!values.containsKey(property.getName())) {
            return kiln$with(values);
        }
        SortedMap<String, String> changed = new TreeMap<>(values);
        changed.put(property.getName(), property.getName(value));
        return kiln$with(changed);
    }

    /** The property's next value, back to the first after the last. */
    public <T extends Comparable<T>> S cycle(Property<T> property) {
        List<T> possible = property.getPossibleValues();
        int i = possible.indexOf(getValue(property));
        return setValue(property, possible.get((i + 1) % possible.size()));
    }

    public boolean hasProperty(Property<?> property) {
        return values.containsKey(property.getName());
    }

    public Collection<Property<?>> getProperties() {
        return kiln$declared();
    }
}
