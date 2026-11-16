package net.minecraft.client.input;

/** Kiln's stand-in for {@code KeyEvent}: a key by its scancode ({@code InputConstants}), with the modifiers held. */
public record KeyEvent(int key, int keycode, int modifiers) implements InputWithModifiers {
    @Override
    public int input() {
        return key;
    }
}
