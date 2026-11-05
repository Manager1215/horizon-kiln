package net.minecraft.world.level.storage.loot;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextKeySet;

/** Kiln's stand-in for {@code LootParams}: the level and the values a loot context is made with. */
public class LootParams {
    private final ServerLevel level;
    private final Map<ContextKey<?>, Object> parameters;

    private LootParams(ServerLevel level, Map<ContextKey<?>, Object> parameters) {
        this.level = level;
        this.parameters = parameters;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** Kiln's own: the value given for {@code key}, or null. */
    @SuppressWarnings("unchecked")
    public <T> T kiln$parameter(ContextKey<T> key) {
        return (T) parameters.get(key);
    }

    public static class Builder {
        private final ServerLevel level;
        private final Map<ContextKey<?>, Object> parameters = new HashMap<>();

        public Builder(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public <T> Builder withParameter(ContextKey<T> key, T value) {
            parameters.put(key, value);
            return this;
        }

        public <T> Builder withOptionalParameter(ContextKey<T> key, T value) {
            if (value == null) {
                parameters.remove(key);
            } else {
                parameters.put(key, value);
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T getParameter(ContextKey<T> key) {
            T value = (T) parameters.get(key);
            if (value == null) {
                throw new java.util.NoSuchElementException(String.valueOf(key));
            }
            return value;
        }

        @SuppressWarnings("unchecked")
        public <T> T getOptionalParameter(ContextKey<T> key) {
            return (T) parameters.get(key);
        }

        public LootParams create(ContextKeySet set) {
            return new LootParams(level, new HashMap<>(parameters));
        }
    }
}
