package net.minecraft.world.level.storage.loot.entries;

/** Kiln's stand-in for {@code LootPoolEntryContainer}: one entry of a loot pool. */
public abstract class LootPoolEntryContainer {
    public abstract static class Builder<T extends Builder<T>> {
        protected abstract T getThis();

        public T unwrap() {
            return getThis();
        }

        public abstract LootPoolEntryContainer build();
    }
}
