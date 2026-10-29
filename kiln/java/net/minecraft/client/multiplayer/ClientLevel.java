package net.minecraft.client.multiplayer;

import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code ClientLevel}: the dimension the player is in. */
public class ClientLevel extends Level {
    @Override
    public long getGameTime() {
        return super.getGameTime();
    }
}
