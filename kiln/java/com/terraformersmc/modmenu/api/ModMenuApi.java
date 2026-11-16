package com.terraformersmc.modmenu.api;

import java.util.Map;

/**
 * Kiln's stand-in for Mod Menu's {@code ModMenuApi}: a mod's {@code modmenu} entrypoint, whose config screen Kiln opens
 * from the mod's row in the game's Options > Mods ({@link kiln.Settings}).
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }
}
