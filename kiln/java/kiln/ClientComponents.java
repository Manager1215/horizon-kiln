package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * A game item's own components that are its data and the same on both sides, read from the game's {@code items.json}
 * (D-159): what repairs it ({@code minecraft:repairable}), how it composts ({@code minecraft:compostable}), whether an
 * enchanting table takes it ({@code minecraft:enchantable}), what it adds to its holder's attributes
 * ({@code minecraft:attribute_modifiers}, D-177). A recipe viewer reads them of the items it lists. A mod's
 * item has those its properties gave it (D-177: an armour's attributes, slot, repairs and enchantability).
 */
public final class ClientComponents {
    private static final Map<String, Optional<Object>> VALUES = new ConcurrentHashMap<>();

    private ClientComponents() {}

    /** {@code item}'s component of {@code type} if it is one of those above; null otherwise or without it. */
    @SuppressWarnings("unchecked")
    public static <T> T component(Item item, DataComponentType<? extends T> type) {
        if (item != null) {
            // A mod's item's own, from its properties (a mod's armour's attributes and slot, D-177).
            T declared = item.kiln$component(type);
            if (declared != null) {
                return declared;
            }
        }
        Identifier typeId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (item == null || typeId == null || !typeId.getNamespace().equals("minecraft")) {
            return null;
        }
        String kind = typeId.getPath();
        if (!kind.equals("repairable") && !kind.equals("compostable") && !kind.equals("enchantable")
                && !kind.equals("attribute_modifiers") && !kind.equals("dye")) {
            return null;
        }
        Identifier id = item.kiln$id();
        if (id == null || !id.getNamespace().equals("minecraft")) {
            return null;
        }
        return (T) VALUES.computeIfAbsent(id + " " + kind, k -> {
            JsonElement json = GameItems.itemComponent(id.toString(), typeId.toString());
            if (kind.equals("attribute_modifiers")) {
                // As the game saves it, read by its codec (D-177): a sword's attack, a chestplate's armor.
                return json == null || type.codec() == null ? Optional.empty()
                        : (Optional<Object>) (Optional<?>) type.codec().parse(JsonOps.INSTANCE, json).result();
            }
            if (kind.equals("dye")) {
                // A dye's color (D-197): the game's crafting mixes it into leather and firework stars.
                return json != null && json.isJsonPrimitive()
                        ? Optional.ofNullable(net.minecraft.world.item.DyeColor.byName(
                                json.getAsString().replace("minecraft:", ""), null))
                        : Optional.empty();
            }
            if (!(json instanceof JsonObject o)) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(switch (kind) {
                    case "repairable" -> o.has("items") ? new Repairable(items(o.get("items"))) : null;
                    case "compostable" -> o.has("layers") ? new Compostable(layers(o.get("layers"))) : null;
                    default -> o.has("value") ? new Enchantable(o.get("value").getAsInt()) : null;
                });
            } catch (RuntimeException e) {
                System.err.println("Kiln: " + id + "'s " + typeId + ": " + e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** A {@code context_int_provider} of the game's data worked out in {@code context}; {@code fallback} if Kiln can't. */
    public static int resolveInt(Identifier provider, LootContext context, int fallback) {
        double value = ContextNumbers.eval(new JsonPrimitive(provider.toString()), "context_int_provider", context, 0);
        return Double.isNaN(value) ? fallback : (int) value;
    }

    private static ResolvableInt layers(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return new ResolvableInt.Constant(json.getAsInt());
        }
        return new ResolvableInt.Reference(ResourceKey.create(Registries.CONTEXT_INT_PROVIDER,
                Identifier.parse(json.getAsString())));
    }

    /** The items of tag {@code tag}, read when first asked (a mod's armour's repair material). */
    public static HolderSet<Item> itemTag(Identifier tag) {
        return items(new JsonPrimitive("#" + tag));
    }

    /** An item set as the data writes one: {@code "#tag"} (its entries read when first asked), an id, or a list. */
    private static HolderSet<Item> items(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsString().startsWith("#")) {
            Identifier tag = Identifier.parse(json.getAsString().substring(1));
            return new HolderSet.Named<>(TagKey.create(Registries.ITEM, tag), () -> {
                List<Holder<Item>> holders = new ArrayList<>();
                for (Item item : GameItems.tag(tag)) {
                    holders.add(item.builtInRegistryHolder());
                }
                return holders;
            });
        }
        List<Holder<Item>> holders = new ArrayList<>();
        for (JsonElement id : json.isJsonArray() ? json.getAsJsonArray().asList() : List.of(json)) {
            holders.add(Item.kiln$byId(Identifier.parse(id.getAsString()).toString()).builtInRegistryHolder());
        }
        return HolderSet.direct(holders);
    }
}
