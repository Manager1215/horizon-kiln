package net.minecraft.client.input;

/**
 * Kiln's stand-in for {@code InputQuirks}: how the game's keys and mouse differ by system, as vanilla's are: on macOS
 * Command stands for Control in shortcuts, Shift turns the wheel sideways and Control with a click is a right click.
 * Modifiers are SDL's bits, as Kiln's input events carry them ({@link InputWithModifiers}, D-094).
 */
public class InputQuirks {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
    public static final boolean REPLACE_CTRL_KEY_WITH_CMD_KEY = MAC;
    /** The system key (0xC00) on macOS, Control (0xC0) elsewhere. */
    public static final int EDIT_SHORTCUT_KEY_MODIFIER = MAC ? 0xC00 : 0xC0;
    public static final boolean SHIFT_INVERTS_SCROLL_AXIS = MAC;
    public static final boolean EMULATE_RIGHT_CLICK_WITH_CTRL_KEY = MAC;
    public static final boolean RESTORE_KEY_STATE_AFTER_MOUSE_GRAB = MAC;

    public InputQuirks() {}

    /** The game quits on its own shortcut; Kiln's mods never see it held. */
    public static boolean isQuitShortcutDown() {
        return false;
    }

    /** A key's translation key: the same on every system here (Kiln's language files have no macOS variants). */
    public static String keyboardTranslationKey(String key) {
        return key;
    }
}
