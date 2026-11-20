package net.minecraft.client;

import kiln.Native;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * Kiln's stand-in for {@code Minecraft}, the client: Horizon as Java mods see it. Kiln updates it from the game
 * before each tick: {@code player} and {@code level} are null outside a world, as in vanilla. The game's methods
 * mods' mixins inject into most run where the game runs them (D-080): {@link #tick} with each tick, {@link #setLevel}
 * as a world's level comes, {@link #disconnect(Screen, boolean)} as the world is left.
 */
public class Minecraft extends net.minecraft.util.thread.BlockableEventLoop<Runnable> {
    /** The client, made once as the game makes it (D-200): null before, and while its constructor sets it. */
    private static volatile Minecraft instance;
    /** Whether the constructor runs: {@link #getInstance} doesn't make a second client from within it. */
    private static volatile boolean making;

    public final Font font = new Font();
    /** The mods' particles (Kiln ticks them with the world; Horizon draws the game's own). */
    public final net.minecraft.client.particle.ParticleEngine particleEngine =
            new net.minecraft.client.particle.ParticleEngine(null, null);
    public final Gui gui = new Gui(this);
    private final DebugScreenOverlay debugOverlay = new DebugScreenOverlay();
    private final com.mojang.blaze3d.platform.Window window = new com.mojang.blaze3d.platform.Window();
    /** The pointer, as the game had it with the tick. */
    public final MouseHandler mouseHandler = new MouseHandler(this);
    /** Made with the game, as vanilla's: mods reach it by the field (its clipboard, D-174). */
    public net.minecraft.client.KeyboardHandler keyboardHandler = new net.minecraft.client.KeyboardHandler(this);
    public ClientLevel level;
    public LocalPlayer player;
    /** The debug screen's entries, as the game's debug profile has them (F3+B's hitboxes...). */
    public final net.minecraft.client.gui.components.debug.DebugScreenEntryList debugEntries =
            new net.minecraft.client.gui.components.debug.DebugScreenEntryList();
    /** What the crosshair is on, as the game had it with the tick; null outside a world. */
    public net.minecraft.world.phys.HitResult hitResult;
    /** The player's actions on the world (using an item, a creative slot); null outside a world. */
    public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode;
    private ClientPacketListener connection;
    private boolean paused;
    private int fps;
    private long frameTimeNs;

    /** The client's resources and the listeners mods register on them (D-152). */
    private final net.minecraft.server.packs.resources.ReloadableResourceManager resourceManager =
            new net.minecraft.server.packs.resources.ReloadableResourceManager(kiln.Resources.INSTANCE);
    private final ResourceLoadStateTracker reloadStateTracker = new ResourceLoadStateTracker();

    /**
     * The game's constructor, the one Kiln's client is made by (D-200): as Fabric's, it sets the instance, runs every
     * mod's entrypoints ({@link kiln.Host#kiln$entrypoints}), then starts the initial resource reload, where mods'
     * mixins hook the client's start (JEI registers its reload listeners there, D-152). Mods' {@code <init>} mixins
     * see this one object, the one the game's code uses.
     */
    public Minecraft(net.minecraft.client.main.GameConfig config) {
        instance = this;
        kiln.Host.kiln$entrypoints();
        reloadStateTracker.startReload(ResourceLoadStateTracker.ReloadReason.INITIAL,
                kiln.Resources.INSTANCE.listPacks().toList());
    }

    /** Kiln's own: whether the client is made (or being made). */
    public static boolean kiln$made() {
        return instance != null || making;
    }

    /** Kiln's own: makes the client, once (D-200). */
    public static void kiln$make() {
        // Another thread (a server call in the game's process) that asks meanwhile gets null, not a second client.
        synchronized (Minecraft.class) {
            if (kiln$made()) {
                return;
            }
            making = true;
        }
        try {
            new Minecraft(net.minecraft.client.main.GameConfig.KILN);
        } finally {
            making = false;
        }
    }

    /** The client; made now when nothing made it yet, null while it is being made (as vanilla's, D-200). */
    public static Minecraft getInstance() {
        if (instance == null && !making) {
            kiln$make();
        }
        return instance;
    }

    /** The connection to the world; null outside one. */
    public ClientPacketListener getConnection() {
        return connection;
    }

    /** When the frame being drawn began: Kiln draws the mods' HUD once a tick, so once a tick it changes. */
    public long getFrameTimeNs() {
        return frameTimeNs;
    }

    /** Kiln's own: the mods' HUD is being drawn anew. */
    public void kiln$frame() {
        frameTimeNs = System.nanoTime();
    }

    private boolean singleplayer;
    /** The game's folder as the mods know it: Kiln's ({@code FabricLoader.getGameDir}, D-151; Mezz Config GUI's). */
    public final java.io.File gameDirectory =
            net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile();
    /** The game's options mods read, as it said with the tick. */
    public Options options = new Options();
    private final net.minecraft.client.resources.language.LanguageManager languageManager =
            new net.minecraft.client.resources.language.LanguageManager();

