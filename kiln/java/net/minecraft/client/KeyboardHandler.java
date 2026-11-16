package net.minecraft.client;

/**
 * Kiln's stand-in for {@code KeyboardHandler}: the keyboard as the game handles it. Horizon handles the keys; the mods'
 * mixins in {@link #keyPress} run at Horizon's {@code key} hook (Kiln's Mixin bridge, D-102).
 */
public class KeyboardHandler {
    private final Minecraft minecraft;

    public KeyboardHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    protected KeyboardHandler() {
        this(Minecraft.getInstance());
    }

    /** A key went down (action 1) or up (0) while playing. */
    public void keyPress(long window, int action, net.minecraft.client.input.KeyEvent event) {
        kiln.Bridge.body(null, window, action, event);
    }

    private static volatile String clipboard = "";

    /**
     * The system's clipboard, as the game last read it for Kiln: with each paste key over a mod's text field (Control+V,
     * on macOS Command+V), and what a mod copied since (D-174). Empty when it holds no text.
     */
    public String getClipboard() {
        return clipboard;
    }

    /** Text onto the system's clipboard, through the game's own; empty text is not copied, as vanilla's. */
    public void setClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        clipboard = text;
        kiln.Screens.copied(text);
    }

    /** Kiln's own: the clipboard's text, as the game read it. */
    public static void kiln$clipboard(String text) {
        clipboard = text == null ? "" : text;
    }
}
