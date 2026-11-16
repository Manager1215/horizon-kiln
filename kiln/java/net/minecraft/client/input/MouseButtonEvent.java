package net.minecraft.client.input;

/** Kiln's stand-in for {@code MouseButtonEvent}: a mouse button at (x, y) in GUI pixels. */
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
    @Override
    public int input() {
        return buttonInfo.button();
    }

    public int button() {
        return buttonInfo.button();
    }

    @Override
    public int modifiers() {
        return buttonInfo.modifiers();
    }
}
