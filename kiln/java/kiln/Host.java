package kiln;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kiln's Java side, a process of its own beside the game: the game (Kiln's Rust side) writes commands to its
 * stdin, one a line, and reads replies from its stdout. The mods' own {@code System.out} goes to stderr
 * (Kiln's java.log), so a mod can't break the protocol. It ends when the game closes its stdin.
 *
 * <p>A line is a word and tab-separated fields; backslash, tab, newline and carriage return in a field are
 * escaped as {@code \\ \t \n \r}. Commands:
 * <ul>
 *   <li>{@code load <class>}: run a mod's client entrypoint;
 *   <li>{@code font <w0,w1,...>}: the game font's width of each character, by code;
 *   <li>{@code key <name>}: a key went down while playing ({@code key.keyboard.j});
 *   <li>{@code adv <id>}: the player has made an advancement (all of them again after entering a world);
 *   <li>{@code tick <n> <connected 0|1> <in world 0|1> <paused 0|1> <game time> <dimension> <gui width> <gui
 *       height>}: a client tick, 20 a second, in a world or not. Connected means not on the title screen; a
 *       connected tick with no player is Horizon loading a dimension.
 * </ul>
 * Replies: {@code ready <java version>}, {@code loaded <class>}, {@code failed <class> <why>}, {@code log <text>},
 * {@code chat <text>}, {@code draw <op>...} (the mods' HUD, when it changed: {@code f x y w h argb} and
 * {@code t x y argb shadow text}), and {@code done <command>} after each command.
 */
public final class Host {
    private static PrintStream out;
    private static List<String> drawn = List.of();

    private Host() {}

    public static void main(String[] args) throws IOException {
        out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(System.err);
        send("ready", Loader.javaVersion());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = in.readLine()) != null; ) {
            String[] fields = line.split("\t", -1);
            for (int i = 1; i < fields.length; i++) {
                fields[i] = unescape(fields[i]);
            }
            try {
                command(fields);
            } catch (RuntimeException | LinkageError e) {
                // LinkageError: a mod called a class or method Kiln has no stand-in for (NoSuchMethodError,
                // NoClassDefFoundError). It stops that call, not Java and every other mod.
                send("log", "command " + fields[0] + " failed: " + e);
            }
            send("done", fields[0]);
        }
    }

    private static void command(String[] f) {
        Minecraft mc = Minecraft.getInstance();
        switch (f[0]) {
            case "load" -> {
                String failed = Loader.load(f[1]);
                if (failed == null) {
                    send("loaded", f[1]);
                } else {
                    send("failed", f[1], failed);
                }
            }
            case "font" -> {
                String[] parts = f[1].split(",");
                int[] widths = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    widths[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
                }
                mc.font.kiln$widths(widths);
            }
            case "key" -> KeyMappingHelper.kiln$pressed(f[1]);
            case "adv" -> mc.kiln$connection().getAdvancements().kiln$done(f[1]);
            case "tick" -> tick(mc, f);
            default -> send("log", "unknown command " + f[0]);
        }
    }

    private static void tick(Minecraft mc, String[] f) {
        boolean inWorld = f[3].equals("1");
        mc.kiln$update(f[2].equals("1"), inWorld, f[4].equals("1"), Long.parseLong(f[5]), f[6]);
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(mc);
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(mc);
        List<String> ops = List.of();
        if (inWorld) {
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Integer.parseInt(f[7]), Integer.parseInt(f[8]));
            for (HudElement element : HudElementRegistry.kiln$elements()) {
                try {
                    element.extractRenderState(graphics, DeltaTracker.ZERO);
                } catch (RuntimeException | LinkageError e) {
                    send("log", "a HUD element threw " + e);
                }
            }
            ops = new ArrayList<>(graphics.kiln$ops());
        }
        if (!ops.equals(drawn)) {
            drawn = ops;
            send("draw", ops.toArray(String[]::new));
        }
    }

    /** A reply line, sent at once. */
    static synchronized void send(String kind, String... fields) {
        StringBuilder b = new StringBuilder(kind);
        for (String f : fields) {
            b.append('\t').append(escape(f));
        }
        out.print(b.append('\n'));
        out.flush();
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(switch (n) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
