package net.minecraft.network.chat;

import net.minecraft.ChatFormatting;

/** Kiln's stand-in for {@code Style}: a text's colour, bold and italic. Kiln draws text plain in one colour. */
public class Style {
    public static final Style EMPTY = new Style(null, false, false);

    private final TextColor color;
    private final boolean bold;
    private final boolean italic;

    private Style(TextColor color, boolean bold, boolean italic) {
        this.color = color;
        this.bold = bold;
        this.italic = italic;
    }

    public TextColor getColor() {
        return color;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isEmpty() {
        return color == null && !bold && !italic;
    }

    public Style withColor(TextColor color) {
        return new Style(color, bold, italic);
    }

    public Style withColor(ChatFormatting format) {
        return withColor(format == null ? null : TextColor.fromLegacyFormat(format));
    }

    public Style withColor(int rgb) {
        return withColor(TextColor.fromRgb(rgb));
    }

    public Style withBold(Boolean bold) {
        return new Style(color, Boolean.TRUE.equals(bold), italic);
    }

    public Style withItalic(Boolean italic) {
        return new Style(color, bold, Boolean.TRUE.equals(italic));
    }

    /** With a chat format applied: a colour, or bold and italic. */
    public Style applyFormat(ChatFormatting format) {
        if (format == ChatFormatting.RESET) {
            return EMPTY;
        }
        if (format.isColor()) {
            return withColor(format);
        }
        return format == ChatFormatting.BOLD ? withBold(true) : format == ChatFormatting.ITALIC ? withItalic(true) : this;
    }
}
