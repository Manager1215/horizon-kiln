import com.mojang.blaze3d.platform.InputConstants;

/**
 * The names Kiln's {@code InputConstants} stand-in gives key codes, a line each ({@code KEYBOARD 99
 * key.keyboard.keypad.period}, then the mouse buttons), for kiln/tests/key_names.rs to compare with Horizon's. It
 * runs as a source file with kiln-runtime.jar on the class path: {@code java -cp kiln-runtime.jar KeyNames.java}.
 */
public class KeyNames {
    public static void main(String[] args) {
        for (int code = 0; code < 512; code++) {
            String name = InputConstants.kiln$name(InputConstants.Type.KEYBOARD, code);
            if (name != null) {
                System.out.println("KEYBOARD " + code + " " + name);
            }
        }
        for (int button = 1; button <= 8; button++) {
            System.out.println("MOUSE " + button + " " + InputConstants.kiln$name(InputConstants.Type.MOUSE, button));
        }
    }
}
