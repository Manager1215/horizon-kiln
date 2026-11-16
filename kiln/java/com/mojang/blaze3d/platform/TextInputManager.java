package com.mojang.blaze3d.platform;

/**
 * Kiln's stand-in for {@code TextInputManager}: the window's text input (an input method's composition) as a field
 * gains or loses the focus. The game's window does its own; what a mod's field asks here changes nothing (D-170).
 */
public class TextInputManager {
    public TextInputManager() {}

    public void startTextInput(Object owner) {}

    public void stopTextInput(Object owner) {}

    public void onTextInputFocusChange(Object owner, boolean focused) {}
}
