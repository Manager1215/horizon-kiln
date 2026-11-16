package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Kiln's stand-in for {@code AbstractSliderButton}: vanilla's slider, its value from 0 to 1 set by a click or a drag
 * along it, or the arrow keys while focused; the mod's {@link #applyValue} hears each change and {@link
 * #updateMessage} sets its text.
 */
public abstract class AbstractSliderButton extends AbstractWidget.WithInactiveMessage {
    protected static final int TEXT_MARGIN = 2;
    public static final int DEFAULT_HEIGHT = 20;
    protected static final int HANDLE_WIDTH = 8;
    protected double value;
    protected boolean canChangeValue;

    public AbstractSliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message);
        this.value = value;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.slider", getMessage());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean lit = isFocused() && !canChangeValue;
        graphics.kiln$sprite(lit ? "widget/slider_highlighted" : "widget/slider", getX(), getY(), getWidth(),
                getHeight(), 3, 0xFF000000);
        boolean handleLit = isHoveredOrFocused() && active;
        graphics.kiln$sprite(handleLit ? "widget/slider_handle_highlighted" : "widget/slider_handle",
                getX() + (int) (value * (double) (width - HANDLE_WIDTH)), getY(), HANDLE_WIDTH, getHeight(), 3,
                handleLit ? 0xFFFFFFFF : 0xFFA0A0A0);
        kiln$centredLabel(graphics, TEXT_MARGIN, active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setValueFromMouse(event);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            canChangeValue = false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isSelection()) {
            canChangeValue = !canChangeValue;
            return true;
        }
        if (canChangeValue && (event.isLeft() || event.isRight())) {
            setValue(value + (event.isLeft() ? -1.0F : 1.0F) / (float) (width - HANDLE_WIDTH));
            return true;
        }
        return false;
    }

    private void setValueFromMouse(MouseButtonEvent event) {
        setValue((event.x() - (double) (getX() + HANDLE_WIDTH / 2)) / (double) (width - HANDLE_WIDTH));
    }

    protected void setValue(double value) {
        double old = this.value;
        this.value = Math.clamp(value, 0.0, 1.0);
        if (old != this.value) {
            applyValue();
        }
        updateMessage();
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        setValueFromMouse(event);
        super.onDrag(event, dx, dy);
    }

    @Override
    public void playDownSound(SoundManager sounds) {}

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
    }

    protected abstract void updateMessage();

    protected abstract void applyValue();
}
