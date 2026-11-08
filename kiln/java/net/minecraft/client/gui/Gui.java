package net.minecraft.client.gui;

import net.minecraft.client.gui.screens.Screen;

/** Kiln's stand-in for {@code Gui}: Horizon's screens. Kiln can't show a mod's screen yet: opening one is logged. */
public class Gui {
    public final Hud hud;

    public Gui(net.minecraft.client.Minecraft minecraft) {
        hud = new Hud(minecraft);
    }

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

    /**
     * The screen shown: the window's game's, set as the game opens or closes one ({@link kiln.Screens}), the one
     * before removed and the new one made ready, with Fabric's screen events. A mod's own screen isn't shown yet.
     */
    public void setScreen(Screen screen) {
        kiln.Screens.set(screen);
    }
}
