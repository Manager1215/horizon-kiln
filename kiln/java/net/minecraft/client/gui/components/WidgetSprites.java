package net.minecraft.client.gui.components;

import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code WidgetSprites}: a widget's sprites, enabled or not, focused or not. A shorter constructor
 * repeats the sprites it has for the rest; REI draws its buttons with them (D-215).
 */
public record WidgetSprites(Identifier enabled, Identifier disabled, Identifier enabledFocused,
        Identifier disabledFocused) {
    public WidgetSprites(Identifier sprite) {
        this(sprite, sprite, sprite, sprite);
    }

    public WidgetSprites(Identifier sprite, Identifier focused) {
        this(sprite, sprite, focused, sprite);
    }

    public WidgetSprites(Identifier enabled, Identifier disabled, Identifier focused) {
        this(enabled, disabled, focused, disabled);
    }

    public Identifier get(boolean isEnabled, boolean isFocused) {
        return isEnabled ? (isFocused ? enabledFocused : enabled) : (isFocused ? disabledFocused : disabled);
    }
}
