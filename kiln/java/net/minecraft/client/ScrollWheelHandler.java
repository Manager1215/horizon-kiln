package net.minecraft.client;

/**
 * Kiln's stand-in for {@code ScrollWheelHandler}: the wheel's notches as the hotbar's next slot. Horizon turns the
 * player's own wheel; a mod's key that moves the selection uses the same arithmetic.
 */
public class ScrollWheelHandler {
    public ScrollWheelHandler() {}

    public org.joml.Vector2i onMouseScroll(double x, double y) {
        kiln.Missing.called("net.minecraft.client.ScrollWheelHandler.onMouseScroll");
        return null;
    }

    /** The slot {@code amount} notches from {@code selected} among {@code count}, round the ends, as vanilla's. */
    public static int getNextScrollWheelSelection(double amount, int selected, int count) {
        int step = (int) Math.signum(amount);
        return Math.floorMod(selected - step, count);
    }
}
