package net.minecraft.client.gui.screens;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code ChatScreen}: the game's chat box, which the game draws. */
public class ChatScreen extends Screen {
    public ChatScreen(String initial, boolean isDraft) {
        this(initial, isDraft, true);
    }

    public ChatScreen(String initial, boolean isDraft, boolean closeOnSubmit) {
        super(Component.translatable("chat_screen.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
