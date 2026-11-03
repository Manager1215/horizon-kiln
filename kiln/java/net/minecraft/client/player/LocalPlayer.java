package net.minecraft.client.player;

import kiln.Native;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code LocalPlayer}: the player in Horizon's window. */
public class LocalPlayer extends Player {
    /** A line in the player's chat, shown only to them. */
    @Override
    public void sendSystemMessage(Component message) {
        Native.chat(message.getString());
    }

    /** A line over the hotbar; Kiln shows it in chat. */
    @Override
    public void sendOverlayMessage(Component message) {
        Native.chat(message.getString());
    }

    @Override
    public Level level() {
        return Minecraft.getInstance().level;
    }
}
