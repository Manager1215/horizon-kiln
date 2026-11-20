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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Kiln's Java side, in the game's process ({@link Jni}, D-112) or a process of its own beside the game: the game
 * (Kiln's Rust side) writes commands to its stdin, one a line, and reads replies from its stdout; in the game's
 * process, the same lines go through two calls. The mods' own {@code System.out} goes to stderr
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
 *       {@code block_tick}, {@code neighbor}, {@code placement}, {@code placed}, {@code destroy}, {@code from_client},
 *       {@code server_tick}, {@code connect}, {@code disconnect}, {@code bridge <call> ...}: the integrated server asks the mods what
 *       using their item or block, a player coming into a level or respawning, what happens to their block (a tick, a
 *       neighbour, placing, breaking), a player's payload, a server tick (Fabric's tick events, {@link TickEvents}) or
 *       a player's connection made or closed does, or what the mods' mixins do at one of Horizon's hooks ({@link
 *       Bridge}), and waits for the answer
 *       ({@link Server} has the fields); during it, Java may ask the game ({@code ask}), and the game answers
 *       ({@code answer <call> <value>});
 *   <li>{@code loot [<id>...]}: the mods' changes to the game's loot tables ({@link Loot});
 *   <li>{@code from_server <hex>}: a mod's payload from the server, for the mods' client receivers ({@link Net});
 *   <li>{@code sin <kind> ...}: the mouse, a key or a character over a mod's own screen, {@code sclosed}: the game
 *       closed it ({@link Screens}); {@code modmenu <mod id> <class>}: a mod's Mod Menu entrypoint, {@code settings
 *       <mod id>}: the button for its settings page pressed ({@link Settings}); {@code catalogue <kiln version>}: the
 *       window's game started, the catalogue's copy may be refreshed online ({@link CatalogueRefresh});
 *       {@code overlay <key>
 *       <row>...}: Kiln's overlay opens, or closes when open ({@link Overlay});
 *   <li>{@code hook <name> <field>...}: one of Horizon's hooks fired on the window, for the game's methods there a mod's
 *       mixin goes into (Kiln's Mixin bridge, {@link Bridge}); nothing goes back.
 * </ul>
 * Replies: {@code ready <java version>}, {@code loaded <class>}, {@code failed <class> <why>}, {@code items <id>...}
 * (the mods' items with a use of their own), {@code blocks <id> <what>...} (their blocks with code of their own) and
 * {@code back <key> <value>...} (what the mods changed of the game's state Horizon owns, {@link Back}), {@code
 * listens <event>...} (the server events they hear) and {@code bridge <hook>...} (Horizon's hooks the mods'
 * mixins need, {@link Bridge}), each when that changed, {@code loot <id> <pools>} for
 * each loot table the mods added to, {@code commands <name>...} (their client commands, when made), {@code send
 * <attended 0|1> <command>} (a command a mod sends as the player), {@code sound <id> <x y z> <volume> <pitch>} (no
 * place for the UI's), {@code particle <id> <x y z> <dx dy dz>}, {@code toast <title> <message> <millis> <token>
 * <replace 0|1>}, {@code net <hex>} (a mod's payload for the server, {@link Net}), {@code log <text>}, {@code chat
 * <text>}, {@code screen open <pause 0|1> <class>} and {@code screen close} (a mod's own screen opens or closes),
 * {@code sdraw <op>...} (what it draws, when that changed), {@code settings <mod id>...} (the mods with a settings
 * page), {@code switch <mod id> <1|0>} (the overlay turned a mod on or off), {@code open mods} (the overlay
 * asks the game to show the mods' folder), {@code
 * draw <op>...} (the mods' HUD,
 * when it changed: {@code f x y w h argb}, {@code t x y argb shadow text} and the rest {@code
 * GuiGraphicsExtractor} writes), {@code ask <call> <what>...} during a
 * server call, {@code return <call> <result> <effect>...} for one, and {@code done <command>} after each command.
 */
public final class Host {
    /** The separate process's stdout and stdin; neither in the game's process ({@link Jni}). */
    private static PrintStream out;
    private static BufferedReader in;
    /** Commands that came while a server call waited for the game's answer: run after it. */
    private static final Deque<String> pending = new ArrayDeque<>();
    private static List<String> drawn = List.of();
    /** Whether the client's start was told ({@code CLIENT_STARTED}), and the connection whose readiness was. */
    private static boolean started;
    private static ClientPacketListener joined;
    /** The chunks that came and went since the last tick ({@code chunks}), for the level the tick has. */
    private static final List<String[]> chunks = new ArrayList<>();
    /** The dimension the client level's chunks are of. */
    private static String chunkDimension = "";

    private Host() {}

    public static void main(String[] args) throws IOException {
        if (Jni.inProcess()) {
            Jni.logToFile();
        } else {
            out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
            System.setOut(System.err);
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        send("ready", Loader.javaVersion());
        // A mod's own thread (a download, a timer) that ends in an exception says so in Kiln's log, not only in java.log.
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            e.printStackTrace();
            Native.log("a mod's thread " + thread.getName() + " threw " + e);
        });
        // The overlay's off-now answers, read off this thread while the mods start (D-126).
        Overlay.prepare();
        for (String line; (line = next()) != null; ) {
            if (line.startsWith("tick\t")) {
                // The mods are loaded and the game ticks: the rest is the client's main loop, Minecraft.run, as the
                // game runs it after making the client; Minecraft.close after it (D-093).
                pending.addFirst(line);
                Minecraft mc = Minecraft.getInstance();
                try {
                    mc.run();
                } finally {
                    // A mod's mixin that cancels or breaks the loop doesn't stop Kiln: the game goes on until it
                    // closes Kiln's input (after a whole run, there is nothing left to read).
                    kiln$loop();
                    mc.close();
                }
                return;
            }
            step(line);
        }
    }

    /** Kiln's own: the client's main loop, every command until the game closes Kiln's input ({@code Minecraft.run}). */
    public static void kiln$loop() {
        try {
            for (String line; (line = next()) != null; ) {
                step(line);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** One command from the game, then its {@code done}. */
    private static void step(String line) {
        String[] fields = line.split("\t", -1);
        for (int i = 1; i < fields.length; i++) {
            fields[i] = unescape(fields[i]);
        }
        // D-112's breadcrumb: Kiln's call, which the mods' calls in it name more closely.
        int depth = Breadcrumb.enter("", fields[0]);
        try {
            command(fields);
        } catch (RuntimeException | LinkageError e) {
            // LinkageError: a mod called a class or method Kiln has no stand-in for (NoSuchMethodError,
            // NoClassDefFoundError). It stops that call, not Java and every other mod.
            send("log", "command " + fields[0] + " failed: " + e);
        } catch (OutOfMemoryError | StackOverflowError e) {
            // Past every boundary that knows its mod (an event Kiln fires with no breadcrumb of its own): the mod is the
            // one whose code the throw came from; Kiln's Java thread goes on.
            SoftSwitch.trip(Breadcrumb.modOf(e.getStackTrace()), e, fields[0]);
        } finally {
            Breadcrumb.leave(depth);
        }
        send("done", fields[0]);
    }

    /** The first {@code load}, which made the client: its constructor runs it ({@link #kiln$entrypoints}). */
    private static String[] firstLoad;

    /**
     * Kiln's own, from {@code Minecraft}'s constructor where Fabric runs the entrypoints (D-200): the {@code load} that
     * made the client, then each one the game sent after it, each a command with its own {@code done}. The first other
     * line waits for the host's loop.
     */
    public static void kiln$entrypoints() {
        String[] first = firstLoad;
        firstLoad = null;
        if (first == null) {
            return;
        }
        load(first);
        try {
            for (String line; (line = next()) != null; ) {
                if (!line.startsWith("load\t")) {
                    pending.addFirst(line);
                    return;
                }
                step(line);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A mod's entrypoint ({@code load <class> [main|client]}). */
    private static void load(String[] f) {
        String failed = Loader.load(f[1], f.length > 2 ? f[2] : "client");
        // The keys it registered, for its page in Options > Mods; even none, so a key it dropped goes too.
        KeyMappingHelper.kiln$announce(f[1]);
        if (failed == null) {
            send("loaded", f[1]);
        } else {
            send("failed", f[1], failed);
        }
        Server.announce();
        Bridge.announce();
    }

    private static void command(String[] f) {
        switch (f[0]) {
            case "load" -> {
                if (Minecraft.kiln$made()) {
                    load(f);
                } else {
                    // The client is made as the game makes it, its constructor running the mods' entrypoints (D-200).
                    firstLoad = f;
                    Minecraft.kiln$make();
                }
            }
            case "font" -> {
                String[] parts = f[1].split(",");
                int[] widths = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    widths[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
                }
                // Before the client is made: the font keeps them (D-200).
                net.minecraft.client.gui.Font.kiln$table(widths);
            }
            case "babies" -> net.minecraft.world.entity.EntityType.kiln$babies(f.length > 1 ? f[1] : "");
            case "key" -> KeyMappingHelper.kiln$pressed(f[1]);
            // The key the player chose for a mod's mapping in Options > Mods.
            case "bind" -> net.minecraft.client.KeyMapping.kiln$bind(f[1], f[2]);
            case "command" -> ClientCommands.kiln$run(f[1]);
            case "adv" -> Minecraft.getInstance().kiln$connection().getAdvancements().kiln$done(f[1]);
            case "chunks" -> chunks.add(new String[] {f[1], f[2]});
            // A chunk's blocks Java asked for (D-186).
            case "chunk_blocks" -> ClientChunks.put(f[1], f[2], f.length > 3 ? f[3] : "");
            case "message" -> {
                // The game's messages; a player's need what Kiln can't give (their signed message, their profile).
                // As the game's component when it came (D-211): a death message keeps its death.* translation.
                Component message = f.length > 3 ? ChatJson.component(f[3], f[2]) : Component.literal(f[2]);
                if (f[1].equals("game")) {
                    ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(message, false);
                }
                // Then into the chat, as the game's ChatComponent.addMessage: the mods' mixins in it (D-106).
                Minecraft.getInstance().gui.hud.getChat().kiln$received(message);
            }
            case "tick" -> {
                ClientChunks.host();
                tick(Minecraft.getInstance(), f);
            }
            case "from_server" -> Net.fromServer(f[1]);
            case "sin" -> Screens.input(f);
            case "sclosed" -> Screens.closed();
            case "modmenu" -> Settings.register(f[1], f[2]);
            case "settings" -> Settings.open(f[1]);
            // The window's game started Kiln: the catalogue's copy is refreshed online, on a thread of its own (D-128).
            case "catalogue" -> CatalogueRefresh.start(java.nio.file.Path.of(System.getProperty("kiln.dir", ".")),
                    f.length > 1 ? f[1] : "");
            // Kiln's overlay (D-113): its key, or the button on Kiln's page in Options > Mods.
            case "overlay" -> Overlay.toggle(f);
            // This Java was started again by the overlay's Restart Java now after Java in the game's process (D-134).
            case "restarted" -> Overlay.restarted(f);
            case "use", "join", "from_client", "respawn", "use_block", "random_tick", "step_on", "block_tick", "neighbor", "placement",
                    "placed", "menu_button", "destroy", "block_entity_tick", "changed", "server_tick", "connect", "disconnect", "bridge", "server_commands", "server_command"
                    -> send("return", Server.call(f));
            case "loot" -> Loot.modify(f);
            // The mods turned off now in Options > Mods (D-113's soft switch), every other one on.
            case "soft_off" -> SoftSwitch.set(f.length > 1 ? f[1] : "");
            // A hook of Horizon's on the window, for the game's method a mod's mixin goes into (Kiln's Mixin bridge).
            case "hook" -> Bridge.client(f);
            case "maps" -> Maps.update(f.length > 1 ? f[1] : "");
            default -> send("log", "unknown command " + f[0]);
        }
    }

    /**
     * A client tick: the game's state, the client events its changes make (the client started, a world's connection
     * made and ready, its level changed with the player in it, the connection closed), then the tick's own events.
     * The game's methods mods inject into most run where the game runs them, with Fabric's events in them: {@code
     * Minecraft.disconnect}, {@code setLevel}, {@code Gui.setScreen} and {@code Minecraft.tick} (D-080).
     */
    /** The connection whose recipes are still to come (D-170), and how many ticks they have waited. */
    private static ClientPacketListener recipesDue;
    private static int recipesWaited;

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
            Screens.size(Integer.parseInt(f[7]), Integer.parseInt(f[8]));
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
            mc.kiln$level(java.net.URLDecoder.decode(state.getOrDefault("level", ""), StandardCharsets.UTF_8));
            mc.kiln$server(java.net.URLDecoder.decode(state.getOrDefault("server", ""), StandardCharsets.UTF_8));
            mc.getLanguageManager().kiln$selected(state.get("language"));
            Back.taken(state.getOrDefault("backs_taken", "0"));
            try {
                mc.options.kiln$set(Math.round(Float.parseFloat(state.getOrDefault("fov", "70"))),
                        Integer.parseInt(state.getOrDefault("render_distance", "12")),
                        Integer.parseInt(state.getOrDefault("gui_scale", "0")));
                mc.options.kiln$subtitles("1".equals(state.get("subtitles")));
                // Rounded to a float, as Horizon keeps it: a mod's 0.7 and Horizon's "0.7" are the same value.
                mc.options.kiln$gamma(Double.parseDouble(state.getOrDefault("gamma", "0.5")));
                // The volume sliders and the window's focus (D-232).
                mc.options.kiln$volumes(state.getOrDefault("volumes", ""));
                mc.kiln$focused(!"1".equals(state.get("unfocused")));
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
            try {
                mc.options.kiln$camera(Integer.parseInt(state.getOrDefault("camera", "0")));
            } catch (NumberFormatException e) {
                send("log", "the game's camera didn't read: " + state.get("camera"));
            }
            mc.debugEntries.kiln$game(state.getOrDefault("debug", ""), state.getOrDefault("debug_on", ""),
                    mc.getDebugOverlay().showDebugScreen());
            mc.kiln$hit(state.getOrDefault("hit", "miss"));
            mc.kiln$connection().kiln$players(state.getOrDefault("players", ""));
            // Resized (and the first time the game says its size): the GUI laid out again, as vanilla's resize (D-192).
            if (mc.getWindow().kiln$window(state.getOrDefault("window", ""))) {
                try {
                    mc.resizeGui();
                } catch (RuntimeException | LinkageError e) {
                    send("log", "Minecraft.resizeGui threw: " + e);
                }
            }
            mc.mouseHandler.kiln$set(state.getOrDefault("cursor", ""), state.getOrDefault("mouse", ""),
                    mc.getWindow().getGuiScale());
            mc.level.kiln$blocks(mc.player.blockPosition(), state.getOrDefault("blocks", ""));
            mc.level.kiln$mobs(state.getOrDefault("mobs", ""));
            screen = java.net.URLDecoder.decode(state.getOrDefault("screen", "playing"), StandardCharsets.UTF_8);
        }
        if (!started) {
            started = true;
            ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(mc);
        }
        if (connection != null && mc.getConnection() != connection) {
            // The world left, as the game leaves one (Minecraft.disconnect): Fabric's DISCONNECT comes in it. Out of a
            // world Kiln doesn't know the game's screen: the title screen is where the game goes from its own world.
            mc.kiln$leave(connection, level, player, new TitleScreen());
        }
        if (mc.getConnection() != null && mc.getConnection() != connection) {
            // A single-player world: its integrated server loaded it before the client connected (D-093).
            if (mc.hasSingleplayerServer()) {
                net.minecraft.server.MinecraftServer.kiln$server().kiln$loadLevel();
            }
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
                // The level set as the game sets one (Minecraft.setLevel): Fabric's AFTER_CLIENT_LEVEL_CHANGE in it.
                mc.kiln$setLevel(level, player);
                ClientEntityEvents.ENTITY_LOAD.invoker().onLoad(mc.player, mc.level);
                if (joined != mc.getConnection()) {
                    // Single player and its world's folder before JOIN, which the state above reads only with a level
                    // and a player: a mod's files per world are named by it then (JEI's bookmarks, D-167).
                    if (f.length > after + 3) {
                        for (String pair : f[after + 3].split(";")) {
                            if (pair.startsWith("single=")) {
                                mc.kiln$singleplayer(pair.equals("single=1"));
                            } else if (pair.startsWith("level=")) {
                                mc.kiln$level(java.net.URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8));
                            }
                        }
                    }
                    joined = mc.getConnection();
                    // The game's ClientPacketListener.handleLogin, the level and player there, before JOIN (D-106).
                    joined.handleLogin(null);
                    ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(joined, Net.TO_SERVER, mc);
                    // Then the recipes (below), as a server synchronizes them after the login.
                    recipesDue = joined;
                    recipesWaited = 0;
                    // Then the connection's commands, as the server's tree comes after joining.
                    ClientCommands.kiln$register();
                }
            }
        }
        if (recipesDue != null && recipesDue == mc.getConnection() && mc.level != null) {
            // The recipes come from the server after it took the player in: in single player once its first call has
            // said the world's folder (a recipe viewer starts here and reads the world's bookmarks there, D-170), at
            // most 40 ticks on. The game's creative tabs go into their registry first (D-152).
            if (Server.worldFolder() != null || !mc.hasSingleplayerServer() || ++recipesWaited > 40) {
                recipesDue = null;
                GameTabs.register();
                GameEnchantments.register();
                ClientRecipes.synchronize(mc);
            }
        } else if (recipesDue != null && recipesDue != mc.getConnection()) {
            recipesDue = null;
        }
        if (mc.player != null) {
            Net.playerReady();
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
        // The client's tick, as the game's (Minecraft.tick): Fabric's tick events in it, the player's in the level's.
        mc.tick();
        // A mod's own screen, after the tick that may have opened it: it ticks and draws.
        Screens.tickMod();
        List<String> ops = List.of();
        if (inWorld) {
            mc.kiln$frame();
            // The level drawn, then the HUD over it.
            LevelRenderEvents.kiln$frame();
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Integer.parseInt(f[7]), Integer.parseInt(f[8]));
            // The HUD, as the game draws it (Hud.extractRenderState): the mods' elements in it (D-093).
            net.minecraft.client.gui.Hud.kiln$begin();
            mc.gui.hud.extractRenderState(graphics, DeltaTracker.ZERO);
            ops = new ArrayList<>(graphics.kiln$ops());
        }
        if (!ops.equals(drawn)) {
            drawn = ops;
            send("draw", ops.toArray(String[]::new));
        }
        // Keys a mod registered after its entrypoint ran (in a tick or lifecycle event), if any.
        KeyMappingHelper.kiln$announce("");
        // What the mods changed of what Horizon owns, back to it, in a world (D-105).
        if (inWorld && mc.player != null) {
            Back.send(mc);
        }
    }

    /** The thread running a server call the game made on its own thread ({@link #direct}), if one runs. */
    private static volatile Thread direct;

    /**
     * In the game's process (D-112): a server call ({@code use}, {@code server_tick}, {@code bridge}...) run on the
     * thread the game called from, one at a time (the game waits for Kiln's own thread to be between commands); its
     * {@code return} line back. Java's questions meanwhile go to the waiting game at once ({@link Jni#ask}), so the
     * game never gives up on an answer.
     */
    static byte[] direct(byte[] bytes) {
        String[] f = new String(bytes, StandardCharsets.UTF_8).split("\t", -1);
        for (int i = 1; i < f.length; i++) {
            f[i] = unescape(f[i]);
        }
        Thread previous = direct;
        direct = Thread.currentThread();
        String[] returned;
        int depth = Breadcrumb.enter("", serverCall(f));
        try {
            returned = Server.call(f);
        } catch (RuntimeException | LinkageError e) {
            returned = new String[] {f.length > 1 ? f[1] : "0", "error", e.toString()};
        } catch (OutOfMemoryError | StackOverflowError e) {
            // As in step: the mod whose code it came from is turned off, and the game's thread goes on.
            SoftSwitch.trip(Breadcrumb.modOf(e.getStackTrace()), e, serverCall(f));
            returned = new String[] {f.length > 1 ? f[1] : "0", "error", e.toString()};
        } finally {
            Breadcrumb.leave(depth);
            direct = previous;
        }
        return line("return", returned).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A server call as D-112's breadcrumb names it: its command, and for the Mixin bridge's the hook ({@code bridge
     * <call> <world> <time> <hook>}).
     */
    private static String serverCall(String[] f) {
        return f[0].equals("bridge") && f.length > 4 ? "bridge " + f[4] : "server " + f[0];
    }

    /** The last call the game gave up on: its questions aren't asked any more. */
    private static String givenUp;

    private static String next() throws IOException {
        return pending.isEmpty() ? readLine() : pending.poll();
    }

    /** The game's next line: from stdin, or in the game's process from the game itself ({@link Jni}). */
    private static String readLine() throws IOException {
        if (in != null) {
            return in.readLine();
        }
        byte[] line = Jni.next();
        return line == null ? null : new String(line, StandardCharsets.UTF_8);
    }

    /**
     * Asks the game during server call {@code call} ({@code ask <call> <what>...}) and waits for its {@code answer
     * <call> <value>}: the game, waiting for the call's return, answers at once. Other commands that come meanwhile
     * wait their turn; an answer to another call (one the game gave up on) is dropped.
     */
    static String ask(String call, String... what) {
        if (Thread.currentThread() == direct) {
            byte[] answer = Jni.ask(line(null, what).getBytes(StandardCharsets.UTF_8));
            if (answer == null) {
                throw new IllegalStateException("the game didn't answer");
            }
            return new String(answer, StandardCharsets.UTF_8);
        }
        if (call.equals(givenUp)) {
            // The game said so at this call's last question: it won't answer this one either.
            throw new GaveUp(call);
        }
        String[] fields = new String[what.length + 1];
        fields[0] = call;
        System.arraycopy(what, 0, fields, 1, what.length);
        send("ask", fields);
        String answer = "answer\t" + call;
        try {
            for (String line; (line = readLine()) != null; ) {
                if (line.equals(answer)) {
                    return "";
                }
                if (line.startsWith(answer + "\t")) {
                    return unescape(line.substring(answer.length() + 1));
                }
                if (line.equals("gave_up\t" + call)) {
                    givenUp = call;
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
        // A call that catches it (Players, for a late payload's lookups) goes on; its next questions throw at once.
        GaveUp(String call) {
            super("the game gave up on call " + call, null, false, false);
        }
    }

    /** A reply line, sent at once. */
    static synchronized void send(String kind, String... fields) {
        String line = line(kind, fields);
        if (out == null) {
            Jni.reply(line.getBytes(StandardCharsets.UTF_8));
            return;
        }
        out.print(line + '\n');
        out.flush();
    }

    /** {@code kind} and the escaped fields, tab-separated; with no kind, the fields alone. */
    private static String line(String kind, String... fields) {
        StringBuilder b = new StringBuilder(kind == null ? "" : kind);
        for (int i = 0; i < fields.length; i++) {
            if (kind != null || i > 0) {
                b.append('\t');
            }
            b.append(escape(fields[i]));
        }
        return b.toString();
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
