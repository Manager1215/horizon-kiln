package kiln;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 *   <li>{@code load <class> [main|client]}: run a mod's entrypoint (client when not said); the game sends every
 *       mod's main entrypoints first, as Fabric runs them;
 *   <li>{@code font <w0,w1,...>}: the game font's width of each character, by code;
 *   <li>{@code key <name>}: a key went down while playing ({@code key.keyboard.j});
 *   <li>{@code adv <id>}: the player has made an advancement (all of them again after entering a world);
 *   <li>{@code tick <n> <connected 0|1> <in world 0|1> <paused 0|1> <game time> <dimension> <gui width> <gui
 *       height> <fps>}: a client tick, 20 a second, in a world or not. Connected means not on the title screen; a
 *       connected tick with no player is Horizon loading a dimension;
 *   <li>{@code use}, {@code join}, {@code respawn}, {@code use_block}, {@code random_tick}, {@code step_on},
 *       {@code block_tick}, {@code neighbor}, {@code placement}, {@code placed}, {@code destroy <call> ...}: the
 *       integrated server asks the mods what using their item or block, a player coming into a level or respawning, or
 *       what happens to their block (a tick, a neighbour, placing, breaking) does, and waits for the answer ({@link
 *       Server} has the fields); during it, Java may ask the game ({@code ask}), and the game answers ({@code answer
 *       <call> <value>});
 *   <li>{@code loot [<id>...]}: the mods' changes to the game's loot tables ({@link Loot}).
 * </ul>
 * Replies: {@code ready <java version>}, {@code loaded <class>}, {@code failed <class> <why>}, {@code items <id>...}
 * (the mods' items with a use of their own), {@code blocks <id> <what>...} (their blocks with code of their own) and
 * {@code listens <event>...} (the server events they hear), each when that changed, {@code loot <id> <pools>} for
 * each loot table the mods added to, {@code log <text>}, {@code chat <text>}, {@code draw <op>...} (the mods' HUD,
 * when it changed: {@code f x y w h argb} and {@code t x y argb shadow text}), {@code ask <call> <what>...} during a
 * server call, {@code return <call> <result> <effect>...} for one, and {@code done <command>} after each command.
 */
public final class Host {
    private static PrintStream out;
    private static BufferedReader in;
    /** Commands that came while a server call waited for the game's answer: run after it. */
    private static final Deque<String> pending = new ArrayDeque<>();
    private static List<String> drawn = List.of();

    private Host() {}

    public static void main(String[] args) throws IOException {
        out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(System.err);
        send("ready", Loader.javaVersion());
        in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = next()) != null; ) {
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
                String failed = Loader.load(f[1], f.length > 2 ? f[2] : "client");
                if (failed == null) {
                    send("loaded", f[1]);
                } else {
                    send("failed", f[1], failed);
                }
                Server.announce();
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
            case "use", "join", "respawn", "use_block", "random_tick", "step_on", "block_tick", "neighbor", "placement",
                    "placed", "destroy" -> send("return", Server.call(f));
            case "loot" -> Loot.modify(f);
            default -> send("log", "unknown command " + f[0]);
        }
    }

    private static void tick(Minecraft mc, String[] f) {
        boolean inWorld = f[3].equals("1");
        mc.kiln$update(f[2].equals("1"), inWorld, f[4].equals("1"), Long.parseLong(f[5]), f[6]);
        if (f.length > 9) {
            mc.kiln$fps(Integer.parseInt(f[9]));
        }
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

    private static String next() throws IOException {
        return pending.isEmpty() ? in.readLine() : pending.poll();
    }

    /**
     * Asks the game during server call {@code call} ({@code ask <call> <what>...}) and waits for its {@code answer
     * <call> <value>}: the game, waiting for the call's return, answers at once. Other commands that come meanwhile
     * wait their turn; an answer to another call (one the game gave up on) is dropped.
     */
    static String ask(String call, String... what) {
        String[] fields = new String[what.length + 1];
        fields[0] = call;
        System.arraycopy(what, 0, fields, 1, what.length);
        send("ask", fields);
        String answer = "answer\t" + call;
        try {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.equals(answer)) {
                    return "";
                }
                if (line.startsWith(answer + "\t")) {
                    return unescape(line.substring(answer.length() + 1));
                }
                if (!line.startsWith("answer\t")) {
                    pending.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("the game's answer didn't come: " + e);
        }
        throw new IllegalStateException("the game closed Kiln's input");
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
