package net.minecraft.client.gui.screens;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code PauseScreen}: the game's menu (Escape), which the game draws. */
public class PauseScreen extends Screen {
    private final boolean showPauseMenu;

    public PauseScreen(boolean showPauseMenu) {
        super(showPauseMenu ? Component.translatable("menu.game") : Component.translatable("menu.paused"));
        this.showPauseMenu = showPauseMenu;
    }

    public boolean showsPauseMenu() {
        return showPauseMenu;
    }
}
