package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The lines a stack's components add to its tooltip, as the game's {@code ItemStack.addDetailsToTooltip} writes them
 * (D-164): a potion's effects and their durations, an armor trim's pattern and material, stored and applied
 * enchantments, a dyed colour, the lore, "Unbreakable", and with advanced tooltips the durability and the item's id.
 * A component the stack's {@code tooltip_display} hides is left out. Horizon writes these lines itself in the game's
 * own tooltip; these are for a mod that draws a tooltip of its own (a recipe viewer's).
 */
public final class ItemTooltips {
    /** The items the game names by their potion ({@code PotionItem}, {@code TippedArrowItem}). */
    public static final Set<String> POTION_ITEMS = Set.of("minecraft:potion", "minecraft:splash_potion",
            "minecraft:lingering_potion", "minecraft:tipped_arrow");
    private static final Map<String, Optional<Rarity>> GAME_RARITY = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Component>> TRIM_TEXTS = new ConcurrentHashMap<>();

    private ItemTooltips() {}

    /** The game's lines the bridge's tooltip call under way on this thread put in its list (D-218), or null. */
    private static final ThreadLocal<List<Component>> GAME_LINES = new ThreadLocal<>();

    /** Kiln's own: the game's lines of the bridge's tooltip call under way ({@code ItemStack.getTooltipLines}). */
    public static void gameLines(List<Component> lines) {
        GAME_LINES.set(lines);
    }

    /** The game's lines the last bridge tooltip call on this thread put in its list, once; null when none. */
    static List<Component> takeGameLines() {
        List<Component> lines = GAME_LINES.get();
        GAME_LINES.remove();
        return lines;
    }

    /** A {@code minecraft:rarity} as saved ({@code "rare"}); null for none or one the game hasn't. */
    public static Rarity rarity(Object saved) {
        if (!(saved instanceof String name)) {
            return null;
        }
        for (Rarity r : Rarity.values()) {
            if (r.getSerializedName().equals(name)) {
                return r;
            }
        }
        return null;
    }

    /** A game item's own rarity, from {@code items.json}; null for a mod's item or without the resources. */
    public static Rarity gameRarity(Item item) {
        String id = item.kiln$id().toString();
        if (!id.startsWith("minecraft:")) {
            return null;
        }
        return GAME_RARITY.computeIfAbsent(id, k -> {
            JsonElement json = GameItems.itemComponent(k, "minecraft:rarity");
            return Optional.ofNullable(json != null && json.isJsonPrimitive() ? rarity(json.getAsString()) : null);
        }).orElse(null);
    }

    /** Whether the stack's {@code tooltip_display} hides its whole tooltip. */
    public static boolean hidden(ItemStack stack) {
        return stack.kiln$saved("minecraft:tooltip_display") instanceof Map<?, ?> display
                && (Boolean.TRUE.equals(display.get("hide_tooltip"))
                        || display.get("hide_tooltip") instanceof Number n && n.intValue() != 0);
    }

    /** Whether the stack's {@code tooltip_display} hides component {@code id}'s lines. */
    static boolean hides(ItemStack stack, String id) {
        return stack.kiln$saved("minecraft:tooltip_display") instanceof Map<?, ?> display
                && display.get("hidden_components") instanceof List<?> hidden && hidden.contains(id);
    }

    /** The lines the stack's components add, past its item's own, in the game's order. */
    public static void details(ItemStack stack, Item.TooltipContext context, TooltipFlag flag,
            Consumer<Component> lines) {
        details(stack, context, null, flag, lines);
    }

    /**
     * The lines, with {@code player} the one who looks (a weapon's attack then shows as the player's total, D-177).
     */
    public static void details(ItemStack stack, Item.TooltipContext context, net.minecraft.world.entity.player.Player player,
            TooltipFlag flag, Consumer<Component> lines) {
        Set<String> own = stack.kiln$savedIds();
        components(stack, "before", context, flag, lines);
        if (own.contains("minecraft:potion_contents") && !hides(stack, "minecraft:potion_contents")) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
                potion(contents, durationScale(stack), context, lines);
            }
        }
        components(stack, "after potion", context, flag, lines);
        if (own.contains("minecraft:trim") && !hides(stack, "minecraft:trim")) {
            trim(stack.kiln$saved("minecraft:trim"), lines);
        }
        for (String id : new String[] {"minecraft:stored_enchantments", "minecraft:enchantments"}) {
            if (own.contains(id) && !hides(stack, id)) {
                ItemEnchantments enchantments = stack.get(id.endsWith("stored_enchantments")
                        ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS);
                if (enchantments != null) {
                    enchantments.addToTooltip(context, lines, flag, null);
                }
            }
        }
        if (own.contains("minecraft:dyed_color") && !hides(stack, "minecraft:dyed_color")) {
            Object saved = stack.kiln$saved("minecraft:dyed_color");
            Object rgb = saved instanceof Map<?, ?> m ? m.get("rgb") : saved;
            if (rgb instanceof Number n) {
                lines.accept(flag != null && flag.isAdvanced()
                        ? Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", n.intValue() & 0xFFFFFF))
                                .withStyle(ChatFormatting.GRAY)
                        : Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        if (own.contains("minecraft:lore") && !hides(stack, "minecraft:lore")) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                lore.styledLines().forEach(lines);
            }
        }
        ItemAttributes.lines(stack, player, lines);
        if (own.contains("minecraft:unbreakable") && !hides(stack, "minecraft:unbreakable")) {
            lines.accept(Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE));
        }
        components(stack, "after unbreakable", context, flag, lines);
        if (flag != null && flag.isAdvanced()) {
            if (stack.isDamaged() && !hides(stack, "minecraft:damage")) {
                lines.accept(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(),
                        stack.getMaxDamage()));
            }
            lines.accept(Component.literal(stack.getItem().kiln$id().toString()).withStyle(ChatFormatting.DARK_GRAY));
            // Every component it has, its item's (a game item's from items.json) and its own.
            Set<String> all = new java.util.HashSet<>(own);
            JsonObject defaults = GameItems.itemComponents(stack.getItem().kiln$id().toString());
            if (defaults != null) {
                all.addAll(defaults.keySet());
            }
            int count = all.size();
            if (count > 0) {
                lines.accept(Component.translatable("item.components", count).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /**
     * The lines of the components a recipe viewer shows on the game's special items (D-172), each where the game's
     * {@code addDetailsToTooltip} writes it ({@code where}): before the potion's lines a painting's title, author and
     * size, a goat horn's instrument, a banner's or a shield's patterns (six at most), a rocket's flight duration and
     * stars, a firework star's explosion; after them a music disc's song; after "Unbreakable" an ominous bottle's Bad
     * Omen. A component the stack hasn't is its item's (a goat horn's is Ponder), from {@code items.json}.
     */
    static void components(ItemStack stack, String where, Item.TooltipContext context, TooltipFlag flag,
            Consumer<Component> lines) {
        switch (where) {
            case "before" -> {
                painting(component(stack, "minecraft:painting/variant"), lines);
                JsonElement instrument = component(stack, "minecraft:instrument");
                if (instrument != null) {
                    Component text = instrument.isJsonPrimitive()
                            ? Component.translatable(key("instrument", instrument.getAsString()))
                            : instrument.isJsonObject() ? description(instrument.getAsJsonObject().get("description"))
                            : null;
                    if (text != null) {
                        lines.accept(text.copy().withStyle(ChatFormatting.GRAY));
                    }
                }
                map(stack, context, flag, lines);
                container(stack, lines);
                JsonElement patterns = component(stack, "minecraft:banner_patterns");
                if (patterns != null && patterns.isJsonArray()) {
                    int shown = 0;
                    for (JsonElement layer : patterns.getAsJsonArray()) {
                        if (shown++ == 6 || !layer.isJsonObject()) {
                            break;
                        }
                        JsonObject o = layer.getAsJsonObject();
                        // The pattern's translation key, from its data (an inline pattern carries its own).
                        JsonElement pattern = o.get("pattern");
                        JsonObject data = pattern == null ? null : pattern.isJsonObject() ? pattern.getAsJsonObject()
                                : pattern.isJsonPrimitive() ? gameData("banner_pattern", pattern.getAsString()) : null;
                        if (data != null && data.has("translation_key") && o.has("color")) {
                            lines.accept(Component.translatable(data.get("translation_key").getAsString() + "."
                                    + o.get("color").getAsString()).withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
                writtenBook(stack, lines);
                JsonElement fireworks = component(stack, "minecraft:fireworks");
                if (fireworks != null && fireworks.isJsonObject()) {
                    rocket(fireworks.getAsJsonObject(), lines);
                }
                JsonElement star = component(stack, "minecraft:firework_explosion");
                if (star != null && star.isJsonObject()) {
                    lines.accept(shape(star.getAsJsonObject()).withStyle(ChatFormatting.GRAY));
                    explosion(star.getAsJsonObject(), "", lines);
                }
            }
            case "after potion" -> {
                JsonElement song = component(stack, "minecraft:jukebox_playable");
                if (song != null) {
                    Component text = song.isJsonPrimitive()
                            ? Component.translatable(key("jukebox_song", song.getAsString()))
                            : song.isJsonObject() ? description(song.getAsJsonObject().get("description")) : null;
                    if (text != null) {
                        lines.accept(text.copy().withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            default -> {
                JsonElement bottle = component(stack, "minecraft:ominous_bottle_amplifier");
                if (bottle != null && bottle.isJsonPrimitive()) {
                    int amplifier = bottle.getAsInt();
                    MutableComponent line = Component.translatable("effect.minecraft.bad_omen");
                    if (amplifier > 0) {
                        line = Component.translatable("potion.withAmplifier", line,
                                Component.translatable("potion.potency." + amplifier));
                    }
                    // The bottle gives Bad Omen for 120000 ticks (100 minutes).
                    lines.accept(Component.translatable("potion.withDuration", line, Component.literal("01:40:00"))
                            .withStyle(ChatFormatting.BLUE));
                }
            }
        }
    }

    /**
     * A filled map's lines, as the game's {@code MapId.addToTooltip} (D-177): "Unknown Map" while the client hasn't the
     * map's data (Kiln has a map's data once the game drew it, {@code kiln.Maps}); else "ID #n" (without a custom name
     * or a pending lock or scale), "Locked", and with advanced tooltips the scale and level. All grey.
     */
    private static void map(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, Consumer<Component> lines) {
        if (!(stack.kiln$saved("minecraft:map_id") instanceof Number id) || hides(stack, "minecraft:map_id")) {
            return;
        }
        Maps.Known data = Maps.known(id.intValue());
        if (data == null) {
            lines.accept(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
            return;
        }
        // MapPostProcessing as saved: "lock" or "scale" (an id 0 or 1 in older saves).
        Object post = stack.kiln$saved("minecraft:map_post_processing");
        boolean lock = "lock".equals(post) || post instanceof Number n && n.intValue() == 0;
        boolean scale = "scale".equals(post) || post instanceof Number n && n.intValue() == 1;
        if (stack.kiln$saved("minecraft:custom_name") == null && post == null) {
            lines.accept(Component.translatable("filled_map.id", id.intValue()).withStyle(ChatFormatting.GRAY));
        }
        if (data.locked() || lock) {
            lines.accept(Component.translatable("filled_map.locked", id.intValue()).withStyle(ChatFormatting.GRAY));
        }
        if (flag != null && flag.isAdvanced()) {
            int level = Math.min(data.scale() + (scale ? 1 : 0), 4);
            lines.accept(Component.translatable("filled_map.scale", 1 << level).withStyle(ChatFormatting.GRAY));
            lines.accept(Component.translatable("filled_map.level", level, 4).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * A shulker box's (any container item's) contents, as the game's {@code ItemContainerContents.addToTooltip}:
     * "Name x count" for the first five stacks by slot, then "and n more..." in italics.
     */
    private static void container(ItemStack stack, Consumer<Component> lines) {
        if (!(stack.kiln$saved("minecraft:container") instanceof List<?> slots) || hides(stack, "minecraft:container")) {
            return;
        }
        List<Map<?, ?>> sorted = new java.util.ArrayList<>();
        for (Object slot : slots) {
            if (slot instanceof Map<?, ?> m && m.get("item") instanceof Map<?, ?>) {
                sorted.add(m);
            }
        }
        sorted.sort(java.util.Comparator.comparingInt(m -> m.get("slot") instanceof Number n ? n.intValue() : 0));
        int shown = 0;
        int all = 0;
        for (Map<?, ?> slot : sorted) {
            ItemStack inside = ItemStack.CODEC.parse(JsonOps.INSTANCE, TagValues.json(slot.get("item"))).result()
                    .orElse(ItemStack.EMPTY);
            if (inside.isEmpty()) {
                continue;
            }
            all++;
            if (shown < 5) {
                shown++;
                lines.accept(Component.translatable("container.shulkerBox.itemCount", inside.getHoverName(),
                        inside.getCount()));
            }
        }
        if (all > shown) {
            lines.accept(Component.translatable("container.shulkerBox.more", all - shown).withStyle(ChatFormatting.ITALIC));
        }
    }

    /**
     * A written book's lines, as the game's {@code WrittenBookContent.addToTooltip}: "by <author>" unless the author
     * is blank, then its generation ("Original", "Copy of original"...), both grey. Its title is its name.
     */
    private static void writtenBook(ItemStack stack, Consumer<Component> lines) {
        if (!(stack.kiln$saved("minecraft:written_book_content") instanceof Map<?, ?> book)
                || hides(stack, "minecraft:written_book_content")) {
            return;
        }
        if (book.get("author") instanceof String author && !author.isBlank()) {
            lines.accept(Component.translatable("book.byAuthor", author).withStyle(ChatFormatting.GRAY));
        }
        int generation = book.get("generation") instanceof Number n ? n.intValue() : 0;
        lines.accept(Component.translatable("book.generation." + generation).withStyle(ChatFormatting.GRAY));
    }

    /**
     * A bundle's contents for its tooltip's picture ({@code BundleItem.getTooltipImage}, D-177): for an item whose
     * stacks hold others ({@code bundle_contents}, the game's bundles), unless its {@code tooltip_display} hides them;
     * null for any other item.
     */
    public static net.minecraft.world.inventory.tooltip.BundleTooltip bundle(ItemStack stack) {
        if (stack.isEmpty() || hides(stack, "minecraft:bundle_contents")) {
            return null;
        }
        JsonElement contents = component(stack, "minecraft:bundle_contents");
        if (contents == null || !contents.isJsonArray()) {
            return null;
        }
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (JsonElement inside : contents.getAsJsonArray()) {
            ItemStack one = ItemStack.CODEC.parse(JsonOps.INSTANCE, inside).result().orElse(ItemStack.EMPTY);
            if (!one.isEmpty()) {
                stacks.add(one);
            }
        }
        net.minecraft.world.item.component.BundleContents value =
                stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        return new net.minecraft.world.inventory.tooltip.BundleTooltip(
                value == null ? net.minecraft.world.item.component.BundleContents.EMPTY : value, stacks);
    }

    /**
     * How full stacks make a bundle, as the game's {@code BundleContents.getWeight}: {@code {numerator, denominator}},
     * 1 full. A stack weighs its count over its item's stack size; a bundle in it its own contents and 1/16 more; a
     * beehive with bees a whole bundle.
     */
    public static long[] bundleWeight(List<ItemStack> stacks) {
        long n = 0;
        long d = 1;
        for (ItemStack s : stacks) {
            long[] one = itemWeight(s);
            n = n * one[1] + one[0] * s.getCount() * d;
            d = d * one[1];
            long g = java.math.BigInteger.valueOf(n).gcd(java.math.BigInteger.valueOf(d)).longValue();
            if (g > 1) {
                n /= g;
                d /= g;
            }
        }
        return new long[] {n, d};
    }

    private static long[] itemWeight(ItemStack stack) {
        JsonElement inside = component(stack, "minecraft:bundle_contents");
        if (inside != null && inside.isJsonArray()) {
            List<ItemStack> stacks = new java.util.ArrayList<>();
            for (JsonElement one : inside.getAsJsonArray()) {
                ItemStack.CODEC.parse(JsonOps.INSTANCE, one).result().filter(s -> !s.isEmpty()).ifPresent(stacks::add);
            }
            long[] w = bundleWeight(stacks);
            return new long[] {w[0] * 16 + w[1], w[1] * 16};
        }
        if (stack.kiln$saved("minecraft:bees") instanceof List<?> bees && !bees.isEmpty()) {
            return new long[] {1, 1};
        }
        JsonElement max = component(stack, "minecraft:max_stack_size");
        int size = max != null && max.isJsonPrimitive() ? max.getAsInt() : stack.getMaxStackSize();
        return new long[] {1, Math.max(1, size)};
    }

    /** A written book's title as the game names the stack by it ({@code ItemStack.getCustomName}); null for none. */
    public static String bookTitle(ItemStack stack) {
        if (!(stack.kiln$saved("minecraft:written_book_content") instanceof Map<?, ?> book)) {
            return null;
        }
        Object title = book.get("title");
        if (title instanceof Map<?, ?> filterable) {
            title = filterable.get("raw");
        }
        return title instanceof String raw && !raw.isBlank() ? raw : null;
    }

    /** Component {@code id} of the stack, as JSON: its own, else its game item's; null when neither has it or it's hidden. */
    private static JsonElement component(ItemStack stack, String id) {
        if (hides(stack, id)) {
            return null;
        }
        Object own = stack.kiln$saved(id);
        if (own != null) {
            return TagValues.json(own);
        }
        return GameItems.itemComponent(stack.getItem().kiln$id().toString(), id);
    }

    /** {@code prefix.namespace.path} for an id ({@code instrument.minecraft.call_goat_horn}). */
    private static String key(String prefix, String id) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
        return key == null ? prefix + "." + id : prefix + "." + key.getNamespace() + "." + key.getPath();
    }

    /** A painting variant's title and author (their data's colours) and its size, from the game's data. */
    private static void painting(JsonElement variant, Consumer<Component> lines) {
        JsonObject data = null;
        if (variant != null && variant.isJsonObject()) {
            data = variant.getAsJsonObject();
        } else if (variant != null && variant.isJsonPrimitive()) {
            data = gameData("painting_variant", variant.getAsString());
        }
        if (data == null) {
            return;
        }
        Component title = description(data.get("title"));
        Component author = description(data.get("author"));
        if (title != null) {
            lines.accept(title);
        }
        if (author != null) {
            lines.accept(author);
        }
        if (data.has("width") && data.has("height")) {
            lines.accept(Component.translatable("painting.dimensions", data.get("width").getAsInt(),
                    data.get("height").getAsInt()));
        }
    }

    private static final Map<String, Optional<JsonObject>> GAME_DATA = new ConcurrentHashMap<>();

    /** The game's data file {@code data/<ns>/<kind>/<path>.json} for an id, from its prepared resources; null for none. */
    static JsonObject gameData(String kind, String id) {
        return GAME_DATA.computeIfAbsent(kind + " " + id, k -> {
            String resources = System.getProperty("kiln.resources", "");
            net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
            if (resources.isEmpty() || key == null) {
                return Optional.empty();
            }
            Path file = Path.of(resources, "data", key.getNamespace(), kind, key.getPath() + ".json");
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return Optional.of(JsonParser.parseReader(in).getAsJsonObject());
            } catch (java.io.IOException | RuntimeException e) {
                Native.log("the game's " + kind + " " + id + " can't be read: " + e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** A rocket's lines: "Flight Duration: n", then its stars, a run of equal ones as "count x shape". */
    private static void rocket(JsonObject fireworks, Consumer<Component> lines) {
        int flight = fireworks.has("flight_duration") ? fireworks.get("flight_duration").getAsInt() : 1;
        if (flight > 0) {
            lines.accept(Component.translatable("item.minecraft.firework_rocket.flight_duration", flight)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!fireworks.has("explosions") || !fireworks.get("explosions").isJsonArray()) {
            return;
        }
        List<JsonElement> stars = fireworks.getAsJsonArray("explosions").asList();
        for (int i = 0; i < stars.size(); ) {
            int run = 1;
            while (i + run < stars.size() && stars.get(i + run).equals(stars.get(i))) {
                run++;
            }
            if (stars.get(i).isJsonObject()) {
                JsonObject star = stars.get(i).getAsJsonObject();
                lines.accept((run == 1 ? Component.translatable("item.minecraft.firework_rocket.single_star", shape(star))
                        : Component.translatable("item.minecraft.firework_rocket.multiple_stars", run, shape(star)))
                        .withStyle(ChatFormatting.GRAY));
                explosion(star, "  ", lines);
            }
            i += run;
        }
    }

    private static MutableComponent shape(JsonObject star) {
        String shape = star.has("shape") ? star.get("shape").getAsString() : "small_ball";
        return Component.translatable("item.minecraft.firework_star.shape." + shape);
    }

    /** A star's colours, fade colours, trail and twinkle, each a grey line ({@code indent} in, on a rocket). */
    private static void explosion(JsonObject star, String indent, Consumer<Component> lines) {
        String colors = colors(star.get("colors"));
        if (!colors.isEmpty()) {
            lines.accept(Component.literal(indent).append(colorNames(star.get("colors"))).withStyle(ChatFormatting.GRAY));
        }
        String fade = colors(star.get("fade_colors"));
        if (!fade.isEmpty()) {
            lines.accept(Component.literal(indent).append(Component.translatable("item.minecraft.firework_star.fade_to"))
                    .append(" ").append(colorNames(star.get("fade_colors"))).withStyle(ChatFormatting.GRAY));
        }
        if (flag(star.get("has_trail"))) {
            lines.accept(Component.literal(indent).append(Component.translatable("item.minecraft.firework_star.trail"))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (flag(star.get("has_twinkle"))) {
            lines.accept(Component.literal(indent).append(Component.translatable("item.minecraft.firework_star.flicker"))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A saved boolean: true, or a byte that isn't 0 (the game's SNBT {@code 1b}). */
    private static boolean flag(JsonElement value) {
        return value != null && value.isJsonPrimitive() && (value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : value.getAsJsonPrimitive().isNumber() && value.getAsInt() != 0);
    }

    private static String colors(JsonElement list) {
        return list == null || !list.isJsonArray() ? "" : list.toString().replaceAll("[\\[\\]]", "");
    }

    /** Colours by their dye's name ("Red, Blue"), "Custom" for one no dye has, as {@code FireworkExplosion}. */
    private static MutableComponent colorNames(JsonElement list) {
        MutableComponent names = Component.empty();
        boolean first = true;
        for (JsonElement c : list.getAsJsonArray()) {
            if (!first) {
                names.append(", ");
            }
            first = false;
            int rgb = c.getAsInt();
            String name = "custom_color";
            for (net.minecraft.world.item.DyeColor dye : net.minecraft.world.item.DyeColor.values()) {
                if (dye.getFireworkColor() == rgb) {
                    name = dye.getName();
                    break;
                }
            }
            names.append(Component.translatable("item.minecraft.firework_star." + name));
        }
        return names;
    }

    /** How long a potion's effects last on this item: its {@code potion_duration_scale} (a lingering one 1/4). */
    private static float durationScale(ItemStack stack) {
        Object own = stack.kiln$saved("minecraft:potion_duration_scale");
        if (own instanceof Number n) {
            return n.floatValue();
        }
        JsonElement json = GameItems.itemComponent(stack.getItem().kiln$id().toString(),
                "minecraft:potion_duration_scale");
        return json != null && json.isJsonPrimitive() ? json.getAsFloat() : 1.0F;
    }

    /**
     * A potion's lines, as {@code PotionContents.addPotionTooltip}: each effect with its level and, unless it ends
     * within a second, its duration, in its category's colour; "No Effects" in grey for none; then, when its effects
     * change an attribute, an empty line, "When Applied:" in purple and each change, blue for a gain, red for a loss
     * (D-172).
     */
    static void potion(PotionContents contents, float scale, Item.TooltipContext context, Consumer<Component> lines) {
        boolean[] any = {false};
        float tickRate = context == null ? 20.0F : context.tickRate();
        List<Component> applied = new java.util.ArrayList<>();
        contents.forEachEffect(effect -> {
            any[0] = true;
            Component change = attributeLine(effect.getEffect().getRegisteredName(), effect.getAmplifier());
            if (change != null) {
                applied.add(change);
            }
            MutableComponent line = PotionContents.getPotionDescription(effect.getEffect(), effect.getAmplifier());
            if (!effect.endsWithin(20)) {
                line = Component.translatable("potion.withDuration", line, duration(effect, tickRate));
            }
            lines.accept(line.withStyle(effect.getEffect().value().getCategory().getTooltipFormatting()));
        }, scale);
        if (!any[0]) {
            lines.accept(Component.translatable("effect.none").withStyle(ChatFormatting.GRAY));
        }
        if (!applied.isEmpty()) {
            lines.accept(Component.empty());
            lines.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));
            applied.forEach(lines);
        }
    }

    /**
     * The attribute a game effect changes while it lasts ({@code MobEffect.addAttributeModifier}): its attribute,
     * the amount at level I (a level more adds it again) and the operation (0 adds a value, 2 multiplies the total).
     * These are the game's own figures (Kiln's {@code MobEffect}s carry no modifiers); a mod's effect has none here.
     */
    private static final Map<String, Object[]> EFFECT_ATTRIBUTES = Map.of(
            "minecraft:speed", new Object[] {"movement_speed", 0.2, 2},
            "minecraft:slowness", new Object[] {"movement_speed", -0.15, 2},
            "minecraft:haste", new Object[] {"attack_speed", 0.1, 2},
            "minecraft:mining_fatigue", new Object[] {"attack_speed", -0.1, 2},
            "minecraft:strength", new Object[] {"attack_damage", 3.0, 0},
            "minecraft:weakness", new Object[] {"attack_damage", -4.0, 0},
            "minecraft:jump_boost", new Object[] {"safe_fall_distance", 1.0, 0},
            "minecraft:health_boost", new Object[] {"max_health", 4.0, 0},
            "minecraft:absorption", new Object[] {"max_absorption", 4.0, 0},
            "minecraft:luck", new Object[] {"luck", 1.0, 0});

    private static final Map<String, Object[]> MORE_EFFECT_ATTRIBUTES = Map.of(
            "minecraft:unluck", new Object[] {"luck", -1.0, 0},
            "minecraft:invisibility", new Object[] {"waypoint_transmit_range", -1.0, 2});

    /** An effect's attribute line at {@code amplifier}, as {@code PotionContents.addPotionTooltip}; null for none. */
    static Component attributeLine(String effect, int amplifier) {
        Object[] change = EFFECT_ATTRIBUTES.getOrDefault(effect, MORE_EFFECT_ATTRIBUTES.get(effect));
        if (change == null) {
            return null;
        }
        double amount = (Double) change[1] * (amplifier + 1);
        int operation = (Integer) change[2];
        double shown = operation == 0 ? amount : amount * 100.0;
        Component name = Component.translatable("attribute.name." + change[0]);
        return amount > 0
                ? Component.translatable("attribute.modifier.plus." + operation, amountText(shown), name)
                        .withStyle(ChatFormatting.BLUE)
                : Component.translatable("attribute.modifier.take." + operation, amountText(-shown), name)
                        .withStyle(ChatFormatting.RED);
    }

    /** The game's {@code ATTRIBUTE_MODIFIER_FORMAT}: at most two decimals, none when whole ("#.##"). */
    static String amountText(double amount) {
        java.text.DecimalFormat format = new java.text.DecimalFormat("#.##",
                java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }

    /**
     * {@code MobEffectUtil.formatDuration}: "∞" for an endless one, else mm:ss (hh:mm:ss from an hour), every field two
     * digits, as the game's {@code StringUtil.formatTickDuration} ("03:00").
     */
    static Component duration(MobEffectInstance effect, float tickRate) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        }
        int seconds = (int) Math.floor(effect.getDuration() / tickRate);
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        String text = hours > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60);
        return Component.literal(text);
    }

    /**
     * An armor trim's lines, as {@code ArmorTrim.addToTooltip}: "Upgrade:" in grey, then the pattern's name in the
     * material's colour and the material's name, each a space in; their names from the game's {@code trim_pattern}
     * and {@code trim_material} data (a material may be written whole, as an object).
     */
    static void trim(Object saved, Consumer<Component> lines) {
        if (!(saved instanceof Map<?, ?> trim)) {
            return;
        }
        Component material = trimText("trim_material", trim.get("material"));
        Component pattern = trimText("trim_pattern", trim.get("pattern"));
        if (material == null || pattern == null) {
            return;
        }
        lines.accept(Component.translatable("item.minecraft.smithing_template.upgrade").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal(" ").append(pattern.copy().setStyle(material.getStyle())));
        lines.accept(Component.literal(" ").append(material));
    }

    /** A trim material's or pattern's description: its data file's ({@code minecraft:gold}), or an inline one's. */
    private static Component trimText(String kind, Object value) {
        if (value instanceof Map<?, ?> inline) {
            return description(TagValues.json(inline.get("description")));
        }
        if (!(value instanceof String id)) {
            return null;
        }
        return TRIM_TEXTS.computeIfAbsent(kind + " " + id, k -> {
            String resources = System.getProperty("kiln.resources", "");
            net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
            if (resources.isEmpty() || key == null) {
                return Optional.empty();
            }
            Path file = Path.of(resources, "data", key.getNamespace(), kind, key.getPath() + ".json");
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                return Optional.ofNullable(description(o.get("description")));
            } catch (java.io.IOException | RuntimeException e) {
                Native.log("the game's " + kind + " " + id + " can't be read: " + e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static Component description(JsonElement json) {
        return json == null ? null
                : ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
    }
}
