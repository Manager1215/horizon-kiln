package kiln;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * What the mods' items are, for the game (D-230): Horizon reads a content mod's items before Java starts, so the
 * components a mod's code gives its items ({@code Item.Properties.food}, {@code durability}, {@code stacksTo}, a
 * tool's or sword's {@code weapon}) are written here as the game's item components, one file a namespace:
 * {@code <kiln dir>/item-facts/<namespace>.json}, {@code {"<id>": {"max_stack_size": n, "components": {...}}}}. At the
 * next start Kiln's carry puts them into the content mod's item declarations (kiln/mixins/kiln/carry.rs), so the game
 * eats, wears out and stacks them as the mod says. A file is written only when what it says changed.
 */
public final class ItemFacts {
    private ItemFacts() {}

    /** After a mod's entrypoint ran ({@code Server.announce}): the files of the namespaces whose facts changed. */
    static void write() {
        String dir = System.getProperty("kiln.dir");
        if (dir == null) {
            return;
        }
        Map<String, JsonObject> byNamespace = new TreeMap<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getNamespace().equals("minecraft")) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            // A block's item is its block's (the import's or Kiln's block file).
            if (item instanceof BlockItem) {
                continue;
            }
            JsonObject facts;
            try {
                facts = facts(item);
            } catch (RuntimeException | LinkageError e) {
                Host.send("log", "Kiln: " + id + "'s components not told to the game: " + e);
                continue;
            }
            if (facts != null) {
                byNamespace.computeIfAbsent(id.getNamespace(), ns -> new JsonObject()).add(id.toString(), facts);
            }
        }
        Path out = Path.of(dir, "item-facts");
        for (Map.Entry<String, JsonObject> e : byNamespace.entrySet()) {
            String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(e.getValue());
            Path file = out.resolve(e.getKey() + ".json");
            try {
                if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(text)) {
                    continue;
                }
                Files.createDirectories(out);
                Path temp = out.resolve(e.getKey() + ".json.tmp");
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException | RuntimeException x) {
                Host.send("log", "Kiln: " + file + " not written: " + x);
            }
        }
    }

    /** One item's facts, or null when its code gives it nothing the game's plain item lacks. */
    static JsonObject facts(Item item) {
        JsonObject components = new JsonObject();
        net.minecraft.world.food.FoodProperties food = item.kiln$component(DataComponents.FOOD);
        if (food != null) {
            JsonObject f = new JsonObject();
            f.addProperty("nutrition", food.nutrition());
            f.addProperty("saturation", food.saturation());
            if (food.canAlwaysEat()) {
                f.addProperty("can_always_eat", true);
            }
            components.add("minecraft:food", f);
        }
        net.minecraft.world.item.component.Consumable consumable = item.kiln$component(DataComponents.CONSUMABLE);
        if (consumable != null) {
            components.add("minecraft:consumable", consumable(consumable));
        }
        int maxDamage = item.kiln$maxDamage();
        if (maxDamage > 0) {
            components.addProperty("minecraft:max_damage", maxDamage);
            components.addProperty("minecraft:damage", 0);
        }
        net.minecraft.world.item.component.Weapon weapon = item.kiln$component(DataComponents.WEAPON);
        if (weapon != null) {
            JsonObject w = new JsonObject();
            w.addProperty("item_damage_per_attack", weapon.itemDamagePerAttack());
            if (weapon.disableBlockingForSeconds() > 0) {
                w.addProperty("disable_blocking_for_seconds", weapon.disableBlockingForSeconds());
            }
            components.add("minecraft:weapon", w);
        }
        int stack = item.getDefaultMaxStackSize();
        if (components.isEmpty() && stack == 64) {
            return null;
        }
        JsonObject facts = new JsonObject();
        if (stack != 64) {
            facts.addProperty("max_stack_size", stack);
        }
        facts.add("components", components);
        return facts;
    }

    /** The game's {@code consumable} component, as its codec writes it. */
    static JsonObject consumable(net.minecraft.world.item.component.Consumable c) {
        JsonObject o = new JsonObject();
        o.addProperty("consume_seconds", c.consumeSeconds());
        if (c.animation() != null) {
            o.addProperty("animation", c.animation().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (c.sound() != null && c.sound().unwrapKey().isPresent()) {
            o.addProperty("sound", c.sound().getRegisteredName());
        }
        o.addProperty("has_consume_particles", c.hasConsumeParticles());
        JsonArray effects = new JsonArray();
        if (c.onConsumeEffects() != null) {
            for (net.minecraft.world.item.consume_effects.ConsumeEffect effect : c.onConsumeEffects()) {
                JsonObject e = consumeEffect(effect);
                if (e != null) {
                    effects.add(e);
                }
            }
        }
        if (!effects.isEmpty()) {
            o.add("on_consume_effects", effects);
        }
        return o;
    }

    /** A consume effect the game knows, or null for one Kiln can't name (left out, the rest kept). */
    static JsonObject consumeEffect(net.minecraft.world.item.consume_effects.ConsumeEffect effect) {
        JsonObject e = new JsonObject();
        if (effect instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect apply) {
            e.addProperty("type", "minecraft:apply_effects");
            JsonArray list = new JsonArray();
            for (net.minecraft.world.effect.MobEffectInstance i : apply.effects()) {
                if (i == null || i.getEffect() == null || i.getEffect().unwrapKey().isEmpty()) {
                    continue;
                }
                JsonObject one = new JsonObject();
                one.addProperty("id", i.getEffect().getRegisteredName());
                one.addProperty("duration", i.getDuration());
                one.addProperty("amplifier", i.getAmplifier());
                list.add(one);
            }
            e.add("effects", list);
            e.addProperty("probability", apply.probability());
            return e;
        }
        if (effect instanceof net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect) {
            e.addProperty("type", "minecraft:clear_all_effects");
            return e;
        }
        return null;
    }
}
