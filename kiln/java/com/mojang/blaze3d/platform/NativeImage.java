package com.mojang.blaze3d.platform;

/**
 * Kiln's stand-in for {@code NativeImage}: a picture in memory, RGBA, read from a PNG with Kiln's own decoder (D-160,
 * no AWT in the game's process). The game draws with its own textures: what a mod draws into one stays in Java
 * (D-170: JEI's config screen makes its icons from PNG bytes).
 */
public class NativeImage implements AutoCloseable {
    private final Format format;
    private final int width;
    private final int height;
    /** Each pixel as vanilla's {@code getPixel} gives it: ABGR in an int. */
    private final int[] pixels;

    public NativeImage(int width, int height, boolean calloc) {
        this(Format.RGBA, width, height, calloc);
    }

    public NativeImage(Format format, int width, int height, boolean calloc) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.pixels = new int[Math.max(0, width) * Math.max(0, height)];
    }

    public NativeImage(Format format, int width, int height, boolean calloc, long pointer) {
        this(format, width, height, calloc);
    }

    protected NativeImage() {
        this(Format.RGBA, 0, 0, false);
    }

    /** A PNG read whole from the stream. */
    public static NativeImage read(java.io.InputStream in) throws java.io.IOException {
        return read(in.readAllBytes());
    }

    public static NativeImage read(byte[] png) throws java.io.IOException {
        int[] size = new int[2];
        byte[] rgba = kiln.Sprites.rgba(png, size);
        NativeImage image = new NativeImage(Format.RGBA, size[0], size[1], false);
        for (int i = 0; i < image.pixels.length; i++) {
            int r = rgba[i * 4] & 0xFF, g = rgba[i * 4 + 1] & 0xFF, b = rgba[i * 4 + 2] & 0xFF, a = rgba[i * 4 + 3] & 0xFF;
            image.pixels[i] = a << 24 | b << 16 | g << 8 | r;
        }
        return image;
    }

    public static NativeImage read(Format format, java.io.InputStream in) throws java.io.IOException {
        return read(in);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Format format() {
        return format;
    }

    /** The pixel as ABGR (vanilla's order in memory). */
    public int getPixel(int x, int y) {
        return pixels[y * width + x];
    }

    public void setPixelABGR(int x, int y, int abgr) {
        pixels[y * width + x] = abgr;
    }

    /** The pixel as ARGB. */
    public int getPixelARGB(int x, int y) {
        int p = getPixel(x, y);
        return p & 0xFF00FF00 | (p & 0xFF) << 16 | p >> 16 & 0xFF;
    }

    public void setPixel(int x, int y, int argb) {
        setPixelABGR(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
    }

    public void fillRect(int x, int y, int w, int h, int argb) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                setPixel(i, j, argb);
            }
        }
    }

    public void writeToFile(java.nio.file.Path file) {
        kiln.Missing.called("com.mojang.blaze3d.platform.NativeImage.writeToFile");
    }

    public void resizeSubRectTo(int x, int y, int w, int h, NativeImage to) {
        kiln.Missing.called("com.mojang.blaze3d.platform.NativeImage.resizeSubRectTo");
    }

    @Override
    public void close() {}

    public static enum Format {
        RGBA, RGB, LUMINANCE_ALPHA, LUMINANCE;
    }
}
