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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

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
 *   <li>{@code command <command>}: the player typed one of the mods' client commands (without its slash);
 *   <li>{@code adv <id>}: the player has made an advancement (all of them again after entering a world);
 *   <li>{@code chunks <loaded> <unloaded>}: chunks of the player's level came to the game and went since the last
 *       tick, each {@code x,z} joined by {@code ;} (all of the level's after a change of dimension);
 *   <li>{@code message <game|chat> <text>}: a message came to the window's chat (the game's, or a player's);
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
 * each loot table the mods added to, {@code commands <name>...} (their client commands, when made), {@code send
 * <attended 0|1> <command>} (a command a mod sends as the player), {@code sound <id> <x y z> <volume> <pitch>} (no
 * place for the UI's), {@code particle <id> <x y z> <dx dy dz>}, {@code toast <title> <message> <millis> <token>
 * <replace 0|1>}, {@code log <text>}, {@code chat <text>}, {@code
 * draw <op>...} (the mods' HUD,
 * when it changed: {@code f x y w h argb}, {@code t x y argb shadow text} and the rest {@code
 * GuiGraphicsExtractor} writes), {@code ask <call> <what>...} during a
 * server call, {@code return <call> <result> <effect>...} for one, and {@code done <command>} after each command.
 */
public final class Host {
    private static PrintStream out;
    private static BufferedReader in;
    /** Commands that came while a server call waited for the game's answer: run after it. */
    private static final Deque<String> pending = new ArrayDeque<>();
    private static List<String> drawn = List.of();
    /** Whether the client's start was told ({@code CLIENT_STARTED}), and the connection whose readiness was. */
    private static boolean started;
    private static ClientPacketListener joined;
    private static final PacketSender NO_SENDER = new PacketSender() {};
    /** The chunks that came and went since the last tick ({@code chunks}), for the level the tick has. */
    private static final List<String[]> chunks = new ArrayList<>();
    /** The dimension the client level's chunks are of. */
    private static String chunkDimension = "";

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
            case "command" -> ClientCommands.kiln$run(f[1]);
            case "adv" -> mc.kiln$connection().getAdvancements().kiln$done(f[1]);
            case "chunks" -> chunks.add(new String[] {f[1], f[2]});
            case "message" -> {
                // The game's messages; a player's need what Kiln can't give (their signed message, their profile).
                if (f[1].equals("game")) {
                    ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal(f[2]), false);
                }
            }
            case "tick" -> tick(mc, f);
            case "use", "join", "respawn", "use_block", "random_tick", "step_on", "block_tick", "neighbor", "placement",
                    "placed", "destroy", "block_entity_tick", "changed" -> send("return", Server.call(f));
            case "loot" -> Loot.modify(f);
            default -> send("log", "unknown command " + f[0]);
        }
    }

    /**
     * A client tick: the game's state, the client events its changes make (the client started, a world's connection
     * made and ready, its level changed with the player in it, the connection closed), then the tick's own events.
     */
    private static void tick(Minecraft mc, String[] f) {
        boolean inWorld = f[3].equals("1");
        ClientPacketListener connection = mc.getConnection();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        mc.kiln$update(f[2].equals("1"), inWorld, f[4].equals("1"), Long.parseLong(f[5]), f[6]);
        if (f.length > 9) {
            mc.kiln$fps(Integer.parseInt(f[9]));
        }
        if (f.length > 8) {
            mc.getWindow().kiln$gui(Integer.parseInt(f[7]), Integer.parseInt(f[8]));
        }
        if (f.length > 10 && mc.player != null) {
            mc.player.kiln$update(f, 10);
        }
        // After the player: what they wear and hold, then F1 and F3.
        int after = 10 + Server.PLAYER;
        if (f.length > after + 2 && mc.player != null) {
            mc.player.kiln$equipment(f[after]);
            mc.gui.hud.kiln$hidden(f[after + 1].equals("1"));
            mc.getDebugOverlay().kiln$shown(f[after + 2].equals("1"));
        }
        // Then the window's world and player: key=value pairs, and the inventory.
        String screen = null;
        if (f.length > after + 4 && mc.player != null && mc.level != null) {
            java.util.Map<String, String> state = new java.util.HashMap<>();
            for (String pair : f[after + 3].split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    state.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            mc.kiln$singleplayer("1".equals(state.get("single")));
            mc.getLanguageManager().kiln$selected(state.get("language"));
            try {
                mc.options.kiln$set(Math.round(Float.parseFloat(state.getOrDefault("fov", "70"))),
                        Integer.parseInt(state.getOrDefault("render_distance", "12")),
                        Integer.parseInt(state.getOrDefault("gui_scale", "0")));
            } catch (NumberFormatException e) {
                send("log", "the game's options didn't read: " + f[after + 3]);
            }
            String held = state.getOrDefault("held", "");
            net.minecraft.client.KeyMapping.kiln$held(held.isEmpty() ? java.util.Set.of() : java.util.Set.of(held.split(",")));
            mc.level.kiln$place(state.getOrDefault("difficulty", "normal"), state.getOrDefault("biome", ""),
                    Integer.parseInt(state.getOrDefault("block_light", "0")), Integer.parseInt(state.getOrDefault("sky_light", "0")));
            try {
                mc.level.kiln$state(Long.parseLong(state.getOrDefault("day", "0")),
                        Float.parseFloat(state.getOrDefault("rain", "0")), Float.parseFloat(state.getOrDefault("thunder", "0")),
                        Integer.parseInt(state.getOrDefault("min_y", "-64")), Integer.parseInt(state.getOrDefault("height", "384")));
            } catch (NumberFormatException e) {
                send("log", "the game's world state didn't read: " + f[after + 3]);
            }
            mc.player.kiln$state(state, f[after + 4]);
            mc.kiln$connection().kiln$players(state.getOrDefault("players", ""));
            mc.getWindow().kiln$window(state.getOrDefault("window", ""));
            mc.mouseHandler.kiln$set(state.getOrDefault("cursor", ""), state.getOrDefault("mouse", ""),
                    mc.getWindow().getGuiScale());
            mc.level.kiln$blocks(mc.player.blockPosition(), state.getOrDefault("blocks", ""));
            screen = java.net.URLDecoder.decode(state.getOrDefault("screen", "playing"), StandardCharsets.UTF_8);
        }
        if (!started) {
            started = true;
            ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(mc);
        }
        if (connection != null && mc.getConnection() != connection) {
            ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(connection, mc);
        }
        if (mc.getConnection() != null && mc.getConnection() != connection) {
            ClientPlayConnectionEvents.INIT.invoker().onPlayInit(mc.getConnection(), mc);
        }
        if (level != mc.level) {
            if (level != null) {
                level.kiln$unloadAll();
            }
            if (level != null && player != null) {
                ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(player, level);
            }
            if (mc.level != null) {
                ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.invoker().afterLevelChange(mc, mc.level);
                ClientEntityEvents.ENTITY_LOAD.invoker().onLoad(mc.player, mc.level);
                if (joined != mc.getConnection()) {
                    joined = mc.getConnection();
                    ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(joined, NO_SENDER, mc);
                    // Then the connection's commands, as the server's tree comes after joining.
                    ClientCommands.kiln$register();
                }
            }
        }
        // The chunks the game has of the level now, after the level itself.
        if (mc.level != null) {
            if (!f[6].equals(chunkDimension)) {
                mc.level.kiln$unloadAll();
                chunkDimension = f[6];
            }
            for (String[] change : chunks) {
                mc.level.kiln$chunks(change[0], change[1]);
            }
        } else {
            chunkDimension = "";
        }
        chunks.clear();
        // The screen open, as vanilla's screen changes come during the client's tick.
        if (screen != null) {
            Screens.tick(mc, screen, Integer.parseInt(f[7]), Integer.parseInt(f[8]));
        } else if (mc.player == null) {
            Screens.leave();
        }
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(mc);
        if (mc.level != null) {
            ClientTickEvents.START_LEVEL_TICK.invoker().onStartTick(mc.level);
            ClientTickEvents.END_LEVEL_TICK.invoker().onEndTick(mc.level);
        }
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(mc);
        List<String> ops = List.of();
        if (inWorld) {
            mc.kiln$frame();
            // The level drawn, then the HUD over it.
            LevelRenderEvents.kiln$frame();
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Integer.parseInt(f[7]), Integer.parseInt(f[8]));
            for (HudElement element : HudElementRegistry.kiln$elements()) {
                try {
                    element.extractRenderState(graphics, DeltaTracker.ZERO);
                } catch (RuntimeException | LinkageError e) {
                    send("log", "a HUD element threw " + e);
                    e.printStackTrace();
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
                if (line.equals("gave_up\t" + call)) {
                    throw new GaveUp(call);
                }
                if (!line.startsWith("answer\t") && !line.startsWith("gave_up\t")) {
                    pending.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("the game's answer didn't come: " + e);
        }
        throw new IllegalStateException("the game closed Kiln's input");
    }

    /**
     * The game gave up on call {@code call} (it answered past its time) and won't answer its questions: the call stops
     * at its next one, as when a mod's code fails, so nothing it would have done with no answer stays (a cached tag,
     * a block entity's state), and the block entities it reached are loaded again.
     */
    static final class GaveUp extends RuntimeException {
        GaveUp(String call) {
            super("the game gave up on call " + call, null, false, false);
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
