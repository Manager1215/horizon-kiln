package net.minecraft.client.gui.components;

/** Kiln's stand-in for {@code DebugScreenOverlay}, the F3 screen: open or not, as the game said with the tick. */
public class DebugScreenOverlay {
    private boolean shown;

    public boolean showDebugScreen() {
        return shown;
    }

    /** Kiln's own: F3, as the game said. */
    public void kiln$shown(boolean shown) {
        this.shown = shown;
    }
}
