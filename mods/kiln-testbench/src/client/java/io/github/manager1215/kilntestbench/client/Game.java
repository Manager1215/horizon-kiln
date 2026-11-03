package io.github.manager1215.kilntestbench.client;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Skip;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * What probes start from. It names only classes Kiln stands in for, so it loads under Kiln too; the probes name the
 * rest.
 */
final class Game {
    /** The HUD's drawing, while the HUD probes run. */
    static GuiGraphicsExtractor graphics;
    static DeltaTracker delta;

    private Game() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static LocalPlayer player() {
        LocalPlayer player = mc().player;
        if (player == null) {
            throw new Skip("no player");
        }
        return player;
    }

    static ClientLevel level() {
        ClientLevel level = mc().level;
        if (level == null) {
            throw new Skip("no level");
        }
        return level;
    }

    static GuiGraphicsExtractor graphics() {
        if (graphics == null) {
            throw new Skip("not drawing");
        }
        return graphics;
    }

    /** The value, when it is what the probe expects; else the probe fails. */
    static <T> T expect(T value, Object expected) {
        if (!String.valueOf(value).equals(String.valueOf(expected))) {
            throw new AssertionError("expected " + expected + ", got " + value);
        }
        return value;
    }

    static void heard(String event) {
        Bench.HEARD.add(event);
    }

    /** For an event's second probe: it passes when the event's listener ran. */
    static String fired(String event) {
        if (!Bench.HEARD.contains(event)) {
            throw new AssertionError(event + " never fired");
        }
        return "fired";
    }
}
