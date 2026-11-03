package net.minecraft.world.entity.player;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;

/** Kiln's stand-in for {@code Player}. */
public abstract class Player extends Avatar {
    /** Survival 0, creative 1, adventure 2, spectator 3. */
    protected int gameMode;

    public void sendSystemMessage(Component message) {
        kiln$effect("tell " + message.kiln$json());
    }

    /** A line over the hotbar (the action bar). */
    public void sendOverlayMessage(Component message) {
        kiln$effect("overlay " + message.kiln$json());
    }

    public boolean isCreative() {
        return gameMode == 1;
    }

    public boolean isSpectator() {
        return gameMode == 3;
    }
}
