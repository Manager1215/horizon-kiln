package net.minecraft.client.server;

/**
 * Kiln's stand-in for {@code IntegratedServer}: single player's server in the window's game. It is Kiln's one {@code
 * MinecraftServer} (Horizon's server, as the Java mods' server code reaches it), which {@code
 * Minecraft.getSingleplayerServer} gives in single player (D-167): a mod's per-world files on the client (a recipe
 * viewer's bookmarks) are named by its world's folder.
 */
public class IntegratedServer extends net.minecraft.server.MinecraftServer {
    protected IntegratedServer() {}

    /** Kiln's own: the one server ({@code MinecraftServer.kiln$server}). */
    public static IntegratedServer kiln$make() {
        return new IntegratedServer();
    }

    /** Whether the world is open to LAN: Kiln doesn't know it, so not. */
    public boolean isPublished() {
        return false;
    }
}
