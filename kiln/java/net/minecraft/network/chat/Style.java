package net.minecraft.network.chat;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.ChatFormatting;

/**
 * Kiln's stand-in for {@code Style}: a text's colour, its formats (bold, italic, underlined, strikethrough,
 * obfuscated), what shows when it is hovered and what a click does. A component set to it (`MutableComponent.withStyle`) goes to the game
 * with them, as vanilla's JSON.
 */
public class Style {
    public static final Style EMPTY = new Style(null, EnumSet.noneOf(ChatFormatting.class), null, null);

    private final TextColor color;
    private final Set<ChatFormatting> formats;
    private final HoverEvent hoverEvent;
    private final ClickEvent clickEvent;

    private Style(TextColor color, Set<ChatFormatting> formats, HoverEvent hoverEvent, ClickEvent clickEvent) {
        this.color = color;
        this.formats = formats;
        this.hoverEvent = hoverEvent;
        this.clickEvent = clickEvent;
    }

    public TextColor getColor() {
        return color;
    }

    public boolean isBold() {
        return formats.contains(ChatFormatting.BOLD);
    }

    public boolean isItalic() {
        return formats.contains(ChatFormatting.ITALIC);
    }

    public boolean isUnderlined() {
        return formats.contains(ChatFormatting.UNDERLINE);
    }

    public boolean isStrikethrough() {
        return formats.contains(ChatFormatting.STRIKETHROUGH);
    }

    public boolean isObfuscated() {
        return formats.contains(ChatFormatting.OBFUSCATED);
    }

    public HoverEvent getHoverEvent() {
        return hoverEvent;
    }

    public ClickEvent getClickEvent() {
        return clickEvent;
    }

    public boolean isEmpty() {
        return color == null && formats.isEmpty() && hoverEvent == null && clickEvent == null;
    }

    /** Kiln's own: its formats (bold, italic...), not its colour. */
    public Set<ChatFormatting> kiln$formats() {
        return formats.isEmpty() ? Set.of() : EnumSet.copyOf(formats);
    }

    public Style withColor(TextColor color) {
        return new Style(color, formats, hoverEvent, clickEvent);
    }

    public Style withColor(ChatFormatting format) {
        return withColor(format == null ? null : TextColor.fromLegacyFormat(format));
    }

    public Style withColor(int rgb) {
        return withColor(TextColor.fromRgb(rgb));
    }

    public Style withBold(Boolean bold) {
        return with(ChatFormatting.BOLD, bold);
    }

    public Style withItalic(Boolean italic) {
        return with(ChatFormatting.ITALIC, italic);
    }

    public Style withUnderlined(Boolean underlined) {
        return with(ChatFormatting.UNDERLINE, underlined);
    }

    public Style withStrikethrough(Boolean strikethrough) {
        return with(ChatFormatting.STRIKETHROUGH, strikethrough);
    }

    public Style withObfuscated(Boolean obfuscated) {
        return with(ChatFormatting.OBFUSCATED, obfuscated);
    }

    public Style withHoverEvent(HoverEvent hoverEvent) {
        return new Style(color, formats, hoverEvent, clickEvent);
    }

    public Style withClickEvent(ClickEvent clickEvent) {
        return new Style(color, formats, hoverEvent, clickEvent);
    }

    private Style with(ChatFormatting format, Boolean on) {
        Set<ChatFormatting> now = formats.isEmpty() ? EnumSet.noneOf(ChatFormatting.class) : EnumSet.copyOf(formats);
        if (Boolean.TRUE.equals(on)) {
            now.add(format);
        } else {
            now.remove(format);
        }
        return new Style(color, now, hoverEvent, clickEvent);
    }

    /** With a chat format applied: a colour, or a format (bold, italic...); reset to none. */
    public Style applyFormat(ChatFormatting format) {
        if (format == ChatFormatting.RESET) {
            return EMPTY;
        }
        return format.isColor() ? withColor(format) : with(format, true);
    }

    public Style applyFormats(ChatFormatting... formats) {
        Style style = this;
        for (ChatFormatting format : formats) {
            style = style.applyFormat(format);
        }
        return style;
    }

    /** This style, with what {@code other} doesn't set taken from it. */
    public Style applyTo(Style other) {
        if (this == EMPTY) {
            return other;
        }
        Set<ChatFormatting> merged = EnumSet.noneOf(ChatFormatting.class);
        merged.addAll(formats);
        merged.addAll(other.formats);
        return new Style(other.color != null ? other.color : color, merged,
                other.hoverEvent != null ? other.hoverEvent : hoverEvent,
                other.clickEvent != null ? other.clickEvent : clickEvent);
    }
}
