package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The game's enchantments in {@code BuiltInRegistries.kiln$ENCHANTMENT} (the level's {@code Registries.ENCHANTMENT}),
 * read once from the prepared resources ({@code data/<namespace>/enchantment/**.json}): name, items, levels, costs and
 * the exclusive set, and which effect components they have (D-208); what the effects do stays the game's. A mod's own
 * enchantments from its data aren't read yet.
 */
public final class GameEnchantments {
    private static boolean registered;

    private GameEnchantments() {}

    /** The game's enchantment {@code id}'s holder, or null for one it hasn't. */
    public static Holder<Enchantment> holder(String id) {
        register();
        Identifier key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.kiln$ENCHANTMENT.get(key).<Holder<Enchantment>>map(h -> h).orElse(null);
    }

    /** The enchantments of the game's tag {@code id} ({@code minecraft:curse}), in the tag's order; empty without it. */
    public static List<Holder<Enchantment>> tag(String id) {
        register();
        TagKey<Enchantment> tag = TagKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                Identifier.parse(id));
        List<Holder<Enchantment>> out = new ArrayList<>();
        BuiltInRegistries.kiln$ENCHANTMENT.get(tag).ifPresent(set -> set.forEach(out::add));
        return out;
    }

    private static volatile java.util.Set<String> curses;

    /** Whether {@code enchantment} is a curse (the game's tag {@code minecraft:curse}): its name shows red. */
    public static boolean isCurse(Holder<Enchantment> enchantment) {
        java.util.Set<String> set = curses;
        if (set == null) {
            set = new java.util.HashSet<>();
            for (Holder<Enchantment> h : tag("minecraft:curse")) {
                set.add(key(h));
            }
            curses = set;
        }
        return set.contains(key(enchantment));
    }

    /** A holder's id, or "" for one with none. */
    public static String key(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    private static volatile List<Holder<Enchantment>> tooltipOrder;

    /** The order a tooltip lists enchantments in (the game's tag {@code minecraft:tooltip_order}). */
    public static List<Holder<Enchantment>> tooltipOrder() {
        List<Holder<Enchantment>> order = tooltipOrder;
        if (order == null) {
            order = List.copyOf(tag("minecraft:tooltip_order"));
            tooltipOrder = order;
        }
        return order;
    }

    /** Registers the game's enchantments, once; none without the resources. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        String resources = System.getProperty("kiln.resources", "");
        Path data = resources.isEmpty() ? null : Path.of(resources, "data");
        if (data == null || !Files.isDirectory(data)) {
            return;
        }
        int count = 0;
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespace : namespaces.sorted().toList()) {
                Path dir = namespace.resolve("enchantment");
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(dir, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                        String path = dir.relativize(file).toString().replace('\\', '/');
                        Identifier id = Identifier.fromNamespaceAndPath(namespace.getFileName().toString(),
                                path.substring(0, path.length() - 5));
                        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            Registry.register(BuiltInRegistries.kiln$ENCHANTMENT, id,
                                    enchantment(JsonParser.parseReader(in).getAsJsonObject()));
                            count++;
                        } catch (IOException | RuntimeException e) {
                            Native.log("the game's enchantment " + id + " can't be read: " + e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Native.log("the game's enchantments can't be listed: " + e);
        }
        Native.log("Kiln has the game's " + count + " enchantments");
    }

    private static Enchantment enchantment(JsonObject o) {
        Component description = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o.get("description"))
                .result().orElse(Component.empty());
        Enchantment.EnchantmentDefinition definition = new Enchantment.EnchantmentDefinition(
                items(o.get("supported_items")),
                o.has("primary_items") ? Optional.of(items(o.get("primary_items"))) : Optional.empty(),
                o.has("weight") ? o.get("weight").getAsInt() : 1,
                o.has("max_level") ? o.get("max_level").getAsInt() : 1,
                cost(o.get("min_cost")), cost(o.get("max_cost")),
                o.has("anvil_cost") ? o.get("anvil_cost").getAsInt() : 1, List.of());
        return new Enchantment(description, definition, exclusive(o.get("exclusive_set")), effects(o.get("effects")));
    }

    /**
     * Which of the game's effect components the enchantment has, as its data's {@code effects} says (D-208): mending's
     * {@code minecraft:repair_with_xp}, so a mod that asks {@code effects().has(REPAIR_WITH_XP)} finds it. Each is a type
     * a stand-in names ({@code EnchantmentEffectComponents}); what the effect does stays the game's, asked during a
     * server call ({@code EnchantmentHelper}), so its value here holds nothing of it: a list effect's value is an empty
     * list, a flag's ({@code {}}, {@code prevent_equipment_drop}) {@code Unit}, and one of a single value (quick charge's
     * {@code crossbow_charge_time}) is left out.
     */
    @SuppressWarnings("unchecked")
    private static DataComponentMap effects(JsonElement json) {
        if (!(json instanceof JsonObject o) || o.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        // The stand-in's effect types are registered when its class is first used.
        java.util.Objects.requireNonNull(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.REPAIR_WITH_XP);
        DataComponentMap.Builder effects = DataComponentMap.builder();
        for (String key : o.keySet()) {
            Identifier id = Identifier.tryParse(key);
            net.minecraft.core.component.DataComponentType<Object> type = id == null ? null
                    : (net.minecraft.core.component.DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            // None for an effect no stand-in names; a stack's own component of the same id (saved, with a codec) isn't one.
            if (type == null || !type.isTransient()) {
                continue;
            }
            JsonElement value = o.get(key);
            if (value.isJsonArray()) {
                effects.set(type, List.of());
            } else if (value instanceof JsonObject flag && flag.isEmpty()) {
                effects.set(type, net.minecraft.util.Unit.INSTANCE);
            }
        }
        return effects.build();
    }

    private static Enchantment.Cost cost(JsonElement json) {
        if (!(json instanceof JsonObject o)) {
            return new Enchantment.Cost(1, 0);
        }
        return new Enchantment.Cost(o.has("base") ? o.get("base").getAsInt() : 1,
                o.has("per_level_above_first") ? o.get("per_level_above_first").getAsInt() : 0);
    }

    /** An item set as the data writes one: {@code "#tag"}, an id, or a list of ids. */
    private static HolderSet<Item> items(JsonElement json) {
        if (json == null) {
            return HolderSet.empty();
        }
        if (json.isJsonPrimitive() && json.getAsString().startsWith("#")) {
            TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    Identifier.parse(json.getAsString().substring(1)));
            return BuiltInRegistries.ITEM.get(tag).<HolderSet<Item>>map(s -> s).orElse(HolderSet.empty());
        }
        List<Holder<Item>> holders = new ArrayList<>();
        for (JsonElement id : json.isJsonArray() ? json.getAsJsonArray().asList() : List.of(json)) {
            holders.add(Item.kiln$byId(Identifier.parse(id.getAsString()).toString()).builtInRegistryHolder());
        }
        return HolderSet.direct(holders);
    }

    /** The enchantments it excludes: a tag of them (read when first asked), an id, or a list. */
    private static HolderSet<Enchantment> exclusive(JsonElement json) {
        if (json == null) {
            return HolderSet.empty();
        }
        if (json.isJsonPrimitive() && json.getAsString().startsWith("#")) {
            TagKey<Enchantment> tag = TagKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                    Identifier.parse(json.getAsString().substring(1)));
            return BuiltInRegistries.kiln$ENCHANTMENT.get(tag).<HolderSet<Enchantment>>map(s -> s)
                    .orElse(HolderSet.empty());
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement id : json.isJsonArray() ? json.getAsJsonArray().asList() : List.of(json)) {
            ids.add(id.getAsString());
        }
        return new HolderSet.ListBacked<>() {
            @Override
            protected List<Holder<Enchantment>> contents() {
                List<Holder<Enchantment>> found = new ArrayList<>();
                for (String id : ids) {
                    Holder<Enchantment> holder = holder(id);
                    if (holder != null) {
                        found.add(holder);
                    }
                }
                return found;
            }

            @Override
            public Optional<TagKey<Enchantment>> unwrapKey() {
                return Optional.empty();
            }

            @Override
            public boolean isBound() {
                return true;
            }

            @Override
            public com.mojang.datafixers.util.Either<TagKey<Enchantment>, List<Holder<Enchantment>>> unwrap() {
                return com.mojang.datafixers.util.Either.right(contents());
            }
        };
    }
}
