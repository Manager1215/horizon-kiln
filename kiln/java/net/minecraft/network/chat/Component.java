package net.minecraft.network.chat;

import com.google.gson.JsonObject;

/**
 * Kiln's stand-in for {@code Component}: chat text, literal or translated, with a colour and styles. Horizon gets it
 * as vanilla's JSON ({@link #kiln$json}) and shows it in the player's language; {@link #getString} is English.
 */
public interface Component {
    /** The text as the player reads it (in English, from the mods' own language files). */
    String getString();

    /** Kiln's own: the text as vanilla's JSON form, {@code {"translate":"...","color":"dark_red"}}. */
    JsonObject kiln$json();

    static MutableComponent literal(String text) {
        return MutableComponent.kiln$literal(text);
    }

    static MutableComponent translatable(String key) {
        return MutableComponent.kiln$translatable(key, new Object[0]);
    }

    static MutableComponent translatable(String key, Object... args) {
        return MutableComponent.kiln$translatable(key, args);
    }

    static MutableComponent empty() {
        return MutableComponent.kiln$literal("");
    }
}
