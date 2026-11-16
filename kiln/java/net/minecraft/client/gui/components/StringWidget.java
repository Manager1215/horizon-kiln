package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code StringWidget}: a line of text, centred in its box, cut to it when too long. */
public class StringWidget extends AbstractStringWidget {
    private int maxWidth = -1;

    public StringWidget(Component message, Font font) {
        this(0, 0, font.width(message.getString()), font.lineHeight, message, font);
    }

    public StringWidget(int width, int height, Component message, Font font) {
        this(0, 0, width, height, message, font);
    }

    public StringWidget(int x, int y, int width, int height, Component message, Font font) {
        super(x, y, width, height, message, font);
    }

    public StringWidget setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    @Override
    public int getWidth() {
        return maxWidth > 0 ? Math.min(maxWidth, width) : width;
    }

    @Override
    protected void kiln$drawText(GuiGraphicsExtractor graphics) {
        String text = getMessage() == null ? "" : getMessage().getString();
        int textWidth = getFont().width(text);
        int x = textWidth > getWidth() ? getX() : getX() + (getWidth() - textWidth) / 2;
        int y = getY() + (getHeight() - getFont().lineHeight + 1) / 2;
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + Math.max(getHeight(), getFont().lineHeight));
        graphics.text(getFont(), text, x, y, kiln$color(), true);
        graphics.disableScissor();
    }
}
