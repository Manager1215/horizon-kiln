package net.fabricmc.fabric.api.client.keymapping.v1;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;

/** Kiln's stand-in for Fabric's {@code KeyMappingHelper}: a mod's keys, which Kiln feeds the game's key presses. */
public final class KeyMappingHelper {
    private static final List<KeyMapping> KEYS = new CopyOnWriteArrayList<>();

    private KeyMappingHelper() {}

    public static KeyMapping registerKeyMapping(KeyMapping key) {
        KEYS.add(key);
        return key;
    }

    /** Kiln's own: the key {@code name} ({@code key.keyboard.j}) went down while playing. */
    public static void kiln$pressed(String name) {
        for (KeyMapping key : KEYS) {
            if (name.equals(key.kiln$key())) {
                key.kiln$press();
            }
        }
    }

    /** How many of {@link #KEYS} the game has heard of. */
    private static int announced;

    /**
     * Kiln's own: tells the game the keys registered since the last call, each its name, its group's name, what
     * Controls call it and the key the mod set, so that the player can choose another in Options > Mods. {@code owner}
     * is the entrypoint class that just ran (they are its mod's), or empty for keys registered later. With an owner it
     * is said even with no keys: that mod has none (any longer).
     */
    public static synchronized void kiln$announce(String owner) {
        if (owner.isEmpty() && announced == KEYS.size()) {
            return;
        }
        List<String> fields = new java.util.ArrayList<>();
        fields.add(owner);
        for (; announced < KEYS.size(); announced++) {
            KeyMapping key = KEYS.get(announced);
            fields.add(key.getName());
            fields.add(key.kiln$categoryLabel());
            fields.add(key.kiln$label());
            fields.add(key.kiln$defaultKey());
        }
        kiln.Native.keys(fields);
    }
}
