package net.minecraft.client.gui.components;

/** Kiln's stand-in for {@code DebugScreenOverlay}, the F3 screen. Kiln doesn't know whether it is open: never. */
public class DebugScreenOverlay {
    public boolean showDebugScreen() {
        return false;
    }
}