    public net.minecraft.client.resources.language.LanguageManager getLanguageManager() {
        return languageManager;
    }

    private final net.minecraft.client.renderer.texture.TextureManager textureManager =
            new net.minecraft.client.renderer.texture.TextureManager(kiln.Resources.INSTANCE);
    private final net.minecraft.client.sounds.SoundManager soundManager = new net.minecraft.client.sounds.SoundManager();

    /** The client's resources: the game's, and the Java mods' above them. */
    public net.minecraft.server.packs.resources.ResourceManager getResourceManager() {
        return kiln.Resources.INSTANCE;
    }

    private final net.minecraft.client.resources.model.sprite.AtlasManager atlasManager =
            new net.minecraft.client.resources.model.sprite.AtlasManager(null, 0);

    /** The game's texture atlases: their sprites by name and size (Horizon draws them). */
    public net.minecraft.client.resources.model.sprite.AtlasManager getAtlasManager() {
        return atlasManager;
    }

    public net.minecraft.client.renderer.texture.TextureManager getTextureManager() {
        return textureManager;
    }

    /** The game's sounds: what a mod plays, the game plays with the next tick. */
    public net.minecraft.client.sounds.SoundManager getSoundManager() {
        return soundManager;
    }

    /** The game's version. */
    public String getLaunchedVersion() {
        return "26.3";
    }

    /** A world of this computer's own: single player (Horizon's integrated server), as the game said. */
    public boolean hasSingleplayerServer() {
        return singleplayer;
    }

    /**
     * Single player's server, in the window's game: Kiln's one {@code MinecraftServer} (Horizon's integrated server, as
     * the Java mods' server code reaches it), so a mod's per-world files on the client (JEI's bookmarks and lookup
     * history) are kept by the world's folder name (D-167); null in multiplayer.
     */
    public net.minecraft.client.server.IntegratedServer getSingleplayerServer() {
        return singleplayer ? (net.minecraft.client.server.IntegratedServer) net.minecraft.server.MinecraftServer.kiln$server() : null;
    }

    /**
     * The server the window's game is on in multiplayer, by its address as the player joined it (the game said with
     * the tick, D-178): a mod's files per server (JEI's bookmarks and history) are kept by it; null in single
     * player and out of a world. Kiln can't tell a LAN world found on the network from a server: both are servers.
     */
    public net.minecraft.client.multiplayer.ServerData getCurrentServer() {
        return singleplayer || player == null ? null : currentServer;
    }

    private net.minecraft.client.multiplayer.ServerData currentServer;

    /** Kiln's own: the server's address the game said with the tick, empty for none (D-178). */
    public void kiln$server(String address) {
        if (address == null || address.isEmpty()) {
            currentServer = null;
        } else if (currentServer == null || !currentServer.ip.equals(address)) {
            // Named as vanilla names a server joined by its address (direct connection, quick play): Kiln doesn't
            // know the server list's name for it.
            String name = kiln.Language.has("selectServer.defaultName") ? kiln.Language.get("selectServer.defaultName")
                    : "Minecraft Server";
            currentServer = new net.minecraft.client.multiplayer.ServerData(name, address,
                    net.minecraft.client.multiplayer.ServerData.Type.OTHER);
        }
    }

    /** The player signed in: the window's player's name and UUID; null outside a world. */
    public User getUser() {
        if (player != null) {
            user = new User(player.getScoreboardName(), player.getUUID(), "", java.util.Optional.empty(),
                    java.util.Optional.empty());
        }
        return user;
    }

    /**
     * The player last seen in a world; before the first, none known: an empty name and the nil UUID. Vanilla's is
     * never null, and mods read it in their entrypoints (JEI's config, D-152), before Kiln sees a player.
     */
    private User user = new User("", new java.util.UUID(0, 0), "", java.util.Optional.empty(), java.util.Optional.empty());

    /** What the camera follows: the player (Kiln doesn't know when a spectator looks through another entity). */
    public net.minecraft.world.entity.Entity getCameraEntity() {
        return player;
    }

    /** Kiln draws the mods' HUD once a tick, at the tick: no part of a tick has passed. */
    public DeltaTracker getDeltaTracker() {
        return DeltaTracker.ZERO;
    }

    /** Kiln's own: single player or not, as the game said. */
    /** Kiln's own: single player's world folder, by name, as the game said with the tick (D-167). */
    public void kiln$level(String level) {
        this.levelId = level;
    }

    private String levelId = "";

    /** Kiln's own: single player's world folder's name; empty when the game didn't say. */
    public String kiln$level() {
        return levelId;
    }

    public void kiln$singleplayer(boolean singleplayer) {
        this.singleplayer = singleplayer;
    }

    private final com.mojang.blaze3d.platform.TextInputManager textInputManager =
            new com.mojang.blaze3d.platform.TextInputManager();

    /** The window's text input, as a mod's text field tells it of its focus (D-170). */
    public com.mojang.blaze3d.platform.TextInputManager textInputManager() {
        return textInputManager;
    }

