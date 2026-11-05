package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

/**
 * Kiln's stand-in for {@code GuiGraphicsExtractor}: it records what a HUD element draws, and Kiln's Rust side
 * draws the same with Horizon's HUD every frame until the next tick. Colours are ARGB; alpha 0 draws nothing,
 * as in vanilla. What a mod draws goes through its pose ({@code pose()}: moved and scaled; Kiln draws nothing
 * rotated).
 */
public class GuiGraphicsExtractor {
    private final int width;
    private final int height;
    private final Matrix3x2fStack pose = new Matrix3x2fStack(16);
    private final List<String> ops = new ArrayList<>();

    /** Kiln's own: a canvas of the game's GUI size. */
    public GuiGraphicsExtractor(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int guiWidth() {
        return width;
    }

    public int guiHeight() {
        return height;
    }

    public Matrix3x2fStack pose() {
        return pose;
    }

    /** The box between corners (x1, y1) and (x2, y2). */
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (argb >>> 24 == 0) {
            return;
        }
        Vector2f a = at(x1, y1);
        Vector2f b = at(x2, y2);
        float x = Math.min(a.x, b.x);
        float y = Math.min(a.y, b.y);
        ops.add("f " + num(x) + " " + num(y) + " " + num(Math.abs(b.x - a.x)) + " " + num(Math.abs(b.y - a.y)) + " "
                + Integer.toUnsignedString(argb));
    }

