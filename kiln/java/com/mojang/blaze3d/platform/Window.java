package com.mojang.blaze3d.platform;

/**
 * Kiln's stand-in for {@code Window}, the game's window, as HUD mods read it: its size in its own pixels, the GUI
 * scale, and the GUI's size in GUI pixels, which Kiln has from the game each tick. Until the game says, the window is
 * the GUI at a scale of 1.
 */
public class Window {
    private int guiWidth = 427;
    private int guiHeight = 240;
    private int width;
    private int height;
    private int guiScale;

    public int getGuiScaledWidth() {
        return guiWidth;
    }

    public int getGuiScaledHeight() {
        return guiHeight;
    }

    /** The window's width in its pixels. */
    public int getWidth() {
        return width > 0 ? width : guiWidth * getGuiScale();
    }

    public int getHeight() {
        return height > 0 ? height : guiHeight * getGuiScale();
    }

    /** As Horizon counts them, the window's pixels are the screen's. */
    public int getScreenWidth() {
        return getWidth();
    }

    public int getScreenHeight() {
        return getHeight();
    }

    /** Window pixels to a GUI pixel. */
    public int getGuiScale() {
        return guiScale > 0 ? guiScale : 1;
    }

    /** Kiln's own: the GUI's size, as the game said. */
    public void kiln$gui(int width, int height) {
        guiWidth = width;
        guiHeight = height;
    }

    /** Kiln's own: the window's size and GUI scale, as the game said ({@code width,height,scale}; 0 when unknown). */
    public void kiln$window(String field) {
        String[] f = field.split(",");
        if (f.length != 3) {
            return;
        }
        try {
            width = Integer.parseInt(f[0]);
            height = Integer.parseInt(f[1]);
            guiScale = Integer.parseInt(f[2]);
        } catch (NumberFormatException e) {
            width = height = guiScale = 0;
        }
    }
}
