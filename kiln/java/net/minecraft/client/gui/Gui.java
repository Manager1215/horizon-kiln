package net.minecraft.client.gui;

import kiln.Native;
import net.minecraft.client.gui.screens.Screen;

/** Kiln's stand-in for {@code Gui}: Horizon's screens. Kiln can't show a mod's screen yet: opening one is logged. */
public class Gui {
    public final Hud hud = new Hud();

    private final net.minecraft.client.gui.components.toasts.ToastManager toasts =
            new net.minecraft.client.gui.components.toasts.ToastManager();

    /** The screen on top in the window's game, as it said with the tick; none while playing. */
    public Screen screen() {
        return kiln.Screens.open();
    }

    /** The game's toasts. */
    public net.minecraft.client.gui.components.toasts.ToastManager toastManager() {
        return toasts;
    }

    public void setScreen(Screen screen) {
        if (screen != null) {
            Native.log("Kiln can't show mods' screens yet: " + screen.getClass().getName() + " stays closed");
        }
    }
}
