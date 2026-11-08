package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code KeyMapping}: a mod's key. Kiln hears the game's key presses while playing and
 * counts them for the mappings on that key; the mod takes them with {@link #consumeClick()}.
 */
public class KeyMapping implements Comparable<KeyMapping> {
    /** Every mapping made, by name, as the game keeps them (mods reach it through an accessor). */
    private static final java.util.Map<String, KeyMapping> ALL = new java.util.HashMap<>();
    private final String name;
    private final InputConstants.Type type;
    private final int defaultKey;
    private final Category category;
    private int clickCount;

    public KeyMapping(String name, int keyCode, Category category) {
        this(name, InputConstants.Type.KEYBOARD, keyCode, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, Category category) {
        this.name = name;
        this.type = type;
        this.defaultKey = keyCode;
        this.category = category;
        synchronized (ALL) {
            ALL.put(name, this);
        }
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
        if (clickCount == 0) {
            return false;
        }
        clickCount--;
        return true;
    }

    /** The key the player chose for it in the game, by name; null while it is the mod's own. */
    private volatile String bound;

    /** Kiln's own: the key's name ({@code key.keyboard.j}): the player's choice, else the mod's. */
    public String kiln$key() {
        String chosen = bound;
        return chosen != null ? chosen : kiln$defaultKey();
    }

    /** Kiln's own: the key the mod set, by name; {@code key.keyboard.unknown} for a code no key has. */
    public String kiln$defaultKey() {
        String name = InputConstants.kiln$name(type, defaultKey);
        return name != null ? name : InputConstants.UNKNOWN.getName();
    }

    /** Kiln's own: what the game's Controls call it, in the language the mods' text is in. */
    public String kiln$label() {
        return kiln.Language.get(name);
    }

    /** Kiln's own: what the game calls its group of keys ({@code key.category.<namespace>.<path>}). */
    public String kiln$categoryLabel() {
        Identifier id = category.id();
        String key = "key.category." + id.getNamespace() + "." + id.getPath();
        return kiln.Language.has(key) ? kiln.Language.get(key) : id.toString();
    }

    /**
     * Kiln's own: the player chose {@code key} ({@code key.keyboard.b}) for the mapping called {@code name}, in the
     * game's Options > Mods; its presses and {@link #isDown()} follow it from now. No mapping of that name: nothing.
     */
    public static void kiln$bind(String name, String key) {
        KeyMapping mapping;
        synchronized (ALL) {
            mapping = ALL.get(name);
        }
        if (mapping != null) {
            mapping.bound = key.isEmpty() || key.equals(mapping.kiln$defaultKey()) ? null : key;
        }
    }

    /** Kiln's own: the key went down. */
    public void kiln$press() {
        clickCount++;
    }

    /** The keys held down now, by name, as the game said with the tick. */
    private static volatile java.util.Set<String> held = java.util.Set.of();

    /** Whether its key is held down now. */
    public boolean isDown() {
        String key = kiln$key();
        return key != null && held.contains(key);
    }

    /** Kiln's own: whether the key named so ({@code key.keyboard.left.shift}) is held down now. */
    public static boolean kiln$isHeld(String key) {
        return held.contains(key);
    }

    /** Kiln's own: the keys the game says are held. */
    public static void kiln$held(java.util.Set<String> keys) {
        held = java.util.Set.copyOf(keys);
    }

    @Override
    public int compareTo(KeyMapping o) {
        return name.compareTo(o.name);
    }

    /** A group of keys in Controls. */
    public record Category(Identifier id) {
        public static final Category MOVEMENT = new Category(Identifier.withDefaultNamespace("movement"));
        public static final Category MISC = new Category(Identifier.withDefaultNamespace("misc"));
        public static final Category MULTIPLAYER = new Category(Identifier.withDefaultNamespace("multiplayer"));
        public static final Category GAMEPLAY = new Category(Identifier.withDefaultNamespace("gameplay"));
        public static final Category INVENTORY = new Category(Identifier.withDefaultNamespace("inventory"));
        public static final Category CREATIVE = new Category(Identifier.withDefaultNamespace("creative"));
        public static final Category SPECTATOR = new Category(Identifier.withDefaultNamespace("spectator"));
        public static final Category DEBUG = new Category(Identifier.withDefaultNamespace("debug"));

        public static Category register(Identifier id) {
            return new Category(id);
        }
    }
}
