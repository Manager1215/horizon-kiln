package net.minecraft.world.level.storage.loot.entries;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/** Kiln's stand-in for {@code LootItem}: an entry that gives an item. */
public class LootItem extends UniformContainerBase {
    private final Item item;

    private LootItem(Item item, int weight, int quality) {
        super(weight, quality);
        this.item = item;
    }

    public static UniformContainerBase.Builder<?> lootTableItem(ItemLike item) {
        return new ItemBuilder(item.asItem());
    }

    /** Kiln's own. */
    public Item kiln$item() {
        return item;
    }

    private static final class ItemBuilder extends UniformContainerBase.Builder<ItemBuilder> {
        private final Item item;

        ItemBuilder(Item item) {
            this.item = item;
        }

        @Override
        protected ItemBuilder getThis() {
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new LootItem(item, weight, quality);
        }
    }
}