    /** Whether the window has the system's focus, as the game said with the tick (D-232). */
    private volatile boolean focused = true;

    /** Whether the window has the system's focus (the player isn't in another window), as the game said (D-232). */
    public boolean isWindowActive() {
        return focused;
    }

    /** Kiln's own: the window's focus, as the game said with the tick. */
    public void kiln$focused(boolean focused) {
        this.focused = focused;
    }

    /** Frames Horizon drew in the last second. */
    public int getFps() {
        return fps;
    }

    public DebugScreenOverlay getDebugOverlay() {
        return debugOverlay;
    }

    public com.mojang.blaze3d.platform.Window getWindow() {
        return window;
    }

    /**
     * The GUI laid out again for the window's size, as vanilla does when the window is resized (and when the GUI
     * scale option changes). Horizon lays its own out; Kiln calls this when the window's size changed, so a mod's
     * mixin at its head runs then (GUI Scaler sets the GUI scale option by the window's size, which goes back to
     * Horizon, D-192).
     */
    public void resizeGui() {}

    /** True while the game is paused (single player's pause menu). */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Kiln's own: the game's state as of this tick. Out of a world there is no connection; while Horizon loads a
     * dimension the connection stays (and what it knows, such as advancements) but there is no player or level.
     */
    public void kiln$update(boolean connected, boolean inWorld, boolean paused, long gameTime, String dimension) {
        this.paused = paused;
        if (!connected) {
            connection = null;
        }
        if (!inWorld) {
            player = null;
            level = null;
            hitResult = null;
            gameMode = null;
            return;
        }
        kiln$connection();
        if (level == null) {
            level = new ClientLevel(connection, null, null, null, 0, 0, null, false, 0L, 63);
            player = new LocalPlayer();
            player.connection = connection;
        }
        if (gameMode == null) {
            gameMode = new net.minecraft.client.multiplayer.MultiPlayerGameMode(this, connection);
        }
        level.kiln$update(dimension, gameTime);
    }

