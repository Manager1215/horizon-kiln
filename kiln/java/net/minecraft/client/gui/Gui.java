package net.minecraft.client.gui;

import kiln.Native;
import net.minecraft.client.gui.screens.Screen;

/** Kiln's stand-in for {@code Gui}: Horizon's screens. Kiln can't show a mod's screen yet: opening one is logged. */
public class Gui {
    /** The screen on top; Kiln's mods never have one. */
    public Screen screen() {
        return null;
    }

    public void setScreen(Screen screen) {
        if (screen != null) {
            Native.log("Kiln can't show mods' screens yet: " + screen.getClass().getName() + " stays closed");
        }
    }
}
