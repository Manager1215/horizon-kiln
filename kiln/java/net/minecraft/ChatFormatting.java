package net.minecraft;

import java.util.Locale;

/** Kiln's stand-in for {@code ChatFormatting}: chat colours and styles, with their legacy codes. */
public enum ChatFormatting {
    BLACK('0', true),
    DARK_BLUE('1', true),
    DARK_GREEN('2', true),
    DARK_AQUA('3', true),
    DARK_RED('4', true),
    DARK_PURPLE('5', true),
    GOLD('6', true),
    GRAY('7', true),
    DARK_GRAY('8', true),
    BLUE('9', true),
    GREEN('a', true),
    AQUA('b', true),
    RED('c', true),
    LIGHT_PURPLE('d', true),
    YELLOW('e', true),
    WHITE('f', true),
    OBFUSCATED('k', false),
    BOLD('l', false),
    STRIKETHROUGH('m', false),
    UNDERLINE('n', false),
    ITALIC('o', false),
    RESET('r', false);

    private final char code;
    private final boolean color;

    ChatFormatting(char code, boolean color) {
        this.code = code;
        this.color = color;
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
