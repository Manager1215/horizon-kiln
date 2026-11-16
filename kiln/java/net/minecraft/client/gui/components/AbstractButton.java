package net.minecraft.client.gui.components;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code AbstractButton}: vanilla's button sprite, pressed by a click, Enter or Space. */
public abstract class AbstractButton extends AbstractWidget.WithInactiveMessage {
    protected static final int TEXT_MARGIN = 2;
    private Supplier<Boolean> highlighted = () -> false;

    public AbstractButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen). */
    protected AbstractButton() {}

    public abstract void onPress(InputWithModifiers input);

    @Override
    protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        extractContents(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    /** Vanilla's button sprite: greyed while inactive, highlighted while hovered or focused. */
    protected final void extractDefaultSprite(GuiGraphicsExtractor graphics) {
        String sprite = !active ? "widget/button_disabled"
                : isHoveredOrFocused() || highlighted.get() ? "widget/button_highlighted" : "widget/button";
        int fallback = !active ? 0xFF2A2A2A : isHoveredOrFocused() ? 0xFF7A7A7A : 0xFF5A5A5A;
        graphics.kiln$sprite(sprite, getX(), getY(), getWidth(), getHeight(), 3, fallback);
    }

    /** Kiln's own: the button's text as vanilla draws it, white, or grey while inactive. */
    protected void kiln$defaultLabel(GuiGraphicsExtractor graphics) {
        kiln$centredLabel(graphics, TEXT_MARGIN, active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isActive() || !event.isSelection()) {
            return false;
        }
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        onPress(event);
        return true;
    }

    public void setOverrideRenderHighlightedSprite(Supplier<Boolean> highlighted) {
        this.highlighted = highlighted;
    }
}