    /**
     * A box from {@code argbTop} at its top to {@code argbBottom} at its bottom. Horizon's HUD fills in one colour,
     * so Kiln draws it a row of pixels at a time, each in its colour between the two.
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int argbTop, int argbBottom) {
        int top = Math.min(y1, y2);
        int rows = Math.abs(y2 - y1);
        for (int row = 0; row < rows; row++) {
            float t = rows == 1 ? 0.0F : row / (float) (rows - 1);
            fill(x1, top + row, x2, top + row + 1, mix(argbTop, argbBottom, t));
        }
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    /** A line from x1 to x2 (both drawn) at row y. */
    public void horizontalLine(int x1, int x2, int y, int argb) {
        fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, argb);
    }

    /** A line down column x, between rows y1 and y2 (neither end drawn, as vanilla). */
    public void verticalLine(int x, int y1, int y2, int argb) {
        fill(x, Math.min(y1, y2) + 1, x + 1, Math.max(y1, y2), argb);
    }

    /** A box's edges, one pixel wide: {@code width} by {@code height} from (x, y). */
    public void outline(int x, int y, int width, int height, int argb) {
        fill(x, y, x + width, y + 1, argb);
        fill(x, y + height - 1, x + width, y + height, argb);
        fill(x, y + 1, x + 1, y + height - 1, argb);
        fill(x + width - 1, y + 1, x + width, y + height - 1, argb);
    }

    /** {@code text} with its top-left at (x, y), with a shadow. */
    public void text(Font font, String text, int x, int y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, String text, int x, int y, int argb, boolean shadow) {
        if (argb >>> 24 == 0 || text == null || text.isEmpty()) {
            return;
        }
        Vector2f at = at(x, y);
        float scale = scale();
        String where = scale == 1.0F ? "t " + num(at.x) + " " + num(at.y)
                : "s " + num(at.x) + " " + num(at.y) + " " + num(scale);
        ops.add(where + " " + Integer.toUnsignedString(argb) + " " + (shadow ? 1 : 0) + " " + text);
    }

    /** Text with its styles dropped: Kiln draws plain text in one colour. */
    public void text(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb, boolean shadow) {
        text(font, text == null ? null : text.getString(), x, y, argb, shadow);
    }

    /** {@code text} centred on x, with a shadow. */
    public void centeredText(Font font, String text, int x, int y, int argb) {
        if (text != null) {
            text(font, text, x - font.width(text) / 2, y, argb, true);
        }
    }

    public void centeredText(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb) {
        centeredText(font, text == null ? null : text.getString(), x, y, argb);
    }

    /** An item's icon, 16 pixels square, its top left at (x, y). */
    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Vector2f at = at(x, y);
        ops.add("i " + num(at.x) + " " + num(at.y) + " " + num(16.0F * scale()) + " " + stack.getItem().kiln$id());
    }

    public void item(ItemStack stack, int x, int y, int seed) {
        item(stack, x, y);
    }

    /** What vanilla draws over an item's icon: its count, unless one, and the durability bar of a worn item. */
    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        itemDecorations(font, stack, x, y, null);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getCount() != 1 || text != null) {
            String count = text == null ? Integer.toString(stack.getCount()) : text;
            text(font, count, x + 19 - 2 - font.width(count), y + 6 + 3, 0xFFFFFFFF, true);
        }
        if (stack.isBarVisible()) {
            int bx = x + 2;
            int by = y + 13;
            fill(bx, by, bx + 13, by + 2, 0xFF000000);
            fill(bx, by, bx + stack.getBarWidth(), by + 1, stack.getBarColor() | 0xFF000000);
        }
    }

    /**
     * {@code text} in lines no wider than {@code width}, from (x, y) down, a line height apart, with a shadow; gives
     * the y below the last line.
     */
    public int textWithWordWrap(Font font, net.minecraft.network.chat.FormattedText text, int x, int y, int width,
            int argb) {
        return textWithWordWrap(font, text, x, y, width, argb, true);
    }

    public int textWithWordWrap(Font font, net.minecraft.network.chat.FormattedText text, int x, int y, int width,
            int argb, boolean shadow) {
        for (String line : font.kiln$wrap(text.getString(), width)) {
            text(font, line, x, y, argb, shadow);
            y += font.lineHeight;
        }
        return y;
    }

    /** The scissors in force, outermost first: each the box it cuts to, inside the one before (x0, y0, x1, y1). */
    private final java.util.ArrayDeque<float[]> scissors = new java.util.ArrayDeque<>();

    /**
     * What is drawn from now on is cut to the box between corners (x1, y1) and (x2, y2), through the pose, inside the
     * scissor already in force; until {@link #disableScissor}.
     */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Vector2f a = at(x1, y1);
        Vector2f b = at(x2, y2);
        float[] box = {Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y)};
        float[] outer = scissors.peekLast();
        if (outer != null) {
            box = new float[] {Math.max(box[0], outer[0]), Math.max(box[1], outer[1]),
                    Math.max(Math.max(box[0], outer[0]), Math.min(box[2], outer[2])),
                    Math.max(Math.max(box[1], outer[1]), Math.min(box[3], outer[3]))};
        }
        scissors.addLast(box);
        clip();
    }

    /** The last scissor is lifted: the one before it is in force again, if any. */
    public void disableScissor() {
        if (scissors.pollLast() == null) {
            throw new IllegalStateException("Scissor stack underflow");
        }
        clip();
    }

    /** Whether (x, y) is inside the scissor in force (true with none). */
    public boolean containsPointInScissor(int x, int y) {
        float[] box = scissors.peekLast();
        return box == null || x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
    }

    private void clip() {
        float[] box = scissors.peekLast();
        ops.add(box == null ? "c" : "c " + num(box[0]) + " " + num(box[1]) + " " + num(box[2]) + " " + num(box[3]));
    }

    /**
     * Kiln's own: what was drawn, one op a string ({@code f x y w h argb}, {@code t x y argb shadow text}, {@code c x0
     * y0 x1 y1} for a scissor and {@code c} for none), ending with no scissor in force.
     */
    public List<String> kiln$ops() {
        if (!scissors.isEmpty()) {
            scissors.clear();
            clip();
        }
        return ops;
    }

    /** A point through the pose. */
    private Vector2f at(float x, float y) {
        return pose.transformPosition(x, y, new Vector2f());
    }

    /** How much the pose scales (Kiln takes it as the same both ways). */
    private float scale() {
        return (float) Math.sqrt(pose.m00 * pose.m00 + pose.m01 * pose.m01);
    }

    /** A number as the ops write it: whole ones without a fraction. */
    private static String num(float v) {
        return v == (int) v ? Integer.toString((int) v) : Float.toString(v);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.renderer.state.gui.GuiRenderState guiRenderState;
    public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.resources.Identifier p1, int p2, int p3, float p4, float p5, int p6, int p7, int p8, int p9) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.blit"); }
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.resources.Identifier p1, int p2, int p3, int p4, int p5) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.blitSprite"); }
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.client.renderer.texture.TextureAtlasSprite p1, int p2, int p3, int p4, int p5) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.blitSprite"); }
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.client.renderer.texture.TextureAtlasSprite p1, int p2, int p3, int p4, int p5, int p6) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.blitSprite"); }
    public void setTooltipForNextFrame(net.minecraft.client.gui.Font p0, java.util.List<net.minecraft.network.chat.Component> p1, java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> p2, int p3, int p4) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.setTooltipForNextFrame"); }
    public void setTooltipForNextFrame(net.minecraft.client.gui.Font p0, net.minecraft.network.chat.Component p1, int p2, int p3) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.setTooltipForNextFrame"); }
    public void setComponentTooltipForNextFrame(net.minecraft.client.gui.Font p0, java.util.List<net.minecraft.network.chat.Component> p1, int p2, int p3) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.setComponentTooltipForNextFrame"); }
    // End of what tools/kiln-stubs.py generated.
}
