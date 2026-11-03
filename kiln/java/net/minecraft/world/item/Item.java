package net.minecraft.world.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code Item}. A mod's item is the mod's own subclass: Kiln calls its {@code use} when a
 * player uses Horizon's item of the same id (Horizon's import of the mod's data has it).
 */
public class Item implements ItemLike {
    private final ResourceKey<Item> id;
    private final int maxStackSize;
    private final Rarity rarity;

    public Item(Properties properties) {
        if (properties.id == null) {
            throw new NullPointerException("Item id not set");
        }
        this.id = properties.id;
        this.maxStackSize = properties.maxStackSize;
        this.rarity = properties.rarity;
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public Item asItem() {
        return this;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public int getDefaultMaxStackSize() {
        return maxStackSize;
    }

    public final String getDescriptionId() {
        return "item." + id.identifier().getNamespace() + "." + id.identifier().getPath().replace('/', '.');
    }

    /** Kiln's own: the id it was made with. */
    public Identifier kiln$id() {
        return id.identifier();
    }

    /**
     * Kiln's own: the item called {@code id}: a mod's when one registered it, else the game's, registered as Kiln
     * meets it (vanilla's registry has them all).
     */
    public static synchronized Item kiln$byId(String id) {
        Identifier name = Identifier.parse(id);
        if (BuiltInRegistries.ITEM.containsKey(name)) {
            return BuiltInRegistries.ITEM.getValue(name);
        }
        return net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, name,
                new Item(new Properties().setId(ResourceKey.create(Registries.ITEM, name))));
    }

    @Override
    public String toString() {
        return id.identifier().toString();
    }

    public static class Properties {
        private ResourceKey<Item> id;
        private int maxStackSize = 64;
        private Rarity rarity = Rarity.COMMON;

        public Properties setId(ResourceKey<Item> id) {
            this.id = id;
            return this;
        }

        public Properties stacksTo(int max) {
            maxStackSize = max;
            return this;
        }

        public Properties durability(int durability) {
            maxStackSize = 1;
            return this;
        }

        public Properties rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Properties fireResistant() {
            return this;
        }

        public Properties useBlockDescriptionPrefix() {
            return this;
        }
    }
}
