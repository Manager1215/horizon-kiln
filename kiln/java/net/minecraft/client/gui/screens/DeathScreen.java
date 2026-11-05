package net.minecraft.client.gui.screens;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code DeathScreen}: the game's "You Died!" screen, which the game draws. */
public class DeathScreen extends Screen {
    private final boolean hardcore;

    public DeathScreen(Component causeOfDeath, boolean hardcore, LocalPlayer player) {
        super(Component.translatable(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.hardcore = hardcore;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
