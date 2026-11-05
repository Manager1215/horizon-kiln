package net.minecraft.util;

/** Kiln's stand-in for {@code ARGB}: a colour as one int, alpha in the top byte. */
public class ARGB {
    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    /** Opaque. */
    public static int color(int red, int green, int blue) {
        return color(255, red, green, blue);
    }

    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    public static int transparent(int color) {
        return color & 0x00FFFFFF;
    }

    public static int color(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | rgb & 0x00FFFFFF;
    }

    public static int white(float alpha) {
        return color(Math.round(alpha * 255.0F), 0xFFFFFF);
    }

    public static int multiplyAlpha(int color, float factor) {
        return color(Math.round(alpha(color) * factor), color);
    }

    public static int scaleRGB(int color, float factor) {
        return color(alpha(color), Math.clamp((long) (red(color) * factor), 0, 255),
                Math.clamp((long) (green(color) * factor), 0, 255), Math.clamp((long) (blue(color) * factor), 0, 255));
    }
}
