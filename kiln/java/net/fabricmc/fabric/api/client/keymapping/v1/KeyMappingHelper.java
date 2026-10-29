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
}
