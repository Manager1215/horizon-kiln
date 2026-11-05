package net.minecraft.client.gui;

/** Kiln's stand-in for {@code Hud}: whether the game's HUD shows (F1 hides it), and its chat. */
public class Hud {
    private final net.minecraft.client.gui.components.ChatComponent chat =
            new net.minecraft.client.gui.components.ChatComponent();
    private boolean hidden;

    public net.minecraft.client.gui.components.ChatComponent getChat() {
        return chat;
    }

    /** A line over the hotbar; Kiln shows it in chat, as a player's overlay message. */
    public void setOverlayMessage(net.minecraft.network.chat.Component message, boolean animate) {
        kiln.Native.chat(message.getString());
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Kiln's own: F1, as the game said. */
    public void kiln$hidden(boolean hidden) {
        this.hidden = hidden;
    }
}
