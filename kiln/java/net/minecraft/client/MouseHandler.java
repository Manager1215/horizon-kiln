package net.minecraft.client;

import com.mojang.blaze3d.platform.Window;

/**
 * Kiln's stand-in for {@code MouseHandler}: the pointer as the window's game has it each tick: where it is in the
 * window's pixels, the buttons held, and whether the game holds it (playing, no screen open). Read only: a mod can't
 * grab or move it.
 */
public class MouseHandler {
    private double xpos;
    private double ypos;
    private boolean left;
    private boolean middle;
    private boolean right;
    private boolean grabbed;

    public MouseHandler(Minecraft minecraft) {}

    public double xpos() {
        return xpos;
    }

    public double ypos() {
        return ypos;
    }

    public boolean isLeftPressed() {
        return left;
    }

    public boolean isMiddlePressed() {
        return middle;
    }

    public boolean isRightPressed() {
        return right;
    }

    public boolean isMouseGrabbed() {
        return grabbed;
    }

    /** The pointer's x in GUI pixels. */
    public double getScaledXPos(Window window) {
        return getScaledXPos(window, xpos);
    }

    public double getScaledYPos(Window window) {
        return getScaledYPos(window, ypos);
    }

    public static double getScaledXPos(Window window, double x) {
        return x * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
    }

    public static double getScaledYPos(Window window, double y) {
        return y * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
    }

    /**
     * Kiln's own: the pointer as the game said, {@code x,y} in GUI pixels (to the window's by its GUI scale), and the
     * buttons and grab, {@code left,middle,right,grabbed} as 1 or 0.
     */
    public void kiln$set(String cursor, String mouse, int scale) {
        String[] c = cursor.split(",");
        if (c.length == 2) {
            try {
                xpos = Double.parseDouble(c[0]) * scale;
                ypos = Double.parseDouble(c[1]) * scale;
            } catch (NumberFormatException e) {
                // As it was.
            }
        }
        String[] m = mouse.split(",");
        if (m.length == 4) {
            left = m[0].equals("1");
            middle = m[1].equals("1");
            right = m[2].equals("1");
            grabbed = m[3].equals("1");
        }
    }
}
