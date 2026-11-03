package net.minecraft.world.level.storage.loot.entries;

/** Kiln's stand-in for {@code UniformContainerBase}: an entry picked by its weight. */
public abstract class UniformContainerBase extends LootPoolEntryContainer {
    protected final int weight;
    protected final int quality;

    protected UniformContainerBase(int weight, int quality) {
        this.weight = weight;
        this.quality = quality;
    }

    /** Kiln's own. */
    public int kiln$weight() {
        return weight;
    }

    public abstract static class Builder<T extends Builder<T>> extends LootPoolEntryContainer.Builder<T> {
        protected int weight = 1;
        protected int quality;

        public T setWeight(int weight) {
            this.weight = weight;
            return getThis();
        }

        public T setQuality(int quality) {
            this.quality = quality;
            return getThis();
        }
    }
}
