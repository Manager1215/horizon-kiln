package net.minecraft.server.packs.resources;

/**
 * Kiln's stand-in for {@code PreparableReloadListener}: what reloads with the resources. Kiln keeps a mod's listeners
 * and doesn't run them (D-152); the shared state is here for the mods that make keys of it as they load (D-170: Mezz
 * Config GUI's sprite manager).
 */
public interface PreparableReloadListener {
    java.util.concurrent.CompletableFuture<Void> reload(SharedState state, java.util.concurrent.Executor prepare,
            PreparationBarrier barrier, java.util.concurrent.Executor apply);

    /** Before the reloads: what a listener puts into the state for the others. */
    default void prepareSharedState(SharedState state) {}

    /** A key into a reload's shared state. */
    final class StateKey<T> {
        public StateKey() {}
    }

    /** What a reload's listeners share: the resources, and values by key. */
    class SharedState {
        private final ResourceManager resources;
        private final java.util.Map<StateKey<?>, Object> values = new java.util.concurrent.ConcurrentHashMap<>();

        public SharedState(ResourceManager resources) {
            this.resources = resources;
        }

        protected SharedState() {
            this(null);
        }

        public ResourceManager resourceManager() {
            return resources;
        }

        public <T> void set(StateKey<T> key, T value) {
            values.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(StateKey<T> key) {
            T value = (T) values.get(key);
            if (value == null) {
                throw new IllegalStateException("no value for " + key);
            }
            return value;
        }
    }

    interface PreparationBarrier {
        <T> java.util.concurrent.CompletableFuture<T> wait(T value);
    }
}
