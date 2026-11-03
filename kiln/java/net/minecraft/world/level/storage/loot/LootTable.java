package net.minecraft.world.level.storage.loot;

import java.util.ArrayList;
import java.util.List;

/** Kiln's stand-in for {@code LootTable}: its pools, as a mod's code builds them. */
public class LootTable {
    private final List<LootPool> pools;

    private LootTable(List<LootPool> pools) {
        this.pools = List.copyOf(pools);
    }

    public static Builder lootTable() {
        return new Builder();
    }

    /** Kiln's own. */
    public List<LootPool> kiln$pools() {
        return pools;
    }

    public static class Builder {
        private final List<LootPool> pools = new ArrayList<>();

        public Builder withPool(LootPool.Builder pool) {
            pools.add(pool.build());
            return this;
        }

        public LootTable build() {
            return new LootTable(pools);
        }

        /** Kiln's own: the pools added so far. */
        public List<LootPool> kiln$pools() {
            return List.copyOf(pools);
        }
    }
}
