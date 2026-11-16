package net.minecraft.server;

import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Kiln's stand-in for {@code MinecraftServer}: the integrated server Horizon runs, as a mod's code on Kiln's server
 * side reaches it ({@code level.getServer()}). Its recipes are the game's.
 */
public class MinecraftServer {
    // Single player's server, as the window's game's Minecraft.getSingleplayerServer gives it too (D-167).
    private static final MinecraftServer KILN = net.minecraft.client.server.IntegratedServer.kiln$make();
    /** The open world's folder ({@code LevelStorageAccess.getLevelId}: a mod's per-world files, D-167). */
    public final net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storageSource =
            new net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess();
    private final RecipeManager recipes = new RecipeManager(null);
    private final net.minecraft.server.players.PlayerList players = new net.minecraft.server.players.PlayerList(this);

    protected MinecraftServer() {}

    /** Kiln's own: the one server. */
    public static MinecraftServer kiln$server() {
        return KILN;
    }

    public RecipeManager getRecipeManager() {
        return recipes;
    }

    /** The players on the server, as Horizon's says them ({@link kiln.Players}). */
    public net.minecraft.server.players.PlayerList getPlayerList() {
        return players;
    }

    /** The server's commands: the Java mods' ({@link kiln.ServerCommands}). */
    public net.minecraft.commands.Commands getCommands() {
        return kiln.ServerCommands.commands();
    }

    /**
     * A file or folder of the world's folder: the one Horizon's server says with its calls ({@link
     * kiln.Server#worldFolder}); before its first call, {@code world} in Kiln's folder (the mods' game folder).
     */
    public java.nio.file.Path getWorldPath(net.minecraft.world.level.storage.LevelResource resource) {
        java.nio.file.Path world = kiln.Server.worldFolder();
        if (world == null) {
            world = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("world");
        }
        return world.resolve(resource.getId()).normalize();
    }

    /** Tasks other threads handed the server ({@link #execute}), run as its next call begins. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * Runs the task on the server's turn, as the next server call begins ({@link #kiln$runTasks}): Horizon's server
     * runs Kiln's Java only in its calls. A mod's thread (mezz_config's file watcher) hands its work to the server
     * this way. One handed over during a call runs with the next one, not at once as vanilla's on its own thread.
     */
    public void execute(Runnable task) {
        tasks.add(task);
    }

    /** Kiln's own: the tasks handed to the server meanwhile, run as a server call begins. */
    public void kiln$runTasks() {
        for (Runnable task; (task = tasks.poll()) != null; ) {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                kiln.Native.log("a task handed to the server threw " + e);
            }
        }
    }

    /** Whether this is the server's thread: Kiln runs a mod's server code only in the server's calls, there. */
    public boolean isSameThread() {
        return true;
    }

    /** The permissions an operator has here: single player's integrated server gives its operators the owner's. */
    public net.minecraft.server.permissions.LevelBasedPermissionSet operatorUserPermissions() {
        return net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER;
    }

    public int getPlayerCount() {
        return players.getPlayerCount();
    }

    /**
     * The integrated server loads its world: in Kiln, as a single-player world is joined, before the client's
     * connection to it is made ({@link #kiln$loadLevel}). Horizon loads the world itself; the mods' mixins in this run
     * there (D-093).
     */
    protected void loadLevel() {}

    /**
     * Kiln's own: {@link #loadLevel}, called by Kiln's runtime as a single-player world is joined, with Fabric's {@code
     * SERVER_STARTING} before it and {@code SERVER_STARTED} after, as the game's server starts around its levels.
     */
    public void kiln$loadLevel() {
        running = true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(this);
        loadLevel();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(this);
    }

    /** Whether the integrated server runs: from a single-player world's join to its leaving. */
    private boolean running;

    public boolean isRunning() {
        return running;
    }

    /** Kiln's own: the single-player world left, so its server stops: Fabric's {@code SERVER_STOPPING}, then {@code SERVER_STOPPED}. */
    public void kiln$stop() {
        if (!running) {
            return;
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(this);
        running = false;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(this);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.server.level.ServerLevel getLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.getLevel"); return null; }
    public net.minecraft.server.ReloadableServerRegistries.Holder reloadableRegistries() { kiln.Missing.called("net.minecraft.server.MinecraftServer.reloadableRegistries"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
