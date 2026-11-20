package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
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
    /** Kiln's own: the box each op drew ({@code x0 y0 x1 y1}), tooltips' left out (D-156). */
    private final List<float[]> boxes = new ArrayList<>();
    private boolean boxless;

    /** Kiln's own: a canvas of the game's GUI size. */
    public GuiGraphicsExtractor(int width, int height) {
        this.width = width;
        this.height = height;
        this.minecraft = net.minecraft.client.Minecraft.getInstance();
    }

    /**
     * A canvas drawing into a render state another one draws into (D-170: Mezz Config GUI's quick menu over the screen
     * it is opened on); the game's size.
     */
    public GuiGraphicsExtractor(net.minecraft.client.Minecraft minecraft,
            net.minecraft.client.renderer.state.gui.GuiRenderState state, int mouseX, int mouseY) {
        this(kiln$window(minecraft).getGuiScaledWidth(), kiln$window(minecraft).getGuiScaledHeight());
        if (minecraft != null) {
            this.minecraft = minecraft;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.guiRenderState = state;
        if (state != null) {
            synchronized (SHARING) {
                SHARING.computeIfAbsent(state, k -> new ArrayList<>()).add(this);
            }
        }
    }

    /**
     * The window of {@code minecraft}, or of the game when a mod passes none: REI makes its own canvas from another's
     * fields ({@code minecraft}, the render state, the pointer), and draws into the same render state (D-215).
     */
    private static com.mojang.blaze3d.platform.Window kiln$window(net.minecraft.client.Minecraft minecraft) {
        return (minecraft != null ? minecraft : net.minecraft.client.Minecraft.getInstance()).getWindow();
    }

    /** Each canvas's render state: what a second canvas made on it draws joins the first's ops (D-170). */
    {
        guiRenderState = new net.minecraft.client.renderer.state.gui.GuiRenderState();
    }

    /** The canvases made on another's render state, by that state, until the other's ops are read. */
    private static final java.util.Map<net.minecraft.client.renderer.state.gui.GuiRenderState, List<GuiGraphicsExtractor>>
            SHARING = new java.util.WeakHashMap<>();

    /** Kiln's own: the ops and boxes of the canvases drawn on this one's render state, after this one's. */
    private void kiln$joinShared() {
        List<GuiGraphicsExtractor> shared;
        synchronized (SHARING) {
            shared = SHARING.remove(guiRenderState);
        }
        if (shared != null) {
            for (GuiGraphicsExtractor other : shared) {
                if (other != this) {
                    ops.addAll(other.kiln$ops());
                    boxes.addAll(other.kiln$boxes());
                }
            }
        }
    }

    /** Tooltips and the like vanilla draws after the rest: Kiln draws each where it is asked, so nothing is left. */
    public void extractDeferredElements(int mouseX, int mouseY, float partialTick) {}

    /** An input method's composition drawn over the field: the game's window draws its own. */
    public void setPreeditOverlay(net.minecraft.client.gui.components.Renderable overlay) {}

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
        box(x, y, x + Math.abs(b.x - a.x), y + Math.abs(b.y - a.y));
    }

    /**
     * Kiln's own: entity type {@code type}'s model in the box between (x1, y1) and (x2, y2), {@code size} pixels a
     * block, raised by {@code offsetY} blocks, turned toward ({@code mouseX}, {@code mouseY}): the game draws it as it
     * draws a mob in its screens (D-173). As vanilla's picture-in-picture entity, the box and the pointer are the
     * screen's, the pose not applied (Just Enough Breeding adds the pose's translation itself); the scissor cuts it.
     */
    public void kiln$entity(net.minecraft.resources.Identifier type, int x1, int y1, int x2, int y2, float size,
            float offsetY, float mouseX, float mouseY) {
        kiln$entity(type, x1, y1, x2, y2, size, offsetY, mouseX, mouseY, "");
    }

    /** The same, the entity as the game saves one ({@code saved}, SNBT; "" for its type alone), D-179. */
    public void kiln$entity(net.minecraft.resources.Identifier type, int x1, int y1, int x2, int y2, float size,
            float offsetY, float mouseX, float mouseY, String saved) {
        if (type == null) {
            return;
        }
        ops.add("e " + num(Math.min(x1, x2)) + " " + num(Math.min(y1, y2)) + " " + num(Math.max(x1, x2)) + " "
                + num(Math.max(y1, y2)) + " " + num(size) + " " + num(offsetY) + " " + num(mouseX) + " " + num(mouseY)
                + " " + type + (saved == null || saved.isEmpty() ? "" : " " + saved));
        box(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }

    /**
     * A box from {@code argbTop} at its top to {@code argbBottom} at its bottom. Horizon's HUD fills in one colour,
     * so Kiln draws it a row of pixels at a time, each in its colour between the two.
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int argbTop, int argbBottom) {
        int top = Math.min(y1, y2);
        int rows = Math.abs(y2 - y1);
        // Rows of the same colour go as one box.
        int from = 0;
        for (int row = 1; row <= rows; row++) {
            int colour = mix(argbTop, argbBottom, rows == 1 ? 0.0F : from / (float) (rows - 1));
            if (row == rows || mix(argbTop, argbBottom, row / (float) (rows - 1)) != colour) {
                fill(x1, top + from, x2, top + row, colour);
                from = row;
            }
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

    /**
     * Kiln's own: sprite {@code name} of the game's GUI ({@code widget/button}, a mod's {@code mymod:...}) drawn into
     * the box {@code width} by {@code height} from (x, y), its edges {@code border} pixels wide kept (nine-sliced, as
     * vanilla's widget sprites); where the game has no such sprite, a box of {@code fallback} instead.
     */
    public void kiln$sprite(String name, int x, int y, int width, int height, int border, int fallback) {
        spriteOp(kiln$spriteName(name), x, y, width, height, border, fallback);
    }

    /** The {@code b} op: the sprite the game calls {@code gameName} over the box, or {@code fallback}'s box. */
    private void spriteOp(String gameName, int x, int y, int width, int height, int border, int fallback) {
        spriteOp(gameName, x, y, width, height, border, fallback, -1);
    }

    /**
     * The {@code b} op, or with a {@code tint} other than white the {@code B} op: the sprite's pixels multiplied by it
     * (ARGB), as vanilla's {@code blitSprite(..., color)} draws (a fluid's colour, a faded button, D-168).
     */
    private void spriteOp(String gameName, int x, int y, int width, int height, int border, int fallback, int tint) {
        if (width <= 0 || height <= 0 || tint >>> 24 == 0) {
            return;
        }
        Vector2f a = at(x, y);
        float scale = scale();
        String where = num(a.x) + " " + num(a.y) + " " + num(width * scale) + " " + num(height * scale) + " "
                + num(border * scale) + " " + Integer.toUnsignedString(fallback) + " ";
        ops.add(tint == -1 ? "b " + where + gameName : "B " + where + Integer.toUnsignedString(tint) + " " + gameName);
        box(a.x, a.y, a.x + width * scale, a.y + height * scale);
    }

    /**
     * GUI sprite {@code sprite} by the name kiln.Sprites gives it (a mod's picture goes to the game, D-160), its edges
     * kept as its {@code .mcmeta} nine-slices them; where Sprites has none, as before: the game's name, its fallback.
     */
    private void guiSprite(net.minecraft.resources.Identifier sprite, int x, int y, int width, int height,
            int border, int fallback) {
        guiSprite(sprite, x, y, width, height, border, fallback, -1);
    }

    private void guiSprite(net.minecraft.resources.Identifier sprite, int x, int y, int width, int height,
            int border, int fallback, int tint) {
        String name = kiln.Sprites.lookup(sprite);
        if (name == null) {
            spriteOp(kiln$spriteName(sprite.toString()), x, y, width, height, border, fallback, tint);
            return;
        }
        if (border == 0 && !sprite.getNamespace().equals("minecraft")) {
            border = nineSliceBorder(net.minecraft.client.resources.metadata.gui.GuiMetadataSection.of(
                    kiln.Sprites.guiMetadata(GUI_ATLAS, sprite)).scaling());
        }
        spriteOp(name, x, y, width, height, border, fallback, tint);
    }

    private static final net.minecraft.resources.Identifier GUI_ATLAS =
            net.minecraft.resources.Identifier.withDefaultNamespace("textures/atlas/gui.png");

    /** The edge a nine-sliced sprite keeps (Horizon's {@code b} op keeps one, the same on every side); else 0. */
    private static int nineSliceBorder(net.minecraft.client.resources.metadata.gui.GuiSpriteScaling scaling) {
        return scaling instanceof net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice slice
                ? slice.border().left() : 0;
    }

    /** The game's name of a GUI sprite: {@code gui/sprites/<path>} for vanilla's, {@code <namespace>:gui/sprites/<path>}. */
    static String kiln$spriteName(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return namespace.equals("minecraft") ? "gui/sprites/" + path : namespace + ":gui/sprites/" + path;
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
        box(at.x, at.y, at.x + (font == null ? 0 : font.width(text)) * scale, at.y + 9 * scale);
    }

    /** Text with its styles: each part in its colour, bold, italic... as the game's (D-164). */
    public void text(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb, boolean shadow) {
        if (text != null) {
            text(font, text.getVisualOrderText(), x, y, argb, shadow);
        }
    }

    /** {@code text} centred on x, with a shadow. */
    public void centeredText(Font font, String text, int x, int y, int argb) {
        if (text != null) {
            text(font, text, x - font.width(text) / 2, y, argb, true);
        }
    }

    public void centeredText(Font font, net.minecraft.network.chat.Component text, int x, int y, int argb) {
        if (text != null) {
            net.minecraft.util.FormattedCharSequence seq = text.getVisualOrderText();
            text(font, seq, x - font.width(seq) / 2, y, argb, true);
        }
    }

    public void centeredText(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int argb) {
        if (text != null) {
            text(font, text, x - font.width(text) / 2, y, argb, true);
        }
    }

    /**
     * An item's icon, 16 pixels square, its top left at (x, y). A stack with components of its own (an enchanted
     * book's, a potion's, a trim's) goes whole, as its SNBT ({@code I x y size snbt}), so the game draws it as it
     * draws that stack: its glint, its potion's colour, its trim (D-164); a plain one by its item's id.
     */
    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Vector2f at = at(x, y);
        String where = num(at.x) + " " + num(at.y) + " " + num(16.0F * scale()) + " ";
        ops.add(stack.kiln$savedIds().isEmpty() ? "i " + where + stack.getItem().kiln$id()
                : "I " + where + stack.kiln$snbt());
        box(at.x, at.y, at.x + 16.0F * scale(), at.y + 16.0F * scale());
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
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, width)) {
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

    /** The tooltip asked for while drawing: drawn last, over the rest (vanilla's deferred tooltip, one a frame). */
    private Runnable deferredTooltip;

    /** Kiln's own: {@code text} as a tooltip by the pointer at (x, y), drawn over everything at the end. */
    public void kiln$tooltip(net.minecraft.network.chat.Component text, int x, int y) {
        if (text != null && !text.getString().isEmpty()) {
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            setTooltipForNextFrame(font, font.split(text, 170), x, y);
        }
    }

    public void setTooltipForNextFrame(Font font, List<net.minecraft.util.FormattedCharSequence> lines, int x, int y) {
        setTooltipForNextFrame(font, lines, DefaultTooltipPositioner.INSTANCE, x, y, false);
    }

    public void setTooltipForNextFrame(Font font, List<net.minecraft.util.FormattedCharSequence> lines,
            ClientTooltipPositioner positioner, int x, int y, boolean focused) {
        setTooltipForNextFrameInternal(font, lines.stream().map(ClientTooltipComponent::create).toList(), x, y,
                positioner, null, focused);
    }

    public void setTooltipForNextFrame(Font font, net.minecraft.network.chat.Component text, int x, int y) {
        setTooltipForNextFrame(font, List.of(text.getVisualOrderText()), x, y);
    }

    public void setTooltipForNextFrame(net.minecraft.network.chat.Component text, int x, int y) {
        setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, text, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<net.minecraft.network.chat.Component> lines, int x,
            int y) {
        setTooltipForNextFrame(font, lines.stream().map(net.minecraft.network.chat.Component::getVisualOrderText)
                .toList(), x, y);
    }

    /** Lines of text, then {@code image} (a bundle's contents, a mod's picture) under them. */
    public void setTooltipForNextFrame(Font font, List<net.minecraft.network.chat.Component> lines,
            java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> image, int x, int y) {
        setTooltipForNextFrame(font, lines, image, x, y, null);
    }

    public void setTooltipForNextFrame(Font font, List<net.minecraft.network.chat.Component> lines,
            java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> image, int x, int y,
            net.minecraft.resources.Identifier background) {
        List<ClientTooltipComponent> parts = new ArrayList<>();
        for (net.minecraft.network.chat.Component line : lines) {
            parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        image.ifPresent(i -> parts.add(parts.isEmpty() ? 0 : 1, ClientTooltipComponent.create(i)));
        setTooltipForNextFrameInternal(font, parts, x, y, DefaultTooltipPositioner.INSTANCE, background, false);
    }

    /**
     * The tooltip of {@code parts} by the pointer at (x, y), placed by {@code positioner}, drawn when the frame's
     * drawing ends; the first asked for in a frame wins, unless a later one is {@code focused} (vanilla's).
     */
    public void setTooltipForNextFrameInternal(Font font, List<ClientTooltipComponent> parts, int x, int y,
            ClientTooltipPositioner positioner, net.minecraft.resources.Identifier background, boolean focused) {
        if (parts.isEmpty() || deferredTooltip != null && !focused) {
            return;
        }
        List<ClientTooltipComponent> copy = List.copyOf(parts);
        deferredTooltip = () -> tooltip(font, copy, x, y, positioner, background, focused);
    }

    /**
     * The tooltip of {@code parts} drawn now: a dark box with a purple edge (vanilla's look, Horizon has no tooltip
     * sprite for the mods), each part's text from the top down, then each part's picture. It takes no input: what
     * it covers is the mods' own only where they drew under it.
     */
    public void tooltip(Font font, List<ClientTooltipComponent> parts, int x, int y,
            ClientTooltipPositioner positioner, net.minecraft.resources.Identifier background, boolean focused) {
        if (parts.isEmpty()) {
            return;
        }
        int w = 0;
        int h = parts.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent part : parts) {
            w = Math.max(w, part.getWidth(font));
            h += part.getHeight(font);
        }
        org.joml.Vector2ic at = positioner.positionTooltip(guiWidth(), guiHeight(), x, y, w, h);
        int left = at.x();
        int top = at.y();
        boolean was = boxless;
        boxless = true;
        pose.pushMatrix();
        fill(left - 3, top - 4, left + w + 3, top - 3, 0xF0100010);
        fill(left - 3, top + h + 3, left + w + 3, top + h + 4, 0xF0100010);
        fill(left - 3, top - 3, left + w + 3, top + h + 3, 0xF0100010);
        fill(left - 4, top - 3, left - 3, top + h + 3, 0xF0100010);
        fill(left + w + 3, top - 3, left + w + 4, top + h + 3, 0xF0100010);
        fillGradient(left - 3, top - 2, left - 2, top + h + 2, 0x505000FF, 0x5028007F);
        fillGradient(left + w + 2, top - 2, left + w + 3, top + h + 2, 0x505000FF, 0x5028007F);
        fill(left - 3, top - 3, left + w + 3, top - 2, 0x505000FF);
        fill(left - 3, top + h + 2, left + w + 3, top + h + 3, 0x5028007F);
        int row = top;
        for (int i = 0; i < parts.size(); i++) {
            ClientTooltipComponent part = parts.get(i);
            part.extractText(this, font, left, row);
            row += part.getHeight(font) + (i == 0 ? 2 : 0);
        }
        row = top;
        for (int i = 0; i < parts.size(); i++) {
            ClientTooltipComponent part = parts.get(i);
            part.extractImage(font, left, row, w, h, this);
            row += part.getHeight(font) + (i == 0 ? 2 : 0);
        }
        pose.popMatrix();
        boxless = was;
    }

    /** Kiln's own: whether a tooltip waits to be drawn at the frame's end. */
    public boolean kiln$hasTooltip() {
        return deferredTooltip != null;
    }

    /** {@code text}, each run of one colour in its style's colour ({@code argb}'s where it has none). */
    public void text(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int argb,
            boolean shadow) {
        if (text == null || argb >>> 24 == 0) {
            return;
        }
        if (styledText(font, text, x, y, argb, shadow)) {
            return;
        }
        StringBuilder run = new StringBuilder();
        int[] state = {x, argb};
        text.accept((index, style, codepoint) -> {
            int colour = style.getColor() == null ? argb : argb & 0xFF000000 | style.getColor().getValue() & 0xFFFFFF;
            if (colour != state[1] && run.length() > 0) {
                String piece = run.toString();
                text(font, piece, state[0], y, state[1], shadow);
                state[0] += font.width(piece);
                run.setLength(0);
            }
            state[1] = colour;
            run.appendCodePoint(codepoint);
            return true;
        });
        if (run.length() > 0) {
            text(font, run.toString(), state[0], y, state[1], shadow);
        }
    }

    /** Text with its styles' colours: {@code text(font, text.getVisualOrderText(), ...)}. */
    public void kiln$styledText(Font font, net.minecraft.network.chat.FormattedText text, int x, int y, int argb,
            boolean shadow) {
        text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(text), x, y, argb, shadow);
    }

    /**
     * Text of which some part is bold, italic, underlined, struck through or obfuscated: one op, {@code j x y scale argb
     * shadow json}, its parts as the game's text JSON with their colours and formats written out, so that the game's
     * font draws them as vanilla's (D-164). False, drawing nothing, for text in colours alone: that goes as before, a
     * {@code t} op for each run of one colour.
     */
    private boolean styledText(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int argb,
            boolean shadow) {
        List<Object[]> runs = new ArrayList<>();
        boolean[] formatted = {false};
        StringBuilder run = new StringBuilder();
        net.minecraft.network.chat.Style[] current = {null};
        text.accept((index, style, codepoint) -> {
            if (!style.kiln$formats().isEmpty()) {
                formatted[0] = true;
            }
            if (current[0] != null && !current[0].equals(style) && run.length() > 0) {
                runs.add(new Object[] {current[0], run.toString()});
                run.setLength(0);
            }
            current[0] = style;
            run.appendCodePoint(codepoint);
            return true;
        });
        if (!formatted[0]) {
            return false;
        }
        if (run.length() > 0) {
            runs.add(new Object[] {current[0], run.toString()});
        }
        com.google.gson.JsonArray extra = new com.google.gson.JsonArray();
        for (Object[] r : runs) {
            net.minecraft.network.chat.Style style = (net.minecraft.network.chat.Style) r[0];
            com.google.gson.JsonObject part = new com.google.gson.JsonObject();
            part.addProperty("text", (String) r[1]);
            if (style.getColor() != null) {
                part.addProperty("color", String.format(java.util.Locale.ROOT, "#%06X",
                        style.getColor().getValue() & 0xFFFFFF));
            }
            for (net.minecraft.ChatFormatting format : style.kiln$formats()) {
                String key = switch (format) {
                    case BOLD -> "bold";
                    case ITALIC -> "italic";
                    case UNDERLINE -> "underlined";
                    case STRIKETHROUGH -> "strikethrough";
                    case OBFUSCATED -> "obfuscated";
                    default -> null;
                };
                if (key != null) {
                    part.addProperty(key, true);
                }
            }
            extra.add(part);
        }
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("text", "");
        json.add("extra", extra);
        Vector2f at = at(x, y);
        float scale = scale();
        ops.add("j " + num(at.x) + " " + num(at.y) + " " + num(scale) + " " + Integer.toUnsignedString(argb) + " "
                + (shadow ? 1 : 0) + " " + json);
        box(at.x, at.y, at.x + (font == null ? 0 : font.width(text)) * scale, at.y + 9 * scale);
        return true;
    }

    /** What vanilla's {@code GuiRenderState} keeps apart, drawn in layers; Kiln draws in the order asked. */
    public void nextStratum() {}

    /** The pointer's shape while over what is drawn; Horizon keeps its own pointer. */
    public void requestCursor(com.mojang.blaze3d.platform.cursor.CursorType cursor) {}

    /** An item's icon as in a slot, with no model of its own (Kiln draws every item so). */
    public void fakeItem(ItemStack stack, int x, int y) {
        item(stack, x, y);
    }

    public void fakeItem(ItemStack stack, int x, int y, int seed) {
        item(stack, x, y);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        item(stack, x, y);
    }

    /** A box of the gui's grey: what a sprite the game has no picture of shows (JEI's own, a mod's, until D-160). */
    private static final int SPRITE_FALLBACK = 0xC08B8B8B;

    /** GUI sprite {@code sprite} stretched over the box {@code width} by {@code height} from (x, y). */
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier sprite, int x, int y, int width, int height) {
        guiSprite(sprite, x, y, width, height, 0, SPRITE_FALLBACK);
    }

    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier sprite, int x, int y, int width, int height, float alpha) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        blitSprite(pipeline, sprite, x, y, width, height, a << 24 | 0xFFFFFF);
    }

    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier sprite, int x, int y, int width, int height, int argb) {
        guiSprite(sprite, x, y, width, height, 0, SPRITE_FALLBACK, argb);
    }

    /** {@code sprite} of an atlas over the box {@code width} by {@code height} from (x, y). */
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int x, int y, int width, int height) {
        atlasSprite(sprite, x, y, width, height, 0, SPRITE_FALLBACK);
    }

    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int x, int y, int width, int height,
            int argb) {
        atlasSprite(sprite, x, y, width, height, 0, SPRITE_FALLBACK, argb);
    }

    /**
     * The part {@code uWidth} by {@code vHeight} from (u, v) of {@code sprite} (as big as {@code textureWidth} by
     * {@code textureHeight}) at (x, y): the whole sprite where that is all of it, else that part of its picture cut
     * out as a sprite of its own (kiln.Sprites, as a blit's part: a recipe viewer's arrow or flame as it fills, D-168);
     * a box of the gui's grey where no jar nor the game's resources have the picture.
     */
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int textureWidth, int textureHeight,
            int u, int v, int x, int y, int uWidth, int vHeight) {
        blitSprite(pipeline, sprite, textureWidth, textureHeight, u, v, x, y, uWidth, vHeight, -1);
    }

    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int textureWidth, int textureHeight,
            int u, int v, int x, int y, int uWidth, int vHeight, int argb) {
        if (sprite == null || uWidth <= 0 || vHeight <= 0) {
            return;
        }
        if (u == 0 && v == 0 && uWidth == textureWidth && vHeight == textureHeight) {
            atlasSprite(sprite, x, y, uWidth, vHeight, 0, SPRITE_FALLBACK, argb);
            return;
        }
        partOf(sprite.atlasLocation(), sprite.contents().name(), textureWidth, textureHeight, u, v, x, y, uWidth,
                vHeight, argb);
    }

    /** The same for GUI sprite {@code sprite} by its id. */
    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier sprite, int textureWidth, int textureHeight, int u, int v, int x, int y,
            int uWidth, int vHeight) {
        blitSprite(pipeline, sprite, textureWidth, textureHeight, u, v, x, y, uWidth, vHeight, -1);
    }

    public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier sprite, int textureWidth, int textureHeight, int u, int v, int x, int y,
            int uWidth, int vHeight, int argb) {
        if (sprite == null || uWidth <= 0 || vHeight <= 0) {
            return;
        }
        if (u == 0 && v == 0 && uWidth == textureWidth && vHeight == textureHeight) {
            guiSprite(sprite, x, y, uWidth, vHeight, 0, SPRITE_FALLBACK, argb);
            return;
        }
        partOf(GUI_ATLAS, sprite, textureWidth, textureHeight, u, v, x, y, uWidth, vHeight, argb);
    }

    /** Part (u, v, w, h) of an atlas sprite's picture, the whole being {@code textureWidth} by {@code textureHeight}. */
    private void partOf(net.minecraft.resources.Identifier atlas, net.minecraft.resources.Identifier sprite,
            int textureWidth, int textureHeight, int u, int v, int x, int y, int uWidth, int vHeight, int argb) {
        String folder = atlas.getPath().contains("gui") ? "textures/gui/sprites/" : "textures/";
        String name = kiln.Sprites.lookup(net.minecraft.resources.Identifier.fromNamespaceAndPath(sprite.getNamespace(),
                folder + sprite.getPath() + ".png"), u, v, uWidth, vHeight, textureWidth, textureHeight);
        if (name == null) {
            missing(sprite + " (" + u + "," + v + " " + uWidth + "x" + vHeight + " of " + textureWidth + "x"
                    + textureHeight + ")");
            fill(x, y, x + uWidth, y + vHeight, SPRITE_FALLBACK);
        } else {
            spriteOp(name, x, y, uWidth, vHeight, 0, SPRITE_FALLBACK, argb);
        }
    }

    private static final java.util.Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A picture a mod drew that Kiln has none of (a grey box instead): said once in the log. */
    private static void missing(String what) {
        if (MISSING.size() < 200 && MISSING.add(what)) {
            kiln.Native.log("Kiln draws a grey box for a picture it hasn't: " + what);
        }
    }

    /**
     * {@code sprite} nine-sliced over the box as the game draws it (D-175): the corners as they are, the edges and the
     * middle tiled at the sprite's size (stretched with {@code stretch_inner}), each piece a part of the sprite's picture
     * (kiln.Sprites). Horizon's {@code b} op keeps one border and stretches the middle: JEI's scroll bar knob, its grip
     * lines stretched into bands, drew as a stack of grey boxes.
     */
    public void blitNineSlicedSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice slice, int x, int y, int width,
            int height, int argb) {
        if (sprite == null || width <= 0 || height <= 0) {
            return;
        }
        var border = slice.border();
        int tw = slice.width();
        int th = slice.height();
        int l = Math.min(border.left(), width / 2);
        int r = Math.min(border.right(), width - l);
        int t = Math.min(border.top(), height / 2);
        int b = Math.min(border.bottom(), height - t);
        int cw = tw - border.left() - border.right();
        int ch = th - border.top() - border.bottom();
        int[] xs = {x, x + l, x + width - r};
        int[] ws = {l, width - l - r, r};
        int[] us = {0, border.left(), tw - border.right()};
        int[] uws = {l, cw, r};
        int[] ys = {y, y + t, y + height - b};
        int[] hs = {t, height - t - b, b};
        int[] vs = {0, border.top(), th - border.bottom()};
        int[] vhs = {t, ch, b};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (ws[col] <= 0 || hs[row] <= 0 || uws[col] <= 0 || vhs[row] <= 0) {
                    continue;
                }
                boolean stretch = slice.stretchInner() || (col != 1 && row != 1);
                int pieceW = col == 1 && !stretch ? uws[col] : ws[col];
                int pieceH = row == 1 && !stretch ? vhs[row] : hs[row];
                boolean cut = pieceW < ws[col] || pieceH < hs[row];
                if (cut) {
                    enableScissor(xs[col], ys[row], xs[col] + ws[col], ys[row] + hs[row]);
                }
                for (int py = ys[row]; py < ys[row] + hs[row]; py += pieceH) {
                    for (int px = xs[col]; px < xs[col] + ws[col]; px += pieceW) {
                        sliceOf(sprite, tw, th, us[col], vs[row], uws[col], vhs[row], px, py,
                                stretch || col != 1 ? pieceW : uws[col], stretch || row != 1 ? pieceH : vhs[row], argb);
                    }
                }
                if (cut) {
                    disableScissor();
                }
            }
        }
    }

    /** Part (u, v, uw, vh) of a sprite {@code tw} by {@code th}, drawn over the box w by h at (x, y). */
    private void sliceOf(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int tw, int th, int u, int v,
            int uw, int vh, int x, int y, int w, int h, int argb) {
        net.minecraft.resources.Identifier atlas = sprite.atlasLocation();
        net.minecraft.resources.Identifier id = sprite.contents().name();
        String folder = atlas.getPath().contains("gui") ? "textures/gui/sprites/" : "textures/";
        String name = kiln.Sprites.lookup(net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(),
                folder + id.getPath() + ".png"), u, v, uw, vh, tw, th);
        if (name == null) {
            fill(x, y, x + w, y + h, SPRITE_FALLBACK);
        } else {
            spriteOp(name, x, y, w, h, 0, SPRITE_FALLBACK, argb);
        }
    }

    /**
     * {@code sprite} tiled over the box, each tile {@code spriteWidth} by {@code spriteHeight} from its top left, those
     * at the right and bottom edges cut to the box (vanilla's tiles, as a fluid fills a recipe viewer's tank, D-168);
     * tinted by {@code argb}.
     */
    public void blitTiledSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int x, int y, int width, int height,
            int u, int v, int spriteWidth, int spriteHeight, int nineSliceWidth, int nineSliceHeight, int argb) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int tileW = spriteWidth > 0 ? spriteWidth : width;
        int tileH = spriteHeight > 0 ? spriteHeight : height;
        boolean cut = width % tileW != 0 || height % tileH != 0;
        if (cut) {
            enableScissor(x, y, x + width, y + height);
        }
        for (int ty = 0; ty < height; ty += tileH) {
            for (int tx = 0; tx < width; tx += tileW) {
                atlasSprite(sprite, x + tx, y + ty, tileW, tileH, 0, SPRITE_FALLBACK, argb);
            }
        }
        if (cut) {
            disableScissor();
        }
    }

    /**
     * The part {@code width} by {@code height} from (u, v) of the picture {@code texture} ({@code textureWidth} by
     * {@code textureHeight}) at (x, y): that part cut out as a sprite of its own (kiln.Sprites, D-160); a box of the
     * gui's grey where no jar nor the game's resources have the picture.
     */
    public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight);
    }

    public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight, int argb) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, argb);
    }

    /** The part {@code uWidth} by {@code vHeight} from (u, v), stretched over the box {@code width} by {@code height}. */
    public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier texture, int x, int y, float u, float v, int width, int height,
            int uWidth, int vHeight, int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, -1);
    }

    /** The same, its pixels multiplied by {@code argb} (vanilla's colour). */
    public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier texture, int x, int y, float u, float v, int width, int height,
            int uWidth, int vHeight, int textureWidth, int textureHeight, int argb) {
        String name = kiln.Sprites.lookup(texture, u, v, uWidth, vHeight, textureWidth, textureHeight);
        if (name == null) {
            missing(texture + " (" + u + "," + v + " " + uWidth + "x" + vHeight + " of " + textureWidth + "x"
                    + textureHeight + ")");
            fill(x, y, x + width, y + height, SPRITE_FALLBACK);
        } else {
            spriteOp(name, x, y, width, height, 0, SPRITE_FALLBACK, argb);
        }
    }

    /**
     * The part of {@code texture} from (u0, v0) to (u1, v1), as fractions of its width and height, over the box from
     * (x0, y0) to (x1, y1), its pixels multiplied by {@code argb}: what the {@code blit}s above come to in the game
     * (REI's panels and slots call it themselves, D-217). The part is cut out of the picture as {@link #blit} cuts one.
     */
    public void innerBlit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.resources.Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0,
            float v1, int argb) {
        int scale = 1 << 16;
        int w = Math.round((u1 - u0) * scale), h = Math.round((v1 - v0) * scale);
        String name = kiln.Sprites.lookup(texture, u0 * scale, v0 * scale, w, h, scale, scale);
        if (name == null) {
            missing(texture + " (" + u0 + "," + v0 + " to " + u1 + "," + v1 + ")");
            fill(x0, y0, x1, y1, SPRITE_FALLBACK);
        } else {
            spriteOp(name, x0, y0, x1 - x0, y1 - y0, 0, SPRITE_FALLBACK, argb);
        }
    }

    /**
     * An atlas's sprite by the game's name: a gui sprite as {@link #guiSprite} (a mod's from its jar, D-160), any
     * other as its id.
     */
    private void atlasSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int x, int y,
            int width, int height, int border, int fallback) {
        atlasSprite(sprite, x, y, width, height, border, fallback, -1);
    }

    /**
     * Another atlas's sprite (the blocks': a fluid's {@code block/water_still}) is its picture's first frame, sent to the
     * game by kiln.Sprites (the HUD's atlas has the GUI's sprites only, D-168); the game's own name where none has it.
     */
    private void atlasSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int x, int y,
            int width, int height, int border, int fallback, int tint) {
        if (sprite == null) {
            return;
        }
        net.minecraft.resources.Identifier name = sprite.contents().name();
        String own = sprite.atlasLocation().getNamespace().equals("minecraft") ? null
                : kiln.Sprites.lookup(sprite.atlasLocation(), name);
        if (own != null) {
            // A mod's own atlas (D-170: Mezz Config GUI's): its picture by the name Sprites gives it.
            spriteOp(own, x, y, width, height, border != 0 ? border : nineSliceBorder(
                    net.minecraft.client.resources.metadata.gui.GuiMetadataSection.of(
                            kiln.Sprites.guiMetadata(sprite.atlasLocation(), name)).scaling()), fallback, tint);
            return;
        }
        boolean gui = sprite.atlasLocation().getPath().contains("gui");
        if (gui) {
            guiSprite(name, x, y, width, height, border, fallback, tint);
        } else {
            String sent = kiln.Sprites.lookupAny(sprite.atlasLocation(), name);
            String id = sent != null ? sent : name.getNamespace().equals("minecraft") ? name.getPath() : name.toString();
            spriteOp(id, x, y, width, height, border, fallback, tint);
        }
    }

    private void box(float x0, float y0, float x1, float y1) {
        if (!boxless) {
            boxes.add(new float[] {x0, y0, x1, y1});
        }
    }

    /** Kiln's own: the boxes drawn ({@code x0 y0 x1 y1} each, through the pose), tooltips' left out (D-156). */
    public List<float[]> kiln$boxes() {
        return boxes;
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
        if (deferredTooltip != null) {
            Runnable tooltip = deferredTooltip;
            deferredTooltip = null;
            pose.clear();
            tooltip.run();
        }
        kiln$joinShared();
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
    /** The widget's text: drawn as {@link #textRenderer(HoveredTextEffects)} draws it. */
    public ActiveTextCollector textRendererForWidget(net.minecraft.client.gui.components.AbstractWidget widget,
            HoveredTextEffects effects) {
        return textRenderer(effects);
    }

    /** Where a widget sends its text to be drawn here (in white, through the pose in force). */
    public ActiveTextCollector textRenderer() {
        return textRenderer(HoveredTextEffects.TOOLTIP_ONLY);
    }

    public ActiveTextCollector textRenderer(HoveredTextEffects effects) {
        return textRenderer(effects, null);
    }

    public ActiveTextCollector textRenderer(HoveredTextEffects effects,
            java.util.function.Consumer<net.minecraft.network.chat.Style> hovered) {
        return new RenderingTextCollector(new ActiveTextCollector.Parameters(new org.joml.Matrix3x2f(pose)));
    }

    /** Lines as a tooltip by the pointer, in the game's font. */
    public void setTooltipForNextFrame(List<net.minecraft.util.FormattedCharSequence> lines, int x, int y) {
        setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, lines, x, y);
    }

    /**
     * What {@link #textRenderer()} gives: each line drawn here with the game's font, its alpha from the opacity, cut
     * to the parameters' box. Their pose is the extractor's when it was made; Kiln draws through the pose in force.
     */
    public class RenderingTextCollector implements ActiveTextCollector,
            java.util.function.Consumer<net.minecraft.network.chat.Style> {
        private ActiveTextCollector.Parameters parameters;

        RenderingTextCollector(ActiveTextCollector.Parameters parameters) {
            this.parameters = parameters;
        }

        /** Kiln's own: a collector with the extractor's pose (a generated subclass's). */
        protected RenderingTextCollector() {
            this(new ActiveTextCollector.Parameters(new org.joml.Matrix3x2f()));
        }

        @Override
        public ActiveTextCollector.Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(ActiveTextCollector.Parameters parameters) {
            this.parameters = parameters;
        }

        /** The style under the pointer: Kiln shows no hover effect of a text's own. */
        @Override
        public void accept(net.minecraft.network.chat.Style style) {}

        @Override
        public void accept(TextAlignment alignment, int x, int y, ActiveTextCollector.Parameters parameters,
                net.minecraft.util.FormattedCharSequence text) {
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            int alpha = Math.round(Math.max(0.0F, Math.min(1.0F, parameters.opacity())) * 255.0F);
            if (alpha == 0 || text == null) {
                return;
            }
            net.minecraft.client.gui.navigation.ScreenRectangle cut = parameters.scissor();
            if (cut != null) {
                enableScissor(cut.left(), cut.top(), cut.right(), cut.bottom());
            }
            text(font, text, alignment.calculateLeft(x, font, text), y, alpha << 24 | 0xFFFFFF, true);
            if (cut != null) {
                disableScissor();
            }
        }

        @Override
        public void acceptScrolling(net.minecraft.network.chat.Component text, int centerX, int left, int right,
                int top, int bottom, ActiveTextCollector.Parameters parameters) {
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            defaultScrollingHelper(text, centerX, left, right, top, bottom, font.width(text), 9, parameters);
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.renderer.state.gui.GuiRenderState guiRenderState;
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.Minecraft minecraft;
    public java.lang.Object scissorStack;
    public net.minecraft.client.resources.model.sprite.SpriteGetter sprites;
    public net.minecraft.client.renderer.texture.TextureAtlas guiSprites;
    public com.mojang.blaze3d.platform.cursor.CursorType pendingCursor;
    public int mouseX;
    public int mouseY;
    public net.minecraft.network.chat.Style hoveredTextStyle;
    public net.minecraft.network.chat.Style clickableTextStyle;
    public net.minecraft.client.gui.components.Renderable preeditOverlay;
    public void innerFill(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, java.lang.Object p1, int p2, int p3, int p4, int p5, int p6, java.lang.Integer p7) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.innerFill"); }
    public void blitNineSliceInnerSegment(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice p1, net.minecraft.client.renderer.texture.TextureAtlasSprite p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9, int p10, int p11, int p12, int p13) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.blitNineSliceInnerSegment"); }
    public void innerBlit(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, com.mojang.renderpearl.api.textures.GpuTextureView p1, com.mojang.renderpearl.api.textures.GpuSampler p2, int p3, int p4, int p5, int p6, float p7, float p8, float p9, float p10, int p11) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.innerBlit"); }
    public void innerTiledBlit(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, com.mojang.renderpearl.api.textures.GpuTextureView p1, com.mojang.renderpearl.api.textures.GpuSampler p2, int p3, int p4, int p5, int p6, int p7, int p8, float p9, float p10, float p11, float p12, int p13) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.innerTiledBlit"); }
    public void item(net.minecraft.world.entity.LivingEntity p0, net.minecraft.world.level.Level p1, net.minecraft.world.item.ItemStack p2, int p3, int p4, int p5) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.item"); }
    public void itemBar(net.minecraft.world.item.ItemStack p0, int p1, int p2) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.itemBar"); }
    public void itemCount(net.minecraft.client.gui.Font p0, net.minecraft.world.item.ItemStack p1, int p2, int p3, java.lang.String p4) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.itemCount"); }
    public void itemCooldown(net.minecraft.world.item.ItemStack p0, int p1, int p2) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.itemCooldown"); }
    public void setTooltipForNextFrameInternal(net.minecraft.client.gui.Font p0, java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> p1, int p2, int p3, net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner p4, net.minecraft.resources.Identifier p5, boolean p6, boolean p7) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.setTooltipForNextFrameInternal"); }
    public void componentHoverEffect(net.minecraft.client.gui.Font p0, net.minecraft.network.chat.Style p1, int p2, int p3) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.componentHoverEffect"); }
    public java.lang.Object createDefaultTextParameters(float p0) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.createDefaultTextParameters"); return null; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void innerFill(com.mojang.renderpearl.api.pipeline.RenderPipeline p0, net.minecraft.client.gui.render.TextureSetup p1, int p2, int p3, int p4, int p5, int p6, java.lang.Integer p7) { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.innerFill"); }
    public static class ScissorStack {
        public ScissorStack(net.minecraft.client.gui.navigation.ScreenRectangle p0) {  }
        public net.minecraft.client.gui.navigation.ScreenRectangle peek() { kiln.Missing.called("net.minecraft.client.gui.GuiGraphicsExtractor.ScissorStack.peek"); return new net.minecraft.client.gui.navigation.ScreenRectangle((net.minecraft.client.gui.navigation.ScreenPosition) null, (int) 0, (int) 0); }
        protected ScissorStack() {  }
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static enum HoveredTextEffects {
        NONE, TOOLTIP_ONLY, TOOLTIP_AND_CURSOR;
    }
    // End of what tools/kiln-stubs.py generated.
}
