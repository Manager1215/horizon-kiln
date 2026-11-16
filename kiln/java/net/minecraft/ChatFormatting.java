package net.minecraft;

import java.util.Locale;

/** Kiln's stand-in for {@code ChatFormatting}: chat colours and styles, with their legacy codes. */
public enum ChatFormatting {
    BLACK('0', 0x000000),
    DARK_BLUE('1', 0x0000AA),
    DARK_GREEN('2', 0x00AA00),
    DARK_AQUA('3', 0x00AAAA),
    DARK_RED('4', 0xAA0000),
    DARK_PURPLE('5', 0xAA00AA),
    GOLD('6', 0xFFAA00),
    GRAY('7', 0xAAAAAA),
    DARK_GRAY('8', 0x555555),
    BLUE('9', 0x5555FF),
    GREEN('a', 0x55FF55),
    AQUA('b', 0x55FFFF),
    RED('c', 0xFF5555),
    LIGHT_PURPLE('d', 0xFF55FF),
    YELLOW('e', 0xFFFF55),
    WHITE('f', 0xFFFFFF),
    OBFUSCATED('k', null),
    BOLD('l', null),
    STRIKETHROUGH('m', null),
    UNDERLINE('n', null),
    ITALIC('o', null),
    RESET('r', null);

    private final char code;
    private final boolean color;
    /** A colour's RGB, as the chat draws it; null for the styles. */
    private final Integer rgb;

    ChatFormatting(char code, Integer rgb) {
        this.code = code;
        this.color = rgb != null;
        this.rgb = rgb;
    }

    /** A colour's RGB; null for a style (bold...). */
    public Integer getColor() {
        return rgb;
    }

    private static final java.util.regex.Pattern STRIP = java.util.regex.Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");

    /** {@code text} without its formatting codes ("§a"); null stays null. */
    public static String stripFormatting(String text) {
        return text == null ? null : STRIP.matcher(text).replaceAll("");
    }

    /** The formatting of code {@code code} ('a' for green), or null. */
    public static ChatFormatting getByCode(char code) {
        char lower = Character.toLowerCase(code);
        for (ChatFormatting f : values()) {
            if (f.code == lower) {
                return f;
            }
        }
        return null;
    }

    public char getChar() {
        return code;
    }

    public boolean isColor() {
        return color;
    }

    public boolean isFormat() {
        return !color && this != RESET;
    }

    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "§" + code;
    }
}
