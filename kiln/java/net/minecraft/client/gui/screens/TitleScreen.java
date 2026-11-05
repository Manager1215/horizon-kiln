package net.minecraft.client.gui.screens;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code TitleScreen}: the game's title screen, which the game draws. */
public class TitleScreen extends Screen {
    public TitleScreen() {
        this(false);
    }

    public TitleScreen(boolean fading) {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
