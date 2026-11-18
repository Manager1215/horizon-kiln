package net.minecraft.network.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.net.URI;

/**
 * Kiln's stand-in for {@code ComponentSerialization}: a text as the game saves it (an item's custom name, its lore), in
 * vanilla's form. A plain text is a string; anything more an object ({@code text} or {@code translate} with {@code
 * with}, {@code color}, the formats, {@code hover_event}, {@code click_event}, {@code insertion}, {@code extra}); a
 * list is its first part with the others after it.
 */
public final class ComponentSerialization {
    /** A text, read and written as the game does (any {@code DynamicOps}: JSON, NBT, Kiln's tags). */
    public static final Codec<Component> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> read(dynamic.convert(JsonOps.INSTANCE).getValue()),
            component -> new Dynamic<>(JsonOps.INSTANCE, write(component)));

    private ComponentSerialization() {}

    /** Kiln's own: a text as the game saves it, a plain one as a string. */
    public static JsonElement write(Component component) {
        JsonObject json = component.kiln$json();
        if (json.size() == 1 && json.has("text")) {
            return json.get("text");
        }
        return json;
    }

    /** Kiln's own: the text the game saved, or an error naming what isn't one. */
    public static DataResult<Component> read(JsonElement json) {
        try {
            return DataResult.success(parse(json));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "Not a text: " + json + " (" + e.getMessage() + ")");
        }
    }

    /** Kiln's own: the same text in any {@code DynamicOps}. */
    public static <T> T encode(DynamicOps<T> ops, Component component) {
        return JsonOps.INSTANCE.convertTo(ops, write(component));
    }

    private static MutableComponent parse(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return Component.literal(json.getAsString());
        }
        if (json.isJsonArray()) {
            JsonArray parts = json.getAsJsonArray();
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("an empty list");
            }
            MutableComponent first = parse(parts.get(0));
            for (int i = 1; i < parts.size(); i++) {
                first.append(parse(parts.get(i)));
            }
            return first;
        }
        JsonObject o = json.getAsJsonObject();
        MutableComponent c;
        if (o.has("translate")) {
            Object[] args = new Object[0];
            if (o.get("with") instanceof JsonArray with) {
                args = new Object[with.size()];
                for (int i = 0; i < with.size(); i++) {
                    JsonElement arg = with.get(i);
                    args[i] = arg instanceof JsonPrimitive p && p.isNumber() ? p.getAsNumber() : parse(arg);
                }
            }
            c = Component.translatable(o.get("translate").getAsString(), args);
        } else if (o.has("text")) {
            c = Component.literal(o.get("text").getAsString());
        } else {
            c = Component.literal("");
        }
        Style style = Style.EMPTY;
        if (o.has("color")) {
            String color = o.get("color").getAsString();
            style = style.withColor(TextColor.parseColor(color).getOrThrow(IllegalArgumentException::new));
        }
        style = style.withBold(flag(o, "bold")).withItalic(flag(o, "italic")).withUnderlined(flag(o, "underlined"))
                .withStrikethrough(flag(o, "strikethrough")).withObfuscated(flag(o, "obfuscated"));
        if (o.get("hover_event") instanceof JsonObject hover && "show_text".equals(string(hover, "action"))
                && hover.has("value")) {
            style = style.withHoverEvent(new HoverEvent.ShowText(parse(hover.get("value"))));
        }
        if (o.get("click_event") instanceof JsonObject click) {
            ClickEvent event = click(click);
            if (event != null) {
                style = style.withClickEvent(event);
            }
        }
        if (string(o, "insertion") != null) {
            style = style.withInsertion(string(o, "insertion"));
        }
        c.withStyle(style);
        if (o.get("extra") instanceof JsonArray extra) {
            for (JsonElement part : extra) {
                c.append(parse(part));
            }
        }
        return c;
    }

    private static Boolean flag(JsonObject o, String name) {
        return o.get(name) instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : null;
    }

    private static String string(JsonObject o, String name) {
        return o.get(name) instanceof JsonPrimitive p ? p.getAsString() : null;
    }

    private static ClickEvent click(JsonObject o) {
        String action = string(o, "action");
        if (action == null) {
            return null;
        }
        return switch (action) {
            case "open_url" -> string(o, "url") == null ? null : new ClickEvent.OpenUrl(URI.create(string(o, "url")));
            case "run_command" -> string(o, "command") == null ? null : new ClickEvent.RunCommand(string(o, "command"));
            case "suggest_command" -> string(o, "command") == null ? null
                    : new ClickEvent.SuggestCommand(string(o, "command"));
            case "copy_to_clipboard" -> string(o, "value") == null ? null
                    : new ClickEvent.CopyToClipboard(string(o, "value"));
            case "change_page" -> o.get("page") instanceof JsonPrimitive p && p.isNumber()
                    ? new ClickEvent.ChangePage(p.getAsInt()) : null;
            default -> null;
        };
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.network.chat.Component> STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC");
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, java.util.Optional<net.minecraft.network.chat.Component>> OPTIONAL_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.network.chat.ComponentSerialization.OPTIONAL_STREAM_CODEC");
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.network.chat.Component> TRUSTED_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC");
    // End of what tools/kiln-stubs.py generated.
}
