package net.minecraft.client.resources.language;

/** Kiln's stand-in for {@code LanguageManager}: the language the game shows ({@code en_us}), as it said. */
public class LanguageManager {
    private String selected = "en_us";

    public String getSelected() {
        return selected;
    }

    /** Kiln's own: the language the game said. */
    public void kiln$selected(String code) {
        if (code != null && !code.isEmpty()) {
            selected = code;
        }
    }
}
