package net.minecraft.world.level.storage.loot;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKey;

/** Kiln's stand-in for {@code LootContext}: what a loot table, or a number a component rolls, is evaluated in. */
public class LootContext {
    private final LootParams params;

    private LootContext(LootParams params) {
        this.params = params;
    }

    public ServerLevel getLevel() {
        return params.getLevel();
    }

    /** Kiln's own: the value the context was made with for {@code key}, or null. */
    public <T> T kiln$parameter(ContextKey<T> key) {
        return params.kiln$parameter(key);
    }

    public static class Builder {
        private final LootParams params;

        public Builder(LootParams params) {
            this.params = params;
        }

        public ServerLevel getLevel() {
            return params.getLevel();
        }

        public Builder withOptionalRandomSeed(long seed) {
            return this;
        }

        public LootContext create(Optional<Identifier> sequence) {
            return new LootContext(params);
        }
    }
}
