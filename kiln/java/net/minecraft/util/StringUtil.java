package net.minecraft.util;

/**
 * Kiln's stand-in for {@code StringUtil}: the game's text helpers, written from what they do (a recipe viewer asks
 * whether a typed character goes into a text field, D-174; Mezz Config GUI's fields, D-170).
 */
public class StringUtil {
    private StringUtil() {
    }

    /** Whether chat and text fields take the character: not a control character, nor the section sign. */
    public static boolean isAllowedChatCharacter(int c) {
        return c != 167 && c >= 32 && c != 127;
    }

    /** The text with the characters chat doesn't take left out. */
    public static String filterText(String text) {
        return filterText(text, false);
    }

    /** The text with the characters chat doesn't take left out; line breaks kept when {@code multiline}. */
    public static String filterText(String text, boolean multiline) {
        StringBuilder kept = new StringBuilder();
        text.codePoints().forEach(c -> {
            if (isAllowedChatCharacter(c) || (multiline && c == '\n')) {
                kept.appendCodePoint(c);
            }
        });
        return kept.toString();
    }

    public static boolean isNullOrEmpty(String text) {
        return text == null || text.isEmpty();
    }

    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** The text without its formatting codes (a section sign and the character after it). */
    public static String stripColor(String text) {
        return text.replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
    }

    /** The text cut to {@code max} characters, with "..." in place of the end when {@code ellipsis}. */
    public static String truncateStringIfNecessary(String text, int max, boolean ellipsis) {
        if (text.length() <= max) {
            return text;
        }
        return ellipsis && max > 3 ? text.substring(0, max - 3) + "..." : text.substring(0, max);
    }

    /** How many lines the text has. */
    public static int lineCount(String text) {
        return text.isEmpty() ? 0 : (int) text.chars().filter(c -> c == '\n').count() + 1;
    }
}
