package net.minecraft.world.level.block.state;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.world.level.block.state.properties.Property;

/** Kiln's stand-in for {@code StateDefinition}: the properties an owner (a block) declares, and its states. */
public class StateDefinition<O, S extends StateHolder<O, S>> {
    private final O owner;
    private final Map<String, Property<?>> properties;
    private final Function<SortedMap<String, String>, S> factory;

    private StateDefinition(O owner, Map<String, Property<?>> properties, Function<SortedMap<String, String>, S> factory) {
        this.owner = owner;
        this.properties = properties;
        this.factory = factory;
    }

    /** The first state: each property at its first value, as vanilla's. */
    public S any() {
        SortedMap<String, String> first = new TreeMap<>();
        properties.values().forEach(p -> first.put(p.getName(), firstName(p)));
        return factory.apply(first);
    }

    public ImmutableList<S> getPossibleStates() {
        List<SortedMap<String, String>> states = new ArrayList<>(List.of(new TreeMap<>()));
        for (Property<?> property : properties.values()) {
            List<SortedMap<String, String>> next = new ArrayList<>();
            for (SortedMap<String, String> state : states) {
                for (String value : names(property)) {
                    SortedMap<String, String> more = new TreeMap<>(state);
                    more.put(property.getName(), value);
                    next.add(more);
                }
            }
            states = next;
        }
        return states.stream().map(factory).collect(ImmutableList.toImmutableList());
    }

    public Property<?> getProperty(String name) {
        return properties.get(name);
    }

    public O getOwner() {
        return owner;
    }

    /** Kiln's own: the declared properties, in order. */
    public List<Property<?>> kiln$properties() {
        return List.copyOf(properties.values());
    }

    private static <T extends Comparable<T>> String firstName(Property<T> property) {
        return property.getName(property.getPossibleValues().get(0));
    }

    private static <T extends Comparable<T>> List<String> names(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }

    public static class Builder<O, S extends StateHolder<O, S>> {
        private final O owner;
        private final Map<String, Property<?>> properties = new LinkedHashMap<>();

        public Builder(O owner) {
            this.owner = owner;
        }

        public Builder<O, S> add(Property<?>... properties) {
            for (Property<?> property : properties) {
                if (this.properties.containsKey(property.getName())) {
                    throw new IllegalArgumentException(owner + " has property: " + property.getName());
                }
                this.properties.put(property.getName(), property);
            }
            return this;
        }

        /** Kiln's own: the definition, its states made by {@code factory} from their values. */
        public StateDefinition<O, S> kiln$create(Function<SortedMap<String, String>, S> factory) {
            return new StateDefinition<>(owner, new LinkedHashMap<>(properties), factory);
        }
    }
}
