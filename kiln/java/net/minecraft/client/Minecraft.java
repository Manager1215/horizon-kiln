package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * Kiln's stand-in for {@code Minecraft}, the client: Horizon as Java mods see it. Kiln updates it from the game
 * before each tick: {@code player} and {@code level} are null outside a world, as in vanilla.
 */
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();

    public final Font font = new Font();
    public final Gui gui = new Gui();
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
