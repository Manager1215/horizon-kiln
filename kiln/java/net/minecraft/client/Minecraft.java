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
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();

    public final Font font = new Font();
    public final Gui gui = new Gui(this);
    private final DebugScreenOverlay debugOverlay = new DebugScreenOverlay();
    private final com.mojang.blaze3d.platform.Window window = new com.mojang.blaze3d.platform.Window();
    /** The pointer, as the game had it with the tick. */
    public final MouseHandler mouseHandler = new MouseHandler(this);
    public ClientLevel level;
    public LocalPlayer player;
    private ClientPacketListener connection;
    private boolean paused;
    private int fps;
    private long frameTimeNs;

    private Minecraft() {}

    public static Minecraft getInstance() {
        return INSTANCE;
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

    /** The player signed in: the window's player's name and UUID; null outside a world. */
    public User getUser() {
        return player == null ? null : new User(player.getScoreboardName(), player.getUUID(), "", java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    /** What the camera follows: the player (Kiln doesn't know when a spectator looks through another entity). */
    public net.minecraft.world.entity.Entity getCameraEntity() {
        return player;
    }

    /** Kiln draws the mods' HUD once a tick, at the tick: no part of a tick has passed. */
    public DeltaTracker getDeltaTracker() {
        return DeltaTracker.ZERO;
    }

    /** Kiln's own: single player or not, as the game said. */
    public void kiln$singleplayer(boolean singleplayer) {
        this.singleplayer = singleplayer;
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
            return;
        }
        kiln$connection();
        if (level == null) {
            level = new ClientLevel();
            player = new LocalPlayer();
            player.connection = connection;
        }
        level.kiln$update(dimension, gameTime);
    }

    /** Either shift key held down, as the game said with the tick. */
    public boolean hasShiftDown() {
        return KeyMapping.kiln$isHeld("key.keyboard.left.shift") || KeyMapping.kiln$isHeld("key.keyboard.right.shift");
    }

    /** Either control key held down (on macOS, command), as the game said with the tick. */
    public boolean hasControlDown() {
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
        return mac ? KeyMapping.kiln$isHeld("key.keyboard.left.win") || KeyMapping.kiln$isHeld("key.keyboard.right.win")
                : KeyMapping.kiln$isHeld("key.keyboard.left.control") || KeyMapping.kiln$isHeld("key.keyboard.right.control");
    }

    /** Either alt key held down, as the game said with the tick. */
    public boolean hasAltDown() {
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
            }
            ClientTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
        }
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(this);
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
     * The client's level: a world joined, or another dimension's level once Horizon has loaded it. Fabric's {@code
     * AFTER_CLIENT_LEVEL_CHANGE} comes in it, as Fabric's own comes where the game hands its engines the new level.
     */
    public void setLevel(ClientLevel level) {
        this.level = level;
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
            Native.log("Kiln can't leave the game's world for a mod yet: it stays in it");
            return;
        }
        ClientPacketListener closed = connection;
        clearClientLevel(screen);
        connection = null;
        if (closed != null) {
            ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(closed, this);
        }
    }

    /**
     * The client's level gone with the world left: no level, no player. The game shows {@code screen} next; out of a
     * world Kiln doesn't know the game's screens, and shows nothing.
     */
    public void clearClientLevel(Screen screen) {
        level = null;
        player = null;
    }

    /**
     * Kiln's own: the game left its world for {@code screen}, so Kiln leaves it as the game does ({@link
     * #disconnect(Screen, boolean)}), whose start sees the connection, level and player the world had ({@link
     * #kiln$update} let them go with the tick). After, they are the tick's again, whatever a mixin did.
     */
    public void kiln$leave(ClientPacketListener connection, ClientLevel level, LocalPlayer player, Screen screen) {
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.renderer.GameRenderer gameRenderer;
    public net.minecraft.client.KeyboardHandler keyboardHandler;
    public void stop() { kiln.Missing.called("net.minecraft.client.Minecraft.stop"); }
    public void disconnectFromWorld(net.minecraft.network.chat.Component p0) { kiln.Missing.called("net.minecraft.client.Minecraft.disconnectFromWorld"); }
    public net.minecraft.client.resources.model.ModelManager getModelManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getModelManager"); return null; }
    public net.minecraft.client.resources.model.sprite.AtlasManager getAtlasManager() { kiln.Missing.called("net.minecraft.client.Minecraft.getAtlasManager"); return null; }
    public net.minecraft.client.renderer.item.ItemModelResolver getItemModelResolver() { kiln.Missing.called("net.minecraft.client.Minecraft.getItemModelResolver"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
