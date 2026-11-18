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
    /** The key it is bound to, as the game's field (mods' mixins read it): the player's choice, else the mod's. */
    private InputConstants.Key key;

    public KeyMapping(String name, int keyCode, Category category) {
        this(name, InputConstants.Type.KEYBOARD, keyCode, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, Category category) {
        this.name = name;
        this.type = type;
        this.defaultKey = keyCode;
        this.category = category;
        this.key = InputConstants.getKey(kiln$defaultKey());
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
            // Through its setKey, as vanilla's options give a mapping its key: a mod's mapping that keeps its key
            // itself (JEI's, which hides it from vanilla's table) hears it so. The game's own choice: not told back.
            // A combo (`control+key.keyboard.o`, D-178) gives a mapping that keeps modifiers itself its modifiers too.
            GAME_CHOICE.set(Boolean.TRUE);
            try {
                int plus = key.lastIndexOf('+');
                String keyName = plus >= 0 ? key.substring(plus + 1) : key;
                InputConstants.Key bound = keyName.isEmpty() ? mapping.getDefaultKey() : InputConstants.getKey(keyName);
                if (plus >= 0 && mapping.kiln$setKeyAndModifiers(bound, key.substring(0, plus))) {
                    return;
                }
                mapping.setKey(bound);
            } finally {
                GAME_CHOICE.remove();
            }
        }
    }

    // --- The modifiers a mapping keeps itself (D-178) ---------------------------------------------------------------
    //
    // JEI's FabricKeyMapping keeps its key and a set of modifiers (Ctrl+O toggles its list) in fields of its own: its
    // getModifiers, getDefaultModifiers and setKeyAndModifiers take a Set of an enum whose constants are SHIFT,
    // CONTROL, ALT and COMMAND. Kiln reads and sets them by those names, so the page in Options > Mods shows and binds
    // the whole combination. Between Java and the game a combination is written `control+shift+key.keyboard.o` (the
    // modifiers in that order: control, shift, alt, command), `+key.keyboard.r` for one with none; a mapping without
    // modifiers of its own is its key's name alone, as before.

    /** The modifiers' names in a combination, in their order there, and the enum constants they are. */
    private static final String[][] MODIFIERS = {
        {"control", "CONTROL"}, {"shift", "SHIFT"}, {"alt", "ALT"}, {"command", "COMMAND"},
    };

    /** A public method of the mapping's own class by name and parameter count, or null. */
    private java.lang.reflect.Method kiln$method(String name, int parameters) {
        for (java.lang.reflect.Method m : getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters
                    && m.getDeclaringClass() != KeyMapping.class) {
                return m;
            }
        }
        return null;
    }

    /** Whether it keeps modifiers itself (JEI's mappings). */
    public boolean kiln$hasModifiers() {
        java.lang.reflect.Method get = kiln$method("getModifiers", 0);
        return get != null && java.util.Set.class.isAssignableFrom(get.getReturnType())
                && kiln$method("setKeyAndModifiers", 2) != null && kiln$modifierType() != null;
    }

    /** The enum its modifiers are, read off getModifiers' return type ({@code Set<FabricKeyModifier>}), or null. */
    @SuppressWarnings("rawtypes")
    private Class<? extends Enum> kiln$modifierType() {
        java.lang.reflect.Method get = kiln$method("getModifiers", 0);
        if (get != null && get.getGenericReturnType() instanceof java.lang.reflect.ParameterizedType p
                && p.getActualTypeArguments().length == 1 && p.getActualTypeArguments()[0] instanceof Class<?> c
                && c.isEnum()) {
            return c.asSubclass(Enum.class);
        }
        return null;
    }

    /** The modifiers in a set the mapping gave, as a combination's prefix ({@code control+shift}). */
    private static String kiln$modifierNames(Object set) {
        StringBuilder out = new StringBuilder();
        if (set instanceof java.util.Set<?> s) {
            for (String[] m : MODIFIERS) {
                if (s.stream().anyMatch(e -> e instanceof Enum<?> en && en.name().equals(m[1]))) {
                    if (out.length() > 0) {
                        out.append('+');
                    }
                    out.append(m[0]);
                }
            }
        }
        return out.toString();
    }

    /**
     * Kiln's own: the combination it is bound to now ({@code default} false) or the mod's own ({@code true}), for a
     * mapping that keeps modifiers itself ({@code control+key.keyboard.o}); for any other its key's name.
     */
    public String kiln$combo(boolean defaults) {
        String key = defaults ? kiln$defaultKey() : saveString();
        if (!kiln$hasModifiers()) {
            return key;
        }
        try {
            Object set = kiln$method(defaults ? "getDefaultModifiers" : "getModifiers", 0).invoke(this);
            return kiln$modifierNames(set) + "+" + key;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return key;
        }
    }

    /**
     * Its key and modifiers ({@code control+shift}, or "" for none) set at once, through its own setKeyAndModifiers;
     * false for a mapping that keeps no modifiers itself (its setKey then).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean kiln$setKeyAndModifiers(InputConstants.Key key, String modifiers) {
        if (!kiln$hasModifiers()) {
            return false;
        }
        Class<? extends Enum> type = kiln$modifierType();
        java.util.Set<Object> set = new java.util.HashSet<>();
        for (String name : modifiers.split("\\+")) {
            for (String[] m : MODIFIERS) {
                if (m[0].equals(name)) {
                    try {
                        set.add(Enum.valueOf(type, m[1]));
                    } catch (IllegalArgumentException e) {
                        // Its enum has no such modifier (COMMAND off macOS in another mod's): left out.
                    }
                }
            }
        }
        try {
            kiln$method("setKeyAndModifiers", 2).invoke(this, key, set);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The mappings set by their mod since the game last heard ({@link #kiln$flushChosen}), by name. */
    private static final java.util.Set<KeyMapping> CHANGED = new java.util.LinkedHashSet<>();

    /**
     * Kiln's own: tells the game the keys the mods set themselves since the last call, each as the combination it is
     * now. Told after the mod's call rather than in setKey: JEI sets the key, then the modifiers.
     */
    public static void kiln$flushChosen() {
        java.util.List<KeyMapping> changed;
        synchronized (CHANGED) {
            if (CHANGED.isEmpty()) {
                return;
            }
            changed = new java.util.ArrayList<>(CHANGED);
            CHANGED.clear();
        }
        for (KeyMapping mapping : changed) {
            kiln.Native.keyChosen(mapping.name, mapping.kiln$combo(false));
        }
    }

    /** Set while the game gives a mapping the player's choice ({@link #kiln$bind}). */
    private static final ThreadLocal<Boolean> GAME_CHOICE = new ThreadLocal<>();

    /** Whether a mod registered it ({@code KeyMappingHelper.registerKeyMapping}): only then has it a page's row. */
    private volatile boolean registered;

    /** Kiln's own: a mod registered it; what it sets itself from now is the player's choice on its page. */
    public void kiln$registered() {
        registered = true;
    }

    /**
     * A mod set the key itself (its own key screen, a library's config field, a preset): its presses and
     * {@link #isDown()} follow it from now, and the game keeps it as the player's choice on the mod's page in Options
     * > Mods ({@code horizon-mods.txt}), as Minecraft keeps it in {@code options.txt}. The mod's own key again: the
     * game forgets the choice. The key kept is what the mapping saves ({@link #saveString()}, as vanilla's options
     * file writes it): a mapping that keeps its key itself and sets this one to none (JEI's, to stay out of vanilla's
     * table) is kept with its own. Before the mod registers it (its constructor), nothing is told: a choice made
     * earlier would go.
     */
    public void setKey(InputConstants.Key key) {
        this.key = key;
        String chosen = saveString();
        bound = chosen.equals(kiln$defaultKey()) ? null : chosen;
        if (registered && GAME_CHOICE.get() == null) {
            if (kiln$hasModifiers()) {
                // Told with the tick (kiln$flushChosen), with the modifiers the mod sets after the key (D-178).
                synchronized (CHANGED) {
                    CHANGED.add(this);
                }
            } else {
                kiln.Native.keyChosen(name, chosen);
            }
        }
    }

    /**
     * The game makes its table of mappings by key again after keys changed. Kiln has none to make: a press finds its
     * mappings by the key's name each time.
     */
    public static void resetMapping() {}

    /** Whether the key pressed ({@code KeyEvent}, by its code) is the one it is bound to. */
    public boolean matches(net.minecraft.client.input.KeyEvent event) {
        return key.getType() == InputConstants.Type.KEYBOARD
                && key.getValue() != InputConstants.UNKNOWN.getValue()
                && key.getValue() == event.key();
    }

    /** Whether the mouse button pressed is the one it is bound to. */
    public boolean matchesMouse(net.minecraft.client.input.MouseButtonEvent event) {
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == event.button();
    }

    /** The key's name as the game shows it ({@code J}, {@code Left Shift}). */
    public net.minecraft.network.chat.Component getTranslatedKeyMessage() {
        return net.minecraft.network.chat.Component.translatable(key.getName());
    }

    /** Whether it has no key. */
    public boolean isUnbound() {
        return key.equals(InputConstants.UNKNOWN);
    }

    /** The key the mod set. */
    public InputConstants.Key getDefaultKey() {
        return InputConstants.getKey(kiln$defaultKey());
    }

    /** Whether the player kept the mod's key. */
    public boolean isDefault() {
        return key.equals(getDefaultKey());
    }

    /** The key as the options file writes it ({@code key.keyboard.j}). */
    public String saveString() {
        return key.getName();
    }

    /** Whether both are bound to the same key. */
    public boolean same(KeyMapping other) {
        return key.equals(other.key);
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

        /** What Controls call the group ({@code key.category.<namespace>.<path>}). */
        public net.minecraft.network.chat.Component label() {
            return net.minecraft.network.chat.Component.translatable(
                    "key.category." + id.getNamespace() + "." + id.getPath());
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean isDown;
    public static void click(com.mojang.blaze3d.platform.InputConstants.Key p0) { kiln.Missing.called("net.minecraft.client.KeyMapping.click"); }
    public static void set(com.mojang.blaze3d.platform.InputConstants.Key p0, boolean p1) { kiln.Missing.called("net.minecraft.client.KeyMapping.set"); }
    public static void setAll() { kiln.Missing.called("net.minecraft.client.KeyMapping.setAll"); }
    public static void releaseAll() { kiln.Missing.called("net.minecraft.client.KeyMapping.releaseAll"); }
    public static void resetToggleKeys() { kiln.Missing.called("net.minecraft.client.KeyMapping.resetToggleKeys"); }
    public void release() { kiln.Missing.called("net.minecraft.client.KeyMapping.release"); }
    public static java.util.function.Supplier<net.minecraft.network.chat.Component> createNameSupplier(java.lang.String p0) { kiln.Missing.called("net.minecraft.client.KeyMapping.createNameSupplier"); return kiln.Constants.nothing(java.util.function.Supplier.class); }
    public boolean matches(com.mojang.blaze3d.platform.InputConstants.Key p0) { kiln.Missing.called("net.minecraft.client.KeyMapping.matches"); return false; }
    public void setDown(boolean p0) { kiln.Missing.called("net.minecraft.client.KeyMapping.setDown"); }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
