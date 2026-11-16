package net.minecraft.client.gui.components;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Kiln's stand-in for {@code Button}: vanilla's button, its text centred on its sprite, {@link OnPress} when pressed. */
public abstract class Button extends AbstractButton {
    public static final int SMALL_WIDTH = 120;
    public static final int DEFAULT_WIDTH = 150;
    public static final int BIG_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 20;
    public static final int DEFAULT_SPACING = 8;
    protected static final CreateNarration DEFAULT_NARRATION = Supplier::get;
    protected final OnPress onPress;
    protected final CreateNarration createNarration;

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    protected Button(int x, int y, int width, int height, Component message, OnPress onPress,
            CreateNarration createNarration) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.createNarration = createNarration;
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen), a button that does nothing. */
    protected Button() {
        this.onPress = button -> {};
        this.createNarration = DEFAULT_NARRATION;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.onPress(this);
    }

    /** Vanilla's button sprite and its text, centred. */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        kiln$defaultLabel(graphics);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return createNarration.createNarrationMessage(super::createNarrationMessage);
    }

    /** The plain button {@link Builder#build} makes. */
    public static class Plain extends Button {
        protected Plain(int x, int y, int width, int height, Component message, OnPress onPress,
                CreateNarration createNarration) {
            super(x, y, width, height, message, onPress, createNarration);
        }
    }

    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private Tooltip tooltip;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private CreateNarration createNarration = DEFAULT_NARRATION;

        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder createNarration(CreateNarration createNarration) {
            this.createNarration = createNarration;
            return this;
        }

        public Button build() {
            Button button = new Plain(x, y, width, height, message, onPress, createNarration);
            button.setTooltip(tooltip);
            return button;
        }
    }

    public interface OnPress {
        void onPress(Button button);
    }

    public interface CreateNarration {
        MutableComponent createNarrationMessage(Supplier<MutableComponent> defaultMessage);
    }
}