    /**
     * Kiln's own: what the crosshair is on, as the game said with the tick: {@code block <x> <y> <z> <face x> <face y>
     * <face z> <hit x> <hit y> <hit z>}, or {@code miss} (nothing within reach, or an entity: Kiln's Java knows no
     * entity but the player). A miss is where the look ends, as vanilla's.
     */
    public void kiln$hit(String field) {
        String[] w = field.split(" ");
        if (w.length == 10 && w[0].equals("block")) {
            try {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(w[1]),
                        Integer.parseInt(w[2]), Integer.parseInt(w[3]));
                net.minecraft.core.Direction face = net.minecraft.core.Direction.getNearest(Integer.parseInt(w[4]),
                        Integer.parseInt(w[5]), Integer.parseInt(w[6]), net.minecraft.core.Direction.UP);
                net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(Double.parseDouble(w[7]),
                        Double.parseDouble(w[8]), Double.parseDouble(w[9]));
                hitResult = new net.minecraft.world.phys.BlockHitResult(at, face, pos, false);
                return;
            } catch (NumberFormatException e) {
                // A miss, below.
            }
        }
        if (player == null) {
            hitResult = null;
            return;
        }
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 end = player.getEyePosition().add(look.scale(4.5));
        hitResult = net.minecraft.world.phys.BlockHitResult.miss(end,
                net.minecraft.core.Direction.getApproximateNearest(look),
                net.minecraft.core.BlockPos.containing(end.x, end.y, end.z));
    }

    /** Either shift key held down: the input's own while a screen handles it (D-174), else as the game said with the tick. */
    public boolean hasShiftDown() {
        if (kiln.Screens.kiln$inputModifiers() >= 0) {
            return (kiln.Screens.kiln$inputModifiers() & 1) != 0;
        }
        return KeyMapping.kiln$isHeld("key.keyboard.left.shift") || KeyMapping.kiln$isHeld("key.keyboard.right.shift");
    }

    /** Either control key held down (on macOS, command), as the game said with the tick. */
    public boolean hasControlDown() {
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
        if (kiln.Screens.kiln$inputModifiers() >= 0) {
            return (kiln.Screens.kiln$inputModifiers() & (mac ? 8 : 2)) != 0;
        }
        return mac ? KeyMapping.kiln$isHeld("key.keyboard.left.win") || KeyMapping.kiln$isHeld("key.keyboard.right.win")
                : KeyMapping.kiln$isHeld("key.keyboard.left.control") || KeyMapping.kiln$isHeld("key.keyboard.right.control");
    }

    /** Either alt key held down, as the game said with the tick. */
    public boolean hasAltDown() {
        if (kiln.Screens.kiln$inputModifiers() >= 0) {
            return (kiln.Screens.kiln$inputModifiers() & 4) != 0;
        }
        return KeyMapping.kiln$isHeld("key.keyboard.left.alt") || KeyMapping.kiln$isHeld("key.keyboard.right.alt");
    }

    /** Kiln's own: the frame rate, from the game with each tick. */
    public void kiln$fps(int fps) {
        this.fps = fps;
    }

    /** Kiln's own: the connection, made before the first tick in a world so advancements can arrive. */
    public ClientPacketListener kiln$connection() {
        if (connection == null) {
            connection = new ClientPacketListener();
        }
        return connection;
    }

    /**
     * The client's tick, 20 a second, in a world or not: Fabric's client tick events around the level's, and in the
     * level's the player's own tick, as the game's entities tick with their level. The game moves and updates the
     * player itself; Kiln's tick of them runs what the mods' mixins put in it. Kiln's runtime calls this with each of
     * the game's ticks, after what changed with it (a world joined or left, a screen opened).
     */
    public void tick() {
        clientThread = Thread.currentThread();
        runTasks();
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(this);
        if (level != null && player != null && !paused) {
            // The HUD's tick, then the keys pressed, when no screen is open (D-093).
            gui.hud.kiln$tick();
            if (gui.screen() == null) {
                handleKeybinds();
            }
        }
        if (level != null) {
            ClientTickEvents.START_LEVEL_TICK.invoker().onStartTick(level);
            // The world stands still while the game is paused (single player's pause menu): the player too.
            if (player != null && !paused) {
                player.tick();
                particleEngine.tick();
            }
            ClientTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
        }
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(this);
    }

    /** The thread the client ticks on (Kiln's main loop), once it has ticked. */
    private volatile Thread clientThread;
    /** What mods asked the client to run, from their own threads or to run later ({@link #execute}, {@link #schedule}). */
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** True on the thread the client ticks on. */
    public boolean isSameThread() {
        return Thread.currentThread() == clientThread;
    }

    /**
     * Runs the task on the client's thread: now when called from it, else with the next tick (the game's own
     * {@code execute}). A mod's thread (a download, a timer) hands its result to the game this way.
     */
    public void execute(Runnable task) {
        if (isSameThread()) {
            runTask(task);
        } else {
            tasks.add(task);
        }
    }

    /**
     * Runs the task on the client's thread and waits for it, as the game's: now on that thread, else with the next tick.
     * REI's plugin reload hands the client's part of each stage over this way: without it, its reload never ended and
     * its overlay stayed hidden (D-215).
     */
    @Override
    public void executeBlocking(Runnable task) {
        if (isSameThread()) {
            task.run();
        } else {
            submit(task).join();
        }
    }

    @Override
    public void executeIfPossible(Runnable task) {
        execute(task);
    }

    /** Runs the task with the next tick of the client, from whichever thread this is called. */
    public void schedule(Runnable task) {
        tasks.add(task);
    }

    /** {@link #execute} with a result: done once the task has run. */
    public java.util.concurrent.CompletableFuture<Void> submit(Runnable task) {
        java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        execute(() -> {
            try {
                task.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
                throw t;
            }
        });
        return done;
    }

    /** {@link #execute} with a value: done once the supplier has run. */
    public <V> java.util.concurrent.CompletableFuture<V> submit(java.util.function.Supplier<V> task) {
        java.util.concurrent.CompletableFuture<V> done = new java.util.concurrent.CompletableFuture<>();
        execute(() -> {
            try {
                done.complete(task.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
                throw t;
            }
        });
        return done;
    }

    private void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            runTask(task);
        }
    }

    /** A task that throws is said in Kiln's log and skipped, as the game logs and goes on. */
    private static void runTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            java.io.StringWriter trace = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(trace));
            Native.log("a task a mod gave the client threw " + trace);
        }
    }

    /**
     * The key mappings pressed this tick handled, in a world with no screen open: Horizon handles the game's own; the
     * mods' mixins in this run there (D-093).
     */
    private void handleKeybinds() {}

    /**
     * The game's main loop, from the client's start to its close: Kiln's runtime runs everything in it after the mods
     * are loaded, from the first tick ({@code kiln.Host}), so the mods' mixins at its start run before Fabric's
     * {@code CLIENT_STARTED}, and those at its end as the game closes (D-093).
     */
    public void run() {
        kiln.Host.kiln$loop();
    }

    /** The client closed, after {@link #run} ends: the game is going away. */
    public void close() {}

    /**
     * The attack key on what the crosshair points at: Horizon attacks; the mods' mixins in this run there, at
     * Horizon's {@code interaction_key} hook (Kiln's Mixin bridge, D-102). Whether it attacked: Kiln can't tell.
     */
    public boolean startAttack() {
        return kiln.Bridge.body(Boolean.FALSE);
    }

    /** The use key: Horizon uses the item; the mods' mixins in this run at the same hook (D-102). */
    public void startUseItem() {
        kiln.Bridge.body(null);
    }

    /** Kiln's own: the keyboard's handler, made when first asked for (Kiln's Mixin bridge calls its keyPress). */
    public net.minecraft.client.KeyboardHandler kiln$keyboard() {
        if (keyboardHandler == null) {
            keyboardHandler = new net.minecraft.client.KeyboardHandler(this);
        }
        return keyboardHandler;
    }

    /**
     * The client's level: a world joined, or another dimension's level once Horizon has loaded it. Fabric's {@code
     * AFTER_CLIENT_LEVEL_CHANGE} comes in it, as Fabric's own comes where the game hands its engines the new level.
     */
    public void setLevel(ClientLevel level) {
        updateLevelInEngines(level, false);
        this.level = level;
        if (connection != null) {
            connection.kiln$level(level);
        }
        if (level != null) {
            ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.invoker().afterLevelChange(this, level);
        }
    }

    /**
     * Kiln's own: the level {@link #kiln$update} made with this tick (what the tick says of it is in it already) set
     * as the game sets one, by {@link #setLevel}, which sees {@code before} as the level and {@code player} as the
     * player: the game makes a world's player after its level. They are the tick's after, whatever a mixin did.
     */
    public void kiln$setLevel(ClientLevel before, LocalPlayer player) {
        ClientLevel madeLevel = level;
        LocalPlayer madePlayer = this.player;
        level = before;
        this.player = player;
        try {
            setLevel(madeLevel);
        } finally {
            level = madeLevel;
            this.player = madePlayer;
        }
    }

    /** Whether Kiln is leaving the game's world ({@link #kiln$leave}): a mod's own {@code disconnect} can't. */
    private boolean leaving;

    /** The world left for {@code screen}, by {@link #disconnect(Screen, boolean, boolean)}, as the game leaves one. */
    public void disconnect(Screen screen, boolean keepResourcePacks) {
        disconnect(screen, keepResourcePacks, true);
    }

    /**
     * The world left: the level and player go ({@link #clearClientLevel}), then the connection, with Fabric's {@code
     * DISCONNECT}. The flags are the game's to act on. Kiln leaves the game's world only when the game has left it: a
     * mod asking to is logged and changes nothing yet.
     */
    public void disconnect(Screen screen, boolean keepResourcePacks, boolean stopSound) {
        if (!leaving) {
            kiln$askLeave();
            return;
        }
        ClientPacketListener closed = connection;
        clearClientLevel(screen);
        connection = null;
        if (closed != null) {
            ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(closed, this);
        }
        if (singleplayer) {
            net.minecraft.server.MinecraftServer.kiln$server().kiln$stop();
        }
    }

    /**
     * The client's level gone with the world left: no level, no player. The game shows {@code screen} next; out of a
     * world Kiln doesn't know the game's screens, and shows nothing.
     */
    public void clearClientLevel(Screen screen) {
        updateLevelInEngines(null, false);
        level = null;
        player = null;
    }

    /**
     * The game hands its engines (sound, particles, the renderers) the new level, null as a world is left: the game
     * does it itself; the mods' mixins in this run here (JEI stops there, D-152).
     */
    private void updateLevelInEngines(ClientLevel level, boolean stopSound) {}

    /**
     * Kiln's own: the game left its world for {@code screen}, so Kiln leaves it as the game does ({@link
     * #disconnect(Screen, boolean)}), whose start sees the connection, level and player the world had ({@link
     * #kiln$update} let them go with the tick). After, they are the tick's again, whatever a mixin did.
     */
    public void kiln$leave(ClientPacketListener connection, ClientLevel level, LocalPlayer player, Screen screen) {
        leaveAsked = false;
        ClientPacketListener nowConnection = this.connection;
        ClientLevel nowLevel = this.level;
        LocalPlayer nowPlayer = this.player;
        this.connection = connection;
        this.level = level;
        this.player = player;
        leaving = true;
        try {
            disconnect(screen, false);
        } finally {
            leaving = false;
            this.connection = nowConnection;
            this.level = nowLevel;
            this.player = nowPlayer;
        }
    }
    /** A screen shown at once: the game's next frame draws it (Kiln opens a mod's screen as it opens one). */
    public void setScreenAndShow(Screen screen) {
        gui.setScreen(screen);
    }

    /**
     * A panorama's six pictures into {@code folder}, as vanilla's F4 debug key takes them: Horizon has no way for a
     * mod to turn its camera and draw a frame yet, so none are taken and the game's failure line is the answer.
     */
    public net.minecraft.network.chat.Component grabPanoramixScreenshot(java.io.File folder) {
        kiln.Missing.called("a panorama screenshot");
        return net.minecraft.network.chat.Component.translatable("screenshot.failure",
                "Kiln can't take a panorama yet");
    }

    /**
     * The shapes mods draw in the world this tick ({@code Gizmos.cuboid}...) collected until the collection closes.
     * Kiln can't draw in Horizon's world yet: they are taken, and not drawn.
     */
    public net.minecraft.gizmos.Gizmos.TemporaryCollection collectPerTickGizmos() {
        return net.minecraft.gizmos.Gizmos.withCollector(net.minecraft.gizmos.GizmoCollector.NOOP);
    }
    private final net.minecraft.client.resources.model.ModelManager modelManager =
            new net.minecraft.client.resources.model.ModelManager();

    /**
     * The game's models as far as the mods read them: the fluids' (their still, flowing and overlay sprites, a recipe
     * viewer's tanks and slots, D-168). Horizon draws the blocks' and items' itself.
     */
    public net.minecraft.client.resources.model.ModelManager getModelManager() {
        return modelManager;
    }
    /** Whether a mod may open a screen over the one open now: Kiln's screens all may (vanilla's default). */
    public boolean canInterruptScreen() {
        return true;
    }
    /** {@code BlockableEventLoop}'s one abstract method: Kiln's tasks are plain {@link Runnable}s (D-206: mods name the loop's type). */
    @Override
    public Runnable wrapRunnable(Runnable task) {
        return task;
    }

    @Override
    public String name() {
        return "Kiln";
    }

    @Override
    public java.util.List<net.minecraft.util.profiling.metrics.MetricSampler> profiledMetrics() {
        return java.util.List.of();
    }

    /**
     * Kiln's own: the text field that has the keyboard, as mods' own fields say (REI's search box calls
     * {@link #onTextInputFocusChange}): while there is one, the game's keys go to the open screen (D-215).
     */
    public static net.minecraft.client.gui.components.events.GuiEventListener kiln$textFocus;

    public void onTextInputFocusChange(net.minecraft.client.gui.components.events.GuiEventListener listener, boolean focused) {
        if (focused) {
            kiln$textFocus = listener;
        } else if (kiln$textFocus == listener) {
            kiln$textFocus = null;
        }
    }

    /** Whether a mod asked the game to leave its world ({@link #kiln$askLeave}) and the game hasn't left yet. */
    private boolean leaveAsked;

    /** The world left, as a quit key does: the game saves and goes to the title screen ({@link #kiln$askLeave}). */
    public void disconnectFromWorld(net.minecraft.network.chat.Component reason) {
        kiln$askLeave();
    }

    /**
     * Kiln's own: a mod leaves the world (a quit or panic key, {@code disconnect}): the game is asked once to leave
     * it as its pause menu's Save and Quit to Title (or Disconnect, on a server) does; Kiln leaves it with the game
     * ({@link #kiln$leave}). Out of a world nothing is asked.
     */
    public void kiln$askLeave() {
        if (level == null || leaveAsked) {
            return;
        }
        leaveAsked = true;
        Native.log("a mod left the world: the game saves it and goes to the title screen");
        kiln.Back.act("leave_world", "1");
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.renderer.GameRenderer gameRenderer;
    public void stop() { kiln.Missing.called("net.minecraft.client.Minecraft.stop"); }
    public net.minecraft.client.renderer.item.ItemModelResolver getItemModelResolver() { kiln.Missing.called("net.minecraft.client.Minecraft.getItemModelResolver"); return null; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.resources.Identifier DEFAULT_FONT = null;
    private java.util.concurrent.CompletableFuture<com.mojang.authlib.services.ProfileResult> profileFuture;
    public java.lang.Object sdlEventHandler;
    public net.minecraft.client.DeltaTracker.Timer deltaTracker;
    public java.lang.Object levelExtractor;
    public java.lang.Object levelRenderer;
    private net.minecraft.client.renderer.item.ItemModelResolver itemModelResolver;
    private java.net.Proxy proxy;
    private boolean offlineDeveloperMode;
    public net.minecraft.server.packs.repository.PackRepository resourcePackRepository;
    public net.minecraft.client.sounds.MusicManager musicManager;
    public java.lang.Object fontManager;
    private com.mojang.authlib.minecraft.UserApiService userApiService;
    private java.lang.Object playerSocialManager;
    private java.lang.Object remoteFriendListUpdateHandler;
    private net.minecraft.client.multiplayer.ProfileKeyPairManager profileKeyPairManager;
    private com.mojang.realmsclient.gui.RealmsDataFetcher realmsDataFetcher;
    private net.minecraft.client.server.IntegratedServer singleplayerServer;
    public net.minecraft.world.entity.Entity crosshairPickEntity;
    private int rightClickDelay;
    public int missTime;
    private java.lang.Thread gameThread;
    public boolean wireframe;
    public boolean smartCull;
    private net.minecraft.client.multiplayer.chat.report.ReportingContext reportingContext;
    public long clientTickCount;
    public boolean isGameLoadFinished() { kiln.Missing.called("net.minecraft.client.Minecraft.isGameLoadFinished"); return false; }
    public void updateTitle() { kiln.Missing.called("net.minecraft.client.Minecraft.updateTitle"); }
    public static net.minecraft.util.ModCheck checkModStatus() { kiln.Missing.called("net.minecraft.client.Minecraft.checkModStatus"); return new net.minecraft.util.ModCheck((net.minecraft.util.ModCheck.Confidence) null, (java.lang.String) null); }
    public void delayCrash(net.minecraft.CrashReport p0) { kiln.Missing.called("net.minecraft.client.Minecraft.delayCrash"); }
    public static void saveReport(java.io.File p0, net.minecraft.CrashReport p1) { kiln.Missing.called("net.minecraft.client.Minecraft.saveReport"); }
    public static void crash(net.minecraft.client.Minecraft p0, java.io.File p1, net.minecraft.CrashReport p2, int p3) { kiln.Missing.called("net.minecraft.client.Minecraft.crash"); }
    public boolean isEnforceUnicode() { kiln.Missing.called("net.minecraft.client.Minecraft.isEnforceUnicode"); return false; }
    public java.util.concurrent.CompletableFuture<java.lang.Void> reloadResourcePacks() { kiln.Missing.called("net.minecraft.client.Minecraft.reloadResourcePacks"); return null; }
    public net.minecraft.world.level.storage.LevelStorageSource getLevelSource() { kiln.Missing.called("net.minecraft.client.Minecraft.getLevelSource"); return null; }
    public void exitWorldAndClose() { kiln.Missing.called("net.minecraft.client.Minecraft.exitWorldAndClose"); }
    private void runTick(boolean p0) { kiln.Missing.called("net.minecraft.client.Minecraft.runTick"); }
    public void renderFrame(boolean p0) { kiln.Missing.called("net.minecraft.client.Minecraft.renderFrame"); }
    public boolean debugClientMetricsStart(java.util.function.Consumer<net.minecraft.network.chat.Component> p0) { kiln.Missing.called("net.minecraft.client.Minecraft.debugClientMetricsStart"); return false; }
    public boolean isRunning() { kiln.Missing.called("net.minecraft.client.Minecraft.isRunning"); return false; }
    public void pauseGame(boolean p0) { kiln.Missing.called("net.minecraft.client.Minecraft.pauseGame"); }
    public net.minecraft.client.sounds.MusicManager getMusicManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getMusicManager"); return null; }
    public boolean isMultiplayerServer() { kiln.Missing.called("net.minecraft.client.Minecraft.isMultiplayerServer"); return false; }
    public double getGpuUtilization() { kiln.Missing.called("net.minecraft.client.Minecraft.getGpuUtilization"); return 0.0; }
    public net.minecraft.client.multiplayer.ProfileKeyPairManager getProfileKeyPairManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getProfileKeyPairManager"); return null; }
    public java.lang.Object createWorldOpenFlows() { kiln.Missing.called("net.minecraft.client.Minecraft.createWorldOpenFlows"); return null; }
    public void doWorldLoad(net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess p0, net.minecraft.server.packs.repository.PackRepository p1, net.minecraft.server.WorldStem p2, java.util.Optional<net.minecraft.world.level.gamerules.GameRules> p3, boolean p4) { kiln.Missing.called("net.minecraft.client.Minecraft.doWorldLoad"); }
    public void disconnectWithSavingScreen() { kiln.Missing.called("net.minecraft.client.Minecraft.disconnectWithSavingScreen"); }
    public void disconnectWithProgressScreen() { kiln.Missing.called("net.minecraft.client.Minecraft.disconnectWithProgressScreen"); }
    public boolean isBlocked(java.util.UUID p0) { kiln.Missing.called("net.minecraft.client.Minecraft.isBlocked"); return false; }
    public net.minecraft.client.multiplayer.chat.ChatAbilities computeChatAbilities() { kiln.Missing.called("net.minecraft.client.Minecraft.computeChatAbilities"); return null; }
    public boolean isDemo() { kiln.Missing.called("net.minecraft.client.Minecraft.isDemo"); return false; }
    public boolean canSwitchGameMode() { kiln.Missing.called("net.minecraft.client.Minecraft.canSwitchGameMode"); return false; }
    public net.minecraft.CrashReport fillReport(net.minecraft.CrashReport p0) { kiln.Missing.called("net.minecraft.client.Minecraft.fillReport"); return null; }
    public static void fillReport(net.minecraft.client.Minecraft p0, net.minecraft.client.resources.language.LanguageManager p1, java.lang.String p2, net.minecraft.client.Options p3, net.minecraft.CrashReport p4) { kiln.Missing.called("net.minecraft.client.Minecraft.fillReport"); }
    public java.util.concurrent.CompletableFuture<java.lang.Void> delayTextureReload() { kiln.Missing.called("net.minecraft.client.Minecraft.delayTextureReload"); return null; }
    public boolean isLocalServer() { kiln.Missing.called("net.minecraft.client.Minecraft.isLocalServer"); return false; }
    public com.mojang.authlib.GameProfile getGameProfile() { kiln.Missing.called("net.minecraft.client.Minecraft.getGameProfile"); return null; }
    public java.net.Proxy getProxy() { kiln.Missing.called("net.minecraft.client.Minecraft.getProxy"); return null; }
    public java.lang.Object getShaderManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getShaderManager"); return null; }
    public net.minecraft.server.packs.repository.PackRepository getResourcePackRepository() { kiln.Missing.called("net.minecraft.client.Minecraft.getResourcePackRepository"); return null; }
    public net.minecraft.server.packs.VanillaPackResources getVanillaPackResources() { kiln.Missing.called("net.minecraft.client.Minecraft.getVanillaPackResources"); return null; }
    public net.minecraft.client.resources.server.DownloadedPackSource getDownloadedPackSource() { kiln.Missing.called("net.minecraft.client.Minecraft.getDownloadedPackSource"); return null; }
    public java.nio.file.Path getResourcePackDirectory() { kiln.Missing.called("net.minecraft.client.Minecraft.getResourcePackDirectory"); return null; }
    public net.minecraft.sounds.Music getSituationalMusic() { kiln.Missing.called("net.minecraft.client.Minecraft.getSituationalMusic"); return new net.minecraft.sounds.Music((net.minecraft.core.Holder) null, (int) 0, (int) 0, (boolean) false); }
    public net.minecraft.server.Services services() { kiln.Missing.called("net.minecraft.client.Minecraft.services"); return new net.minecraft.server.Services((com.mojang.authlib.minecraft.SessionService) null, (com.mojang.authlib.services.ServicesKeySet) null, (com.mojang.authlib.GameProfileRepository) null, (net.minecraft.server.players.UserNameToIdResolver) null, (net.minecraft.server.players.ProfileResolver) null); }
    public net.minecraft.client.resources.SkinManager getSkinManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getSkinManager"); return null; }
    public void setCameraEntity(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.client.Minecraft.setCameraEntity"); }
    public boolean shouldEntityAppearGlowing(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.client.Minecraft.shouldEntityAppearGlowing"); return false; }
    public java.lang.Thread getRunningThread() { kiln.Missing.called("net.minecraft.client.Minecraft.getRunningThread"); return null; }
    public java.lang.Object getEntityRenderDispatcher() { kiln.Missing.called("net.minecraft.client.Minecraft.getEntityRenderDispatcher"); return null; }
    public java.lang.Object getBlockEntityRenderDispatcher() { kiln.Missing.called("net.minecraft.client.Minecraft.getBlockEntityRenderDispatcher"); return null; }
    public java.lang.Object getMapRenderer() { kiln.Missing.called("net.minecraft.client.Minecraft.getMapRenderer"); return null; }
    public com.mojang.datafixers.DataFixer getFixerUpper() { kiln.Missing.called("net.minecraft.client.Minecraft.getFixerUpper"); return null; }
    public net.minecraft.client.color.block.BlockColors getBlockColors() { kiln.Missing.called("net.minecraft.client.Minecraft.getBlockColors"); return null; }
    public boolean showOnlyReducedInfo() { kiln.Missing.called("net.minecraft.client.Minecraft.showOnlyReducedInfo"); return false; }
    public net.minecraft.client.tutorial.Tutorial getTutorial() { kiln.Missing.called("net.minecraft.client.Minecraft.getTutorial"); return null; }
    public net.minecraft.client.HotbarManager getHotbarManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getHotbarManager"); return null; }
    public net.minecraft.client.resources.palette.PalettedTextureManager getPalettedTextureManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getPalettedTextureManager"); return null; }
    public net.minecraft.client.resources.MapTextureManager getMapTextureManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getMapTextureManager"); return null; }
    public java.lang.Object getPlayerSocialManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getPlayerSocialManager"); return null; }
    public java.lang.Object windowSurface() { kiln.Missing.called("net.minecraft.client.Minecraft.windowSurface"); return null; }
    public java.lang.Object getFramerateLimitTracker() { kiln.Missing.called("net.minecraft.client.Minecraft.getFramerateLimitTracker"); return null; }
    public void updateMaxMipLevel(int p0) { kiln.Missing.called("net.minecraft.client.Minecraft.updateMaxMipLevel"); }
    public net.minecraft.client.model.geom.EntityModelSet getEntityModels() { kiln.Missing.called("net.minecraft.client.Minecraft.getEntityModels"); return null; }
    public net.minecraft.client.InputType getLastInputType() { kiln.Missing.called("net.minecraft.client.Minecraft.getLastInputType"); return null; }
    public void setLastInputType(net.minecraft.client.InputType p0) { kiln.Missing.called("net.minecraft.client.Minecraft.setLastInputType"); }
    public net.minecraft.client.GameNarrator getNarrator() { kiln.Missing.called("net.minecraft.client.Minecraft.getNarrator"); return null; }
    public net.minecraft.client.multiplayer.chat.report.ReportingContext getReportingContext() { kiln.Missing.called("net.minecraft.client.Minecraft.getReportingContext"); return null; }
    public net.minecraft.world.level.validation.DirectoryValidator directoryValidator() { kiln.Missing.called("net.minecraft.client.Minecraft.directoryValidator"); return null; }
    public net.minecraft.client.renderer.PlayerSkinRenderCache playerSkinRenderCache() { kiln.Missing.called("net.minecraft.client.Minecraft.playerSkinRenderCache"); return null; }
    public void invalidateSurfaceConfiguration() { kiln.Missing.called("net.minecraft.client.Minecraft.invalidateSurfaceConfiguration"); }
    public net.minecraft.network.PacketProcessor packetProcessor() { kiln.Missing.called("net.minecraft.client.Minecraft.packetProcessor"); return null; }
    public void pick(float p0) { kiln.Missing.called("net.minecraft.client.Minecraft.pick"); }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
