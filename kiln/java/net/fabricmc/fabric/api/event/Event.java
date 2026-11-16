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

    /** Kiln's own: whether anything listens, so the game asks Java only about events a mod hears. */
    public boolean kiln$heard() {
        return true;
    }

    /** Kiln's own: how many listen (1 when Kiln doesn't count them). */
    public int kiln$listeners() {
        return kiln$heard() ? 1 : 0;
    }

    /** Kiln's own: an event whose invoker {@code factory} makes from its (live) list of listeners. */
    public static <T> Event<T> kiln$of(Function<List<T>, T> factory) {
        return new Event<>() {
            /** Every listener, in the order they registered. */
            private final List<T> all = new CopyOnWriteArrayList<>();
            /** The invoker's: those of the mods that are on now (kiln.SoftSwitch, D-113). */
            private final List<T> listeners = new CopyOnWriteArrayList<>();

            {
                this.invoker = factory.apply(listeners);
                kiln.SoftSwitch.listeners(all, listeners);
            }

            @Override
            public void register(T listener) {
                synchronized (listeners) {
                    all.add(listener);
                    if (!kiln.SoftSwitch.offFor(listener)) {
                        listeners.add(listener);
                    }
                }
            }

            @Override
            public boolean kiln$heard() {
                return !listeners.isEmpty();
            }

            @Override
            public int kiln$listeners() {
                return listeners.size();
            }
        };
    }
}
