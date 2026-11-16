package net.minecraft.client.input;

/** Kiln's stand-in for {@code MouseButtonInfo}: a mouse button (1 left, 2 middle, 3 right) and the modifiers held. */
public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers {
    @Override
    public int input() {
        return button;
    }
}
