package com.mojang.blaze3d.platform;

/**
 * Kiln's stand-in for {@code MacosUtil}: whether the game runs on macOS, as vanilla's says (JEI names the Command key
 * "Cmd" there). Vanilla's window helpers on macOS are the game's (Horizon's), not here.
 */
public final class MacosUtil {
    public static final boolean IS_MACOS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .startsWith("mac");

    private MacosUtil() {}
}
