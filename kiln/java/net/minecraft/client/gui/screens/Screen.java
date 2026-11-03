package net.minecraft.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code Screen}, so that mods with screens load; Kiln doesn't show them yet
 * ({@link net.minecraft.client.gui.Gui#setScreen}).
 */
public abstract class Screen {
    protected final Component title;

    protected Screen(Component title) {
        this.title = title;
    }

    protected Screen(Minecraft minecraft, Font font, Component title) {
        this(title);
    }

    public Component getTitle() {
        return title;
    }

    protected void init() {}

    public void onClose() {}
}
