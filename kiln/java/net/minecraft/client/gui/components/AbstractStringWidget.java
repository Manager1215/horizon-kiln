package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code AbstractStringWidget}: a widget that is only text, which a click goes through. */
public abstract class AbstractStringWidget extends AbstractWidget {
    private final Font font;
    private int color = 0xFFFFFFFF;

    public AbstractStringWidget(int x, int y, int width, int height, Component message, Font font) {
        super(x, y, width, height, message);
        this.font = font;
        this.active = false;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        kiln$drawText(graphics);
    }

    /** Kiln's own: its text, as the widget lays it out. */
    protected abstract void kiln$drawText(GuiGraphicsExtractor graphics);

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {}

    protected final Font getFont() {
        return font;
    }

    /** Kiln's own: the colour it draws in. */
    protected int kiln$color() {
        return color;
    }

    public AbstractStringWidget setColor(int color) {
        this.color = color;
        return this;
    }
}
