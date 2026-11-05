package net.minecraft.locale;

/**
 * Kiln's stand-in for {@code Language}: the text for a translation key, in the language the game shows, the
 * game's own and the mods' ({@code kiln.Language} has them).
 */
public abstract class Language {
    private static final Language INSTANCE = new Language() {
        @Override
        public String getOrDefault(String key, String fallback) {
            return kiln.Language.has(key) ? kiln.Language.get(key) : fallback;
        }

        @Override
        public boolean has(String key) {
            return kiln.Language.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }
    };

    public static Language getInstance() {
        return INSTANCE;
    }

    public String getOrDefault(String key) {
        return getOrDefault(key, key);
    }

    public abstract String getOrDefault(String key, String fallback);

    public abstract boolean has(String key);

    public abstract boolean isDefaultRightToLeft();
}
