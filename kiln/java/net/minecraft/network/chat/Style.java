package net.minecraft.network.chat;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.ChatFormatting;

/**
 * Kiln's stand-in for {@code Style}: a text's colour, its formats (bold, italic, underlined, strikethrough,
 * obfuscated), what shows when it is hovered, what a click does and what a shift-click puts in the chat box. A component set to it (`MutableComponent.withStyle`) goes to the game
 * with them, as vanilla's JSON. Each format is unset, on or off (#217, D-185): vanilla's {@code Boolean} fields, so
 * a mod's {@code withItalic(false)} (an item's custom name upright, the game's are italic) reaches the game as
 * {@code "italic": false}, and {@code withItalic(null)} leaves it out.
 */
public class Style {
    public static final Style EMPTY = new Style(null, EnumSet.noneOf(ChatFormatting.class),
            EnumSet.noneOf(ChatFormatting.class), null, null, null);

    private final TextColor color;
    private final Set<ChatFormatting> formats;
    /** The formats set off explicitly ({@code withItalic(false)}): never in {@link #formats} too. */
    private final Set<ChatFormatting> off;
    private final HoverEvent hoverEvent;
    private final ClickEvent clickEvent;
    /** What a shift-click on the text puts in the chat box (vanilla's {@code insertion}); null for none. */
    private final String insertion;

    private Style(TextColor color, Set<ChatFormatting> formats, Set<ChatFormatting> off, HoverEvent hoverEvent,
            ClickEvent clickEvent, String insertion) {
        this.color = color;
        this.formats = formats;
        this.off = off;
        this.hoverEvent = hoverEvent;
        this.clickEvent = clickEvent;
        this.insertion = insertion;
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

    public String getInsertion() {
        return insertion;
    }

    public boolean isEmpty() {
        return color == null && formats.isEmpty() && off.isEmpty() && hoverEvent == null && clickEvent == null
                && insertion == null;
    }

    /** Kiln's own: its formats (bold, italic...), not its colour. */
    public Set<ChatFormatting> kiln$formats() {
        return formats.isEmpty() ? Set.of() : EnumSet.copyOf(formats);
    }

    /** Kiln's own: the formats set off explicitly ({@code withItalic(false)}), which go to the game as {@code false}. */
    public Set<ChatFormatting> kiln$off() {
        return off.isEmpty() ? Set.of() : EnumSet.copyOf(off);
    }

    public Style withColor(TextColor color) {
        return new Style(color, formats, off, hoverEvent, clickEvent, insertion);
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
        return new Style(color, formats, off, hoverEvent, clickEvent, insertion);
    }

    public Style withClickEvent(ClickEvent clickEvent) {
        return new Style(color, formats, off, hoverEvent, clickEvent, insertion);
    }

    public Style withInsertion(String insertion) {
        return new Style(color, formats, off, hoverEvent, clickEvent, insertion);
    }

    private Style with(ChatFormatting format, Boolean on) {
        Set<ChatFormatting> now = copy(formats);
        Set<ChatFormatting> nowOff = copy(off);
        now.remove(format);
        nowOff.remove(format);
        if (on != null) {
            (on ? now : nowOff).add(format);
        }
        return new Style(color, now, nowOff, hoverEvent, clickEvent, insertion);
    }

    /** Two styles are equal when each part is, as the game's (a format set off differs from one left unset). */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Style s && java.util.Objects.equals(color, s.color)
                && formats.equals(s.formats) && off.equals(s.off) && java.util.Objects.equals(hoverEvent, s.hoverEvent)
                && java.util.Objects.equals(clickEvent, s.clickEvent);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(color, formats, off, hoverEvent, clickEvent);
    }

    private static Set<ChatFormatting> copy(Set<ChatFormatting> formats) {
        return formats.isEmpty() ? EnumSet.noneOf(ChatFormatting.class) : EnumSet.copyOf(formats);
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
        // What other sets (on or off) wins; what it leaves unset comes from this one.
        Set<ChatFormatting> merged = copy(formats);
        merged.removeAll(other.off);
        merged.addAll(other.formats);
        Set<ChatFormatting> mergedOff = copy(off);
        mergedOff.removeAll(other.formats);
        mergedOff.addAll(other.off);
        return new Style(other.color != null ? other.color : color, merged, mergedOff,
                other.hoverEvent != null ? other.hoverEvent : hoverEvent,
                other.clickEvent != null ? other.clickEvent : clickEvent,
                other.insertion != null ? other.insertion : insertion);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public java.lang.Integer getShadowColor() { kiln.Missing.called("net.minecraft.network.chat.Style.getShadowColor"); return null; }
    public net.minecraft.network.chat.FontDescription getFont() { kiln.Missing.called("net.minecraft.network.chat.Style.getFont"); return null; }
    public net.minecraft.network.chat.Style withShadowColor(int p0) { kiln.Missing.called("net.minecraft.network.chat.Style.withShadowColor"); return this; }
    public net.minecraft.network.chat.Style withFont(net.minecraft.network.chat.FontDescription p0) { kiln.Missing.called("net.minecraft.network.chat.Style.withFont"); return this; }
    public net.minecraft.network.chat.Style applyLegacyFormat(net.minecraft.ChatFormatting p0) { kiln.Missing.called("net.minecraft.network.chat.Style.applyLegacyFormat"); return this; }
    public static class Serializer {
        public static final com.mojang.serialization.Codec<net.minecraft.network.chat.Style> CODEC = kiln.Constants.codec("net.minecraft.network.chat.Style.Serializer.CODEC");
        public Serializer() {  }
    }
    // End of what tools/kiln-stubs.py generated.
}
