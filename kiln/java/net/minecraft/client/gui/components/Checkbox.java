package net.minecraft.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code Checkbox}: vanilla's box sprite, ticked or not, its label beside it. */
public class Checkbox extends AbstractButton {
    private static final int SPACING = 4;
    private boolean selected;
    private final OnValueChange onValueChange;

    Checkbox(int x, int y, int maxWidth, Component message, Font font, boolean selected, OnValueChange onValueChange) {
        super(x, y, 0, getBoxSize(font), message);
        this.selected = selected;
        this.onValueChange = onValueChange;
        this.width = adjustWidth(maxWidth, font);
    }

    public int adjustWidth(int maxWidth, Font font) {
        int wanted = getBoxSize(font) + SPACING + font.width(getMessage() == null ? "" : getMessage().getString());
        return Math.min(wanted, maxWidth);
    }

    public static Builder builder(Component message, Font font) {
        return new Builder(message, font);
    }

    public static int getBoxSize(Font font) {
        return font.lineHeight + 8;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        selected = !selected;
        onValueChange.onValueChange(this, selected);
    }

    public boolean selected() {
        return selected;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int box = getBoxSize(font);
        String sprite = (selected ? "widget/checkbox_selected" : "widget/checkbox")
                + (isHoveredOrFocused() ? "_highlighted" : "");
        graphics.kiln$sprite(sprite, getX(), getY(), box, box, 1, selected ? 0xFFE0E0E0 : 0xFF6F6F6F);
        String text = getMessage() == null ? "" : getMessage().getString();
        graphics.text(font, text, getX() + box + SPACING, getY() + (box - 8) / 2, 0xFFE0E0E0, true);
    }

    public static class Builder {
        private final Component message;
        private final Font font;
        private int x;
        private int y;
        private int maxWidth;
        private OnValueChange onValueChange = OnValueChange.NOP;
        private boolean selected;
        private Tooltip tooltip;

        Builder(Component message, Font font) {
            this.message = message;
            this.font = font;
            this.maxWidth = Integer.MAX_VALUE;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder onValueChange(OnValueChange onValueChange) {
            this.onValueChange = onValueChange;
            return this;
        }

        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder maxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Checkbox build() {
            Checkbox box = new Checkbox(x, y, maxWidth, message, font, selected, onValueChange);
            box.setTooltip(tooltip);
            return box;
        }
    }

    public interface OnValueChange {
        OnValueChange NOP = (box, selected) -> {};

        void onValueChange(Checkbox box, boolean selected);
    }
}
