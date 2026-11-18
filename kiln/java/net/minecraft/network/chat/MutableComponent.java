package net.minecraft.network.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kiln.Language;
import net.minecraft.ChatFormatting;

/** Kiln's stand-in for {@code MutableComponent}: text that more text and styles can be added to. */
public final class MutableComponent implements Component {
    private final String text;
    private final String key;
    private final Object[] args;
    private final List<Component> siblings = new ArrayList<>();
    private String color;
    private final Set<ChatFormatting> formats = EnumSet.noneOf(ChatFormatting.class);
    /** Formats set off explicitly (Style's {@code withItalic(false)}, D-185): {@code false} in the JSON. */
    private final Set<ChatFormatting> off = EnumSet.noneOf(ChatFormatting.class);
    private HoverEvent hover;
    private ClickEvent click;
    /** What a shift-click puts in the chat box (Style's {@code insertion}). */
    private String insertion;

    private MutableComponent(String text, String key, Object[] args) {
        this.text = text;
        this.key = key;
        this.args = args;
    }

    static MutableComponent kiln$literal(String text) {
        return new MutableComponent(text, null, null);
    }

    static MutableComponent kiln$translatable(String key, Object[] args) {
        return new MutableComponent(null, key, args);
    }

    /** A component holding {@code contents}: a literal text or a translation. */
    public static MutableComponent create(ComponentContents contents) {
        if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            return new MutableComponent(null, t.getKey(), t.getArgs());
        }
        if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents p) {
            return new MutableComponent(p.text(), null, null);
        }
        return new MutableComponent(contents.visit(java.util.Optional::of).orElse(""), null, null);
    }

    @Override
    public ComponentContents getContents() {
        return key == null ? net.minecraft.network.chat.contents.PlainTextContents.create(text)
                : new net.minecraft.network.chat.contents.TranslatableContents(key, null, args);
    }

    /** With {@code style}'s colour, formats, hover, click and insertion added to its own. */
    public MutableComponent withStyle(Style style) {
        if (style.getColor() != null) {
            color = style.getColor().serialize();
        }
        formats.removeAll(style.kiln$off());
        formats.addAll(style.kiln$formats());
        off.removeAll(style.kiln$formats());
        off.addAll(style.kiln$off());
        if (style.getHoverEvent() != null) {
            hover = style.getHoverEvent();
        }
        if (style.getClickEvent() != null) {
            click = style.getClickEvent();
        }
        if (style.getInsertion() != null) {
            insertion = style.getInsertion();
        }
        return this;
    }

    /** A new component with its contents, style and parts (the parts themselves shared, as vanilla's). */
    @Override
    public MutableComponent copy() {
        MutableComponent copy = new MutableComponent(text, key, args);
        copy.color = color;
        copy.formats.addAll(formats);
        copy.off.addAll(off);
        copy.hover = hover;
        copy.click = click;
        copy.insertion = insertion;
        copy.siblings.addAll(siblings);
        return copy;
    }

    /** With {@code style} in place of its own. */
    public MutableComponent setStyle(Style style) {
        color = null;
        formats.clear();
        off.clear();
        hover = null;
        click = null;
        insertion = null;
        return withStyle(style);
    }

    public MutableComponent withStyle(java.util.function.UnaryOperator<Style> change) {
        return setStyle(change.apply(getStyle()));
    }

    public MutableComponent append(String more) {
        return append(Component.literal(more));
    }

    public MutableComponent append(Component more) {
        siblings.add(more);
        return this;
    }

    public MutableComponent withStyle(ChatFormatting format) {
        if (format == ChatFormatting.RESET) {
            color = null;
            formats.clear();
            off.clear();
        } else if (format.isColor()) {
            color = format.getName();
        } else {
            formats.add(format);
            off.remove(format);
        }
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formats) {
        for (ChatFormatting format : formats) {
            withStyle(format);
        }
        return this;
    }

    public MutableComponent withColor(int rgb) {
        color = String.format("#%06X", rgb & 0xFFFFFF);
        return this;
    }

    public MutableComponent withColor(TextColor textColor) {
        color = textColor == null ? null : textColor.serialize();
        return this;
    }

    /** Its colour, bold and italic, from what was set on it. */
    @Override
    public Style getStyle() {
        Style style = Style.EMPTY;
        if (color != null) {
            ChatFormatting named = color.startsWith("#") ? null : ChatFormatting.valueOf(color.toUpperCase(java.util.Locale.ROOT));
            style = named != null ? style.withColor(named) : style.withColor(Integer.parseInt(color.substring(1), 16));
        }
        for (ChatFormatting format : formats) {
            style = style.applyFormat(format);
        }
        for (ChatFormatting format : off) {
            style = kiln$setOff(style, format);
        }
        return style.withHoverEvent(hover).withClickEvent(click).withInsertion(insertion);
    }

    private static Style kiln$setOff(Style style, ChatFormatting format) {
        return switch (format) {
            case BOLD -> style.withBold(false);
            case ITALIC -> style.withItalic(false);
            case UNDERLINE -> style.withUnderlined(false);
            case STRIKETHROUGH -> style.withStrikethrough(false);
            case OBFUSCATED -> style.withObfuscated(false);
            default -> style;
        };
    }

    /** Its own text in its style on top of {@code parent}, then each sibling's on top of that. */
    @Override
    public <T> java.util.Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style parent) {
        Style style = parent.applyTo(getStyle());
        String own = key == null ? text : Language.format(key, args);
        if (!own.isEmpty()) {
            java.util.Optional<T> out = consumer.accept(style, own);
            if (out.isPresent()) {
                return out;
            }
        }
        for (Component sibling : siblings) {
            java.util.Optional<T> out = sibling.visit(consumer, style);
            if (out.isPresent()) {
                return out;
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public List<Component> getSiblings() {
        return siblings;
    }

    @Override
    public String getString() {
        StringBuilder b = new StringBuilder(key == null ? text : Language.format(key, args));
        for (Component sibling : siblings) {
            b.append(sibling.getString());
        }
        return b.toString();
    }

    @Override
    public JsonObject kiln$json() {
        JsonObject json = new JsonObject();
        if (key == null) {
            json.addProperty("text", text);
        } else if (Language.modOnly(key)) {
            // A mod's own key: the game has no text for it, so it goes as the mod's (0.4.0 sent the key, D-109).
            json.addProperty("text", Language.format(key, args));
        } else {
            json.addProperty("translate", key);
            if (args.length > 0) {
                JsonArray with = new JsonArray();
                for (Object arg : args) {
                    if (arg instanceof Component c) {
                        with.add(c.kiln$json());
                    } else if (arg instanceof Number n) {
                        with.add(n);
                    } else {
                        with.add(String.valueOf(arg));
                    }
                }
                json.add("with", with);
            }
        }
        if (color != null) {
            json.addProperty("color", color);
        }
        for (ChatFormatting format : formats) {
            json.addProperty(format == ChatFormatting.UNDERLINE ? "underlined" : format.getName(), true);
        }
        for (ChatFormatting format : off) {
            json.addProperty(format == ChatFormatting.UNDERLINE ? "underlined" : format.getName(), false);
        }
        if (hover != null) {
            json.add("hover_event", hover.kiln$json());
        }
        if (click != null) {
            json.add("click_event", click.kiln$json());
        }
        if (insertion != null) {
            json.addProperty("insertion", insertion);
        }
        if (!siblings.isEmpty()) {
            JsonArray extra = new JsonArray();
            siblings.forEach(s -> extra.add(s.kiln$json()));
            json.add("extra", extra);
        }
        return json;
    }

    @Override
    public String toString() {
        return getString();
    }

    /**
     * As vanilla's: the same text or translation (and its arguments), style and siblings. A mod compares an option's
     * caption with {@code Component.translatable("options.gamma")} to know the option (Resource Gamma Util, D-192).
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof MutableComponent m && java.util.Objects.equals(text, m.text)
                && java.util.Objects.equals(key, m.key) && java.util.Arrays.equals(args, m.args)
                && java.util.Objects.equals(color, m.color) && formats.equals(m.formats)
                && java.util.Objects.equals(hover, m.hover) && java.util.Objects.equals(click, m.click)
                && java.util.Objects.equals(insertion, m.insertion)
                && siblings.equals(m.siblings);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(text, key, java.util.Arrays.hashCode(args), color, formats, siblings);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.network.chat.ComponentContents contents;
    // End of what tools/kiln-stubs.py generated.
}
