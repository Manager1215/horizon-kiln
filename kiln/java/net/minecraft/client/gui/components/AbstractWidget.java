package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Kiln's stand-in for {@code AbstractWidget}: a widget of a mod's screen, drawn through Kiln's ops and taking the
 * screen's mouse and keys as vanilla's does (a left click inside it while active and visible, then its drag and
 * release). Narration is left out: Kiln has no narrator.
 */
public abstract class AbstractWidget implements LayoutElement, Renderable, GuiEventListener {
    private int x;
    private int y;
    protected int width;
    protected int height;
    protected Component message;
    protected boolean isHovered;
    public boolean active = true;
    public boolean visible = true;
    protected float alpha = 1.0F;
    private boolean focused;
    private Tooltip tooltip;
    private int tabOrderGroup;

    public AbstractWidget(int x, int y, int width, int height, Component message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen), a widget of no size at (0, 0). */
    protected AbstractWidget() {
        this(0, 0, 0, 0, Component.empty());
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        isHovered = graphics.containsPointInScissor(mouseX, mouseY) && mouseX >= x && mouseY >= y
                && mouseX < x + width && mouseY < y + height;
        extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        if (isHovered && tooltip != null) {
            graphics.kiln$tooltip(tooltip.kiln$message(), mouseX, mouseY);
        }
    }

    protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick);

    public void setTooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    public Tooltip getTooltip() {
        return tooltip;
    }

    public void setTooltipDelay(java.time.Duration delay) {}

    protected MutableComponent createNarrationMessage() {
        return wrapDefaultNarrationMessage(getMessage());
    }

    public static MutableComponent wrapDefaultNarrationMessage(Component message) {
        return Component.translatable("gui.narrate.button", message);
    }

    public void onClick(MouseButtonEvent event, boolean doubleClick) {}

    public void onRelease(MouseButtonEvent event) {}

    protected void onDrag(MouseButtonEvent event, double dx, double dy) {}

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isValidClickButton(event.buttonInfo()) || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        onClick(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isValidClickButton(event.buttonInfo())) {
            onRelease(event);
            return true;
        }
        return false;
    }

    protected boolean isValidClickButton(MouseButtonInfo button) {
        return button.button() == 1;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (isValidClickButton(event.buttonInfo())) {
            onDrag(event, dx, dy);
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive() && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public void playDownSound(SoundManager sounds) {
        playButtonClickSound(sounds);
    }

    public static void playButtonClickSound(SoundManager sounds) {
        kiln.Native.sound("minecraft:ui.button.click", null, 0.25F, 1.0F);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setRectangle(int width, int height, int x, int y) {
        setSize(width, height);
        setPosition(x, y);
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    public Component getMessage() {
        return message;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isHoveredOrFocused() {
        return isHovered || focused;
    }

    public boolean isActive() {
        return visible && active;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    public int getRight() {
        return x + width;
    }

    public int getBottom() {
        return y + height;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitor.accept(this);
    }

    @Override
    public int getTabOrderGroup() {
        return tabOrderGroup;
    }

    public void setTabOrderGroup(int group) {
        tabOrderGroup = group;
    }

    /** Kiln's own: the widget's text in one colour, centred in it and cut to it less {@code margin} each side. */
    protected void kiln$centredLabel(GuiGraphicsExtractor graphics, int margin, int argb) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        String text = getMessage() == null ? "" : getMessage().getString();
        int left = getX() + margin;
        int right = getRight() - margin;
        int textWidth = font.width(text);
        int textX = textWidth > right - left ? left : (left + right - textWidth) / 2;
        graphics.enableScissor(left, getY(), right, getBottom());
        graphics.text(font, text, textX, getY() + (getHeight() - 8) / 2, argb, true);
        graphics.disableScissor();
    }

    /** {@code AbstractWidget.WithInactiveMessage}: a widget whose text greys out while inactive. */
    public abstract static class WithInactiveMessage extends AbstractWidget {
        public WithInactiveMessage(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        protected WithInactiveMessage() {}
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public net.minecraft.network.chat.Component inactiveMessage;
        // End of what tools/kiln-stubs.py generated.
    }
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
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput p0) { kiln.Missing.called("net.minecraft.client.gui.components.AbstractWidget.updateNarration"); }
    // End of what tools/kiln-stubs.py generated.
}
