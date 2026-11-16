package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Kiln's stand-in for Mod Menu's {@code ConfigScreenFactory}: a mod's settings screen, made over {@code parent}. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
