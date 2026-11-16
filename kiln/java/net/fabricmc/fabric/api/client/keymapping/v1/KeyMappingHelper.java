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
        key.kiln$registered();
        // And among the game's options' mappings, as Fabric adds it there.
        net.minecraft.client.Minecraft.getInstance().options.kiln$add(key);
        return key;
    }

    /** The key it is bound to now: the player's choice, else the mod's. */
    public static com.mojang.blaze3d.platform.InputConstants.Key getBoundKeyOf(KeyMapping key) {
        return com.mojang.blaze3d.platform.InputConstants.getKey(key.kiln$key());
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
        // The keys the mods set themselves since the last call, with their modifiers (D-178).
        KeyMapping.kiln$flushChosen();
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
            // With its modifiers for a mapping that keeps them itself (JEI's Ctrl+O: control+key.keyboard.o, D-178).
            fields.add(key.kiln$combo(true));
        }
        kiln.Native.keys(fields);
    }
}
