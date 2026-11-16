package net.minecraft.util;

import java.util.regex.Pattern;

/**
 * Kiln's stand-in for {@code FileUtil}: the game's rules for a file name made from a name (a world's, a server's), as a
 * mod keeps its files per world (a recipe viewer's bookmarks, D-167).
 */
public class FileUtil {
    private static final Pattern RESERVED_WINDOWS_FILENAMES =
            Pattern.compile(".*\\.|(?:COM|CLOCK\\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", Pattern.CASE_INSENSITIVE);
    private static final String ILLEGAL = "/\n\r\t\u0000\f`?*\\<>|\":";

    public FileUtil() {}

    /** The name with each character a file name can't hold made {@code _}. */
    public static String sanitizeName(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            out.append(ILLEGAL.indexOf(c) >= 0 ? '_' : c);
        }
        return out.toString();
    }

    /** Whether the name is a file name on every system (not one Windows keeps for itself, no illegal character). */
    public static boolean isPathPartPortable(String part) {
        if (RESERVED_WINDOWS_FILENAMES.matcher(part).matches()) {
            return false;
        }
        for (char c : part.toCharArray()) {
            if (ILLEGAL.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }
}
