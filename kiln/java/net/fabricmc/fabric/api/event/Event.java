package net.fabricmc.fabric.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code Event}: listeners, and an invoker that calls them all. Phases are
 * accepted and ignored: listeners run in the order they registered.
 */
public abstract class Event<T> {
    public static final Identifier DEFAULT_PHASE = Identifier.fromNamespaceAndPath("fabric", "default");

    protected volatile T invoker;

    public final T invoker() {
        return invoker;
    }

    public abstract void register(T listener);

    public void register(Identifier phase, T listener) {
        register(listener);
    }

    public void addPhaseOrdering(Identifier first, Identifier second) {}

    /** Kiln's own: an event whose invoker {@code factory} makes from its (live) list of listeners. */
    public static <T> Event<T> kiln$of(Function<List<T>, T> factory) {
        return new Event<>() {
            private final List<T> listeners = new CopyOnWriteArrayList<>();

            {
                this.invoker = factory.apply(listeners);
            }

            @Override
            public void register(T listener) {
                listeners.add(listener);
            }
        };
    }
}
