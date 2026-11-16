package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Kiln's stand-in for {@code DynamicTexture}: a texture a mod makes from a picture in memory and registers by a name
 * (D-170: the icons of JEI's config screen). Registered, it is drawn as a mod's own picture is (D-160): kiln.Sprites
 * sends its pixels to the game the first time a mod draws a part of it, and again after each {@link #upload()} or
 * {@link #setPixels} (D-179).
 */
public class DynamicTexture extends AbstractTexture {
    private final java.util.function.Supplier<String> label;
    private NativeImage pixels;
    /** How many times its pixels changed (an upload, new pixels): what the game has of it is from one of them. */
    private int version;

    public DynamicTexture(java.util.function.Supplier<String> label, NativeImage pixels) {
        this.label = label;
        this.pixels = pixels;
    }

    public DynamicTexture(java.util.function.Supplier<String> label, int width, int height, boolean calloc) {
        this(label, new NativeImage(width, height, calloc));
    }

    public DynamicTexture(String label, int width, int height, boolean calloc) {
        this(() -> label, width, height, calloc);
    }

    public NativeImage getPixels() {
        return pixels;
    }

    public void setPixels(NativeImage pixels) {
        this.pixels = pixels;
        version++;
    }

    /** Sends the picture to the GPU in vanilla: in Kiln, the next draw sends the game its pixels as they are now. */
    public void upload() {
        version++;
    }

    /** Kiln's own: how many times its pixels changed (D-179). */
    public int kiln$version() {
        return version;
    }

    @Override
    public void close() {
        if (pixels != null) {
            pixels.close();
            pixels = null;
        }
    }
}
