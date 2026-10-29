package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code KeyMapping}: a mod's key. Kiln hears the game's key presses while playing and
 * counts them for the mappings on that key; the mod takes them with {@link #consumeClick()}.
 */
public class KeyMapping implements Comparable<KeyMapping> {
    private final String name;
    private final InputConstants.Type type;
    private final int defaultKey;
    private final Category category;
    private int clicks;

    public KeyMapping(String name, int keyCode, Category category) {
        this(name, InputConstants.Type.KEYBOARD, keyCode, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, Category category) {
        this.name = name;
        this.type = type;
        this.defaultKey = keyCode;
        this.category = category;
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, Category category, int order) {
        this(name, type, keyCode, category);
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    /** True once for each press since the last call. */
    public boolean consumeClick() {
        if (clicks == 0) {
            return false;
        }
        clicks--;
        return true;
    }

    /** Kiln's own: the key's name ({@code key.keyboard.j}). */
    public String kiln$key() {
        return InputConstants.kiln$name(type, defaultKey);
    }

    /** Kiln's own: the key went down. */
    public void kiln$press() {
        clicks++;
    }

    @Override
    public int compareTo(KeyMapping o) {
        return name.compareTo(o.name);
    }

    /** A group of keys in Controls. */
    public record Category(Identifier id) {
        public static Category register(Identifier id) {
            return new Category(id);
        }
    }
}
