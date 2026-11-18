package net.minecraft.client;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

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

    private final Minecraft minecraft;

    public MouseHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

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
     * The wheel turned ({@code x} and {@code y} scaled steps). Over a screen Kiln hands the mods ({@link #kiln$scroll}),
     * the screen's {@code mouseScrolled} with Fabric's events, as vanilla's (D-215: Architectury wraps that call). While
     * playing, Horizon scrolls the hotbar; the mods' mixins in this run at Horizon's {@code mouse_scrolling} hook
     * (Kiln's Mixin bridge, D-102), after the spectator's check where vanilla's turns a spectator's flying speed.
     */
    public void onScroll(long window, double x, double y) {
        Screen screen = kiln$screen;
        double sensitivity = 1.0;
        double scrollX = x * sensitivity;
        double scrollY = y * sensitivity;
        if (screen != null) {
            if (ScreenMouseEvents.allowMouseScroll(screen).invoker().allowMouseScroll(screen, kiln$x, kiln$y, scrollX, scrollY)) {
                ScreenMouseEvents.beforeMouseScroll(screen).invoker().beforeMouseScroll(screen, kiln$x, kiln$y, scrollX, scrollY);
                boolean used = screen.mouseScrolled(kiln$x, kiln$y, scrollX, scrollY);
                ScreenMouseEvents.afterMouseScroll(screen).invoker().afterMouseScroll(screen, kiln$x, kiln$y, scrollX,
                        scrollY, used);
            }
            return;
        }
        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        boolean spectator = player != null && player.isSpectator();
        kiln.Bridge.body(null, window, x, y);
    }

    /**
     * A button pressed (action 1) or released (0). Over a screen Kiln hands the mods ({@link #kiln$button}), the
     * screen's {@code mouseClicked} or {@code mouseReleased} with Fabric's events, as vanilla's (D-215); while playing,
     * the mods' mixins in this run at {@code mouse_button_pre} (D-102).
     */
    public void onButton(long window, net.minecraft.client.input.MouseButtonInfo button, int action) {
        Screen screen = kiln$screen;
        if (screen == null) {
            kiln.Bridge.body(null, window, button, action);
            return;
        }
        if (minecraft.gui.overlay() != null) {
            return;
        }
        MouseButtonEvent event = kiln$event;
        if (action == 1) {
            if (ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, event)) {
                ScreenMouseEvents.beforeMouseClick(screen).invoker().beforeMouseClick(screen, event);
                boolean used = screen.mouseClicked(event, kiln$twice);
                ScreenMouseEvents.afterMouseClick(screen).invoker().afterMouseClick(screen, event, used);
            }
        } else if (ScreenMouseEvents.allowMouseRelease(screen).invoker().allowMouseRelease(screen, event)) {
            ScreenMouseEvents.beforeMouseRelease(screen).invoker().beforeMouseRelease(screen, event);
            boolean used = screen.mouseReleased(event);
            ScreenMouseEvents.afterMouseRelease(screen).invoker().afterMouseRelease(screen, event, used);
        }
    }

    /**
     * The pointer's movement since the last frame: over a screen Kiln hands the mods with a button held
     * ({@link #kiln$drag}), the screen's {@code mouseDragged} with Fabric's events, as vanilla's (D-215). Horizon turns
     * the camera while playing.
     */
    public void handleAccumulatedMovement() {
        Screen screen = kiln$screen;
        if (screen == null) {
            return;
        }
        MouseButtonEvent event = kiln$event;
        double dx = kiln$x;
        double dy = kiln$y;
        if (ScreenMouseEvents.allowMouseDrag(screen).invoker().allowMouseDrag(screen, event, dx, dy)) {
            ScreenMouseEvents.beforeMouseDrag(screen).invoker().beforeMouseDrag(screen, event, dx, dy);
            boolean used = screen.mouseDragged(event, dx, dy);
            ScreenMouseEvents.afterMouseDrag(screen).invoker().afterMouseDrag(screen, event, dx, dy, used);
        }
    }

    /** Kiln's own: the screen input being handed through this handler now, or null (Horizon's hooks while playing). */
    private Screen kiln$screen;
    private MouseButtonEvent kiln$event;
    private boolean kiln$twice;
    /** The pointer (a scroll's) or the movement (a drag's), in GUI pixels. */
    private double kiln$x;
    private double kiln$y;

    /** Kiln's own: a press ({@code press}) or release on {@code screen}, as vanilla's {@link #onButton} gives it. */
    public void kiln$button(Screen screen, MouseButtonEvent event, boolean doubleClick, boolean press) {
        kiln$screen = screen;
        kiln$event = event;
        kiln$twice = doubleClick;
        try {
            onButton(0L, event.buttonInfo(), press ? 1 : 0);
        } finally {
            kiln$screen = null;
            kiln$event = null;
        }
    }

    /** Kiln's own: the wheel over {@code screen} at (x, y), as vanilla's {@link #onScroll} gives it. */
    public void kiln$scroll(Screen screen, double x, double y, double scrollX, double scrollY) {
        kiln$screen = screen;
        kiln$x = x;
        kiln$y = y;
        try {
            onScroll(0L, scrollX, scrollY);
        } finally {
            kiln$screen = null;
        }
    }

    /** Kiln's own: a drag over {@code screen} by (dx, dy), as vanilla's {@link #handleAccumulatedMovement} gives it. */
    public void kiln$drag(Screen screen, MouseButtonEvent event, double dx, double dy) {
        kiln$screen = screen;
        kiln$event = event;
        kiln$x = dx;
        kiln$y = dy;
        try {
            handleAccumulatedMovement();
        } finally {
            kiln$screen = null;
            kiln$event = null;
        }
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.MouseHandler.LastClick lastClick;
    public int lastClickButton;
    public net.minecraft.client.input.MouseButtonInfo activeButton;
    private double accumulatedDX;
    private double accumulatedDY;
    private double lastHandleMovementTime;
    private boolean mouseGrabbed;
    public void onMove(long p0, double p1, double p2, double p3, double p4) { kiln.Missing.called("net.minecraft.client.MouseHandler.onMove"); }
    public void setIgnoreFirstMove() { kiln.Missing.called("net.minecraft.client.MouseHandler.setIgnoreFirstMove"); }
    public void grabMouse() { kiln.Missing.called("net.minecraft.client.MouseHandler.grabMouse"); }
    public void releaseMouse() { kiln.Missing.called("net.minecraft.client.MouseHandler.releaseMouse"); }
    public static record LastClick(long time, net.minecraft.client.gui.screens.Screen screen) {
    }
    // End of what tools/kiln-stubs.py generated.
}
