package net.minecraft.world.item;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Kiln's stand-in for {@code SpawnEggItem}: the spawn egg of an entity type, as the game names it ({@code
 * minecraft:cow_spawn_egg} for {@code minecraft:cow}). The game's eggs are Kiln's game items; a recipe viewer's plugin
 * finds a mob's egg by its type (Just Enough Breeding, D-173).
 */
public class SpawnEggItem extends Item {
    public SpawnEggItem(Item.Properties properties) {
        super(properties);
    }

    /** The spawn egg of {@code type}, if the game or a mod has one by that name. */
    public static Optional<Holder<Item>> byId(EntityType<?> type) {
        Identifier id = type == null ? null : EntityType.getKey(type);
        if (id == null) {
            return Optional.empty();
        }
        Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
        return BuiltInRegistries.ITEM.get(egg).map(h -> (Holder<Item>) h);
    }

    /** The type it makes: the entity type its id names, a pig when there is none (as the registry's default). */
    public EntityType<?> getType(ItemStack stack) {
        String path = kiln$id().getPath();
        Identifier type = Identifier.fromNamespaceAndPath(kiln$id().getNamespace(),
                path.endsWith("_spawn_egg") ? path.substring(0, path.length() - "_spawn_egg".length()) : path);
        return BuiltInRegistries.ENTITY_TYPE.getValue(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        java.util.List<SpawnEggItem> eggs = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem egg) {
                eggs.add(egg);
            }
        }
        return eggs;
    }
}
