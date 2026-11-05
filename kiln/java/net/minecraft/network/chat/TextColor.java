package net.minecraft.network.chat;

import net.minecraft.ChatFormatting;

/** Kiln's stand-in for {@code TextColor}: a text's colour, an RGB value, named when it is a chat colour. */
public final class TextColor {
    private final int value;
    private final String name;

    private TextColor(int value, String name) {
        this.value = value & 0xFFFFFF;
        this.name = name;
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb, null);
    }

    public static TextColor fromLegacyFormat(ChatFormatting format) {
        return format.isColor() ? new TextColor(format.getColor(), format.getName()) : null;
    }

    public int getValue() {
        return value;
    }

    /** As the game's JSON writes it: the chat colour's name, else {@code #RRGGBB}. */
    public String serialize() {
        return name != null ? name : String.format("#%06X", value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextColor c && c.value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
