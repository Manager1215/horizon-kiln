package net.minecraft.client;

import net.minecraft.client.gui.Font;
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
    public ClientLevel level;
    public LocalPlayer player;
    private ClientPacketListener connection;
    private boolean paused;

    private Minecraft() {}

    public static Minecraft getInstance() {
        return INSTANCE;
    }

    /** The connection to the world; null outside one. */
    public ClientPacketListener getConnection() {
        return connection;
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
        }
        level.kiln$update(dimension, gameTime);
    }

    /** Kiln's own: the connection, made before the first tick in a world so advancements can arrive. */
    public ClientPacketListener kiln$connection() {
        if (connection == null) {
            connection = new ClientPacketListener();
        }
        return connection;
    }
}
