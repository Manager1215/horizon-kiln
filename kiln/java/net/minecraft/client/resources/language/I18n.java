package net.minecraft.client.resources.language;

/** Kiln's stand-in for {@code I18n}: the game's language's text for a key, as the game shows it. */
public class I18n {
    protected I18n() {}

    /**
     * {@code key}'s text in the language the game is in (the mods' translations above the game's), its arguments
     * filled in; the key itself when no language has it, as vanilla's.
     */
    public static String get(String key, Object... args) {
        return kiln.Language.format(key, args == null ? new Object[0] : args);
    }

    public static boolean exists(String key) {
        return kiln.Language.has(key);
    }
}
