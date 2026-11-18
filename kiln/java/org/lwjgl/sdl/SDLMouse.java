package org.lwjgl.sdl;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;

/**
 * Kiln's stand-in for LWJGL's {@code SDLMouse}: the game's window is Horizon's, so a mod that asks SDL where the pointer
 * is and which buttons are held is answered from Kiln's mouse (REI's search field asks it, D-215). Positions are in
 * the window's pixels, as SDL's.
 */
public final class SDLMouse {
    public static final int SDL_BUTTON_LEFT = 1;
    public static final int SDL_BUTTON_MIDDLE = 2;
    public static final int SDL_BUTTON_RIGHT = 3;
    public static final int SDL_BUTTON_LMASK = 1;
    public static final int SDL_BUTTON_MMASK = 1 << 1;
    public static final int SDL_BUTTON_RMASK = 1 << 2;

    private SDLMouse() {}

    /** The buttons held, as SDL's mask, and the pointer into {@code x} and {@code y} when given. */
    public static int SDL_GetMouseState(FloatBuffer x, FloatBuffer y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null) {
            return 0;
        }
        if (x != null) {
            x.put(x.position(), (float) mc.mouseHandler.xpos());
        }
        if (y != null) {
            y.put(y.position(), (float) mc.mouseHandler.ypos());
        }
        return (mc.mouseHandler.isLeftPressed() ? SDL_BUTTON_LMASK : 0)
                | (mc.mouseHandler.isMiddlePressed() ? SDL_BUTTON_MMASK : 0)
                | (mc.mouseHandler.isRightPressed() ? SDL_BUTTON_RMASK : 0);
    }
}
