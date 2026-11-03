package net.minecraft.world.level.storage.loot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Kiln's stand-in for {@code LootPool}: how many rolls, and the entries each roll picks from. */
public class LootPool {
    private final ContextIntProvider rolls;
    private final List<LootPoolEntryContainer> entries;

    private LootPool(ContextIntProvider rolls, List<LootPoolEntryContainer> entries) {
        this.rolls = rolls;
        this.entries = List.copyOf(entries);
    }

    public static Builder lootPool() {
        return new Builder();
    }

    /** Kiln's own. */
    public ContextIntProvider kiln$rolls() {
        return rolls;
    }

    /** Kiln's own. */
    public List<LootPoolEntryContainer> kiln$entries() {
        return entries;
    }

    public static class Builder {
        private Holder<ContextIntProvider> rolls = ContextIntProviders.exactly(1);
        private final List<LootPoolEntryContainer> entries = new ArrayList<>();

        public Builder setRolls(Holder<ContextIntProvider> rolls) {
            this.rolls = rolls;
            return this;
        }

        public Builder add(LootPoolEntryContainer.Builder<?> entry) {
            return add(entry.build());
        }

        /** Fabric's {@code FabricLootPoolBuilder} adds this one to vanilla's builder. */
        public Builder add(LootPoolEntryContainer entry) {
            entries.add(entry);
            return this;
        }

        public Builder unwrap() {
            return this;
        }

        public LootPool build() {
            return new LootPool(rolls.value(), entries);
        }
    }
}
