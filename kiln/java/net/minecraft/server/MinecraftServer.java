package net.minecraft.server;

import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Kiln's stand-in for {@code MinecraftServer}: the integrated server Horizon runs, as a mod's code on Kiln's server
 * side reaches it ({@code level.getServer()}). Its recipes are the game's.
 */
public class MinecraftServer extends net.minecraft.util.thread.BlockableEventLoop<Runnable> {
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

    /**
     * The server's tick, where Horizon's server ticks (its {@code server_tick} start call, D-200): Fabric's
     * {@code START_SERVER_TICK} at its head, as the Fabric API's mixin fires it; the levels tick after it in the game
     * and {@code END_SERVER_TICK} comes with the end call, so a mixin at its TAIL runs before the levels tick, not after.
     */
    public void tickServer(java.util.function.BooleanSupplier hasTimeLeft) {
        kiln$ticks++;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(this);
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

    /** The names of the players on the server. */
    public String[] getPlayerNames() {
        return players.getPlayerNamesArray();
    }

    /** The server's registries: Kiln's (what the mods register and the game's entries Kiln names, D-112). */
    public net.minecraft.core.RegistryAccess.Frozen registryAccess() {
        return net.minecraft.core.RegistryAccess.KILN;
    }

    /** Whether {@code player} owns this server: a single-player world's player ({@code IntegratedServer}); none here. */
    public boolean isSingleplayerOwner(net.minecraft.server.players.NameAndId player) {
        return false;
    }

    /**
     * The operator permissions {@code player} has: the level of the player whose command is running (the game gives
     * it with the command, D-107); none for anyone else, as Kiln doesn't know the server's operators.
     */
    public net.minecraft.server.permissions.LevelBasedPermissionSet getProfilePermissions(
            net.minecraft.server.players.NameAndId player) {
        return net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(kiln.ServerCommands.levelOf(player.id())));
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

    @Override
    public void close() {}

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.server.level.ServerLevel getLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.getLevel"); return null; }
    public net.minecraft.server.ReloadableServerRegistries.Holder reloadableRegistries() { kiln.Missing.called("net.minecraft.server.MinecraftServer.reloadableRegistries"); return null; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.world.level.gamerules.GameRules gameRules;
    public net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> registries;
    public java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.server.level.ServerLevel> levels;
    public long nextTickTimeNanos;
    public java.util.concurrent.Executor executor;
    public net.minecraft.server.MinecraftServer.ReloadableResources resources;
    public net.minecraft.world.level.storage.WorldData worldData;
    private int emptyTicks;
    public net.minecraft.world.level.GameType getDefaultGameType() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getDefaultGameType"); return null; }
    public boolean isHardcore() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isHardcore"); return false; }
    public boolean saveAllChunks(boolean p0, boolean p1, boolean p2) { kiln.Missing.called("net.minecraft.server.MinecraftServer.saveAllChunks"); return false; }
    public java.lang.String getLocalIp() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getLocalIp"); return null; }
    public void halt(boolean p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.halt"); }
    public boolean haveTime() { kiln.Missing.called("net.minecraft.server.MinecraftServer.haveTime"); return false; }
    protected void waitForTasks() { kiln.Missing.called("net.minecraft.server.MinecraftServer.waitForTasks"); }
    public java.nio.file.Path getServerDirectory() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getServerDirectory"); return null; }
    public boolean isShutdown() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isShutdown"); return false; }
    public net.minecraft.server.level.ServerLevel overworld() { kiln.Missing.called("net.minecraft.server.MinecraftServer.overworld"); return null; }
    public java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> levelKeys() { kiln.Missing.called("net.minecraft.server.MinecraftServer.levelKeys"); return null; }
    public java.lang.Iterable<net.minecraft.server.level.ServerLevel> getAllLevels() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getAllLevels"); return null; }
    public java.lang.String getServerVersion() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getServerVersion"); return null; }
    public java.lang.String getServerModName() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getServerModName"); return null; }
    public net.minecraft.world.clock.ServerClockManager clockManager() { kiln.Missing.called("net.minecraft.server.MinecraftServer.clockManager"); return null; }
    public void sendSystemMessage(net.minecraft.network.chat.Component p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.sendSystemMessage"); }
    public int getPort() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getPort"); return 0; }
    public com.mojang.authlib.GameProfile getSingleplayerProfile() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getSingleplayerProfile"); return null; }
    public boolean isSingleplayer() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isSingleplayer"); return false; }
    public void setDifficulty(net.minecraft.world.Difficulty p0, boolean p1) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setDifficulty"); }
    public boolean isDedicatedServer() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isDedicatedServer"); return false; }
    public boolean usesAuthentication() { kiln.Missing.called("net.minecraft.server.MinecraftServer.usesAuthentication"); return false; }
    public void setUsesAuthentication(boolean p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setUsesAuthentication"); }
    public java.lang.String getMotd() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getMotd"); return null; }
    public void setMotd(java.lang.String p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setMotd"); }
    public boolean isStopped() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isStopped"); return false; }
    public boolean isPublished() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isPublished"); return false; }
    public net.minecraft.server.network.ServerConnectionListener getConnection() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getConnection"); return null; }
    public boolean isReady() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isReady"); return false; }
    /** The server's ticks since Java started (D-211): {@link #tickServer} counts them, as vanilla's tickCount. */
    private int kiln$ticks;
    private net.minecraft.world.level.storage.WorldData kiln$worldData;

    public int getTickCount() {
        return kiln$ticks;
    }
    public boolean isUnderSpawnProtection(net.minecraft.server.level.ServerLevel p0, net.minecraft.core.BlockPos p1, net.minecraft.world.entity.player.Player p2) { kiln.Missing.called("net.minecraft.server.MinecraftServer.isUnderSpawnProtection"); return false; }
    public net.minecraft.server.Services services() { kiln.Missing.called("net.minecraft.server.MinecraftServer.services"); return new net.minecraft.server.Services((com.mojang.authlib.minecraft.SessionService) null, (com.mojang.authlib.services.ServicesKeySet) null, (com.mojang.authlib.GameProfileRepository) null, (net.minecraft.server.players.UserNameToIdResolver) null, (net.minecraft.server.players.ProfileResolver) null); }
    public net.minecraft.network.protocol.status.ServerStatus getStatus() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getStatus"); return new net.minecraft.network.protocol.status.ServerStatus((net.minecraft.network.chat.Component) null, (java.util.Optional) null, (java.util.Optional) null, (java.util.Optional) null, (boolean) false); }
    public int getAbsoluteMaxWorldSize() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getAbsoluteMaxWorldSize"); return 0; }
    public boolean scheduleExecutables() { kiln.Missing.called("net.minecraft.server.MinecraftServer.scheduleExecutables"); return false; }
    public void executeIfPossible(java.lang.Runnable p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.executeIfPossible"); }
    public java.lang.Thread getRunningThread() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getRunningThread"); return null; }
    public int getCompressionThreshold() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getCompressionThreshold"); return 0; }
    public com.mojang.datafixers.DataFixer getFixerUpper() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getFixerUpper"); return null; }
    public net.minecraft.server.ServerAdvancementManager getAdvancements() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getAdvancements"); return null; }
    public net.minecraft.server.ServerFunctionManager getFunctions() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getFunctions"); return null; }
    public java.util.concurrent.CompletableFuture<java.lang.Void> reloadResources(java.util.Collection<java.lang.String> p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.reloadResources"); return null; }
    public void kickUnlistedPlayers() { kiln.Missing.called("net.minecraft.server.MinecraftServer.kickUnlistedPlayers"); }
    public net.minecraft.server.packs.repository.PackRepository getPackRepository() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getPackRepository"); return null; }
    public net.minecraft.commands.CommandSourceStack createCommandSourceStack() { kiln.Missing.called("net.minecraft.server.MinecraftServer.createCommandSourceStack"); return null; }
    public net.minecraft.server.level.ServerLevel findRespawnDimension() { kiln.Missing.called("net.minecraft.server.MinecraftServer.findRespawnDimension"); return null; }
    public net.minecraft.world.level.levelgen.WorldGenSettings getWorldGenSettings() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getWorldGenSettings"); return null; }
    public net.minecraft.server.ServerScoreboard getScoreboard() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getScoreboard"); return null; }
    public net.minecraft.world.level.storage.CommandStorage getCommandStorage() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getCommandStorage"); return null; }
    public net.minecraft.server.bossevents.CustomBossEvents getCustomBossEvents() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getCustomBossEvents"); return null; }
    public void setWeatherParameters(int p0, int p1, boolean p2, boolean p3) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setWeatherParameters"); }
    public net.minecraft.world.level.saveddata.WeatherData getWeatherData() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getWeatherData"); return null; }
    public boolean isEnforceWhitelist() { kiln.Missing.called("net.minecraft.server.MinecraftServer.isEnforceWhitelist"); return false; }
    public void setEnforceWhitelist(boolean p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setEnforceWhitelist"); }
    public void setUsingWhitelist(boolean p0) { kiln.Missing.called("net.minecraft.server.MinecraftServer.setUsingWhitelist"); }
    public float getCurrentSmoothedTickTime() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getCurrentSmoothedTickTime"); return 0.0F; }
    public net.minecraft.server.ServerTickRateManager tickRateManager() { kiln.Missing.called("net.minecraft.server.MinecraftServer.tickRateManager"); return null; }
    public long getAverageTickTimeNanos() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getAverageTickTimeNanos"); return 0L; }
    public long[] getTickTimesNanos() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getTickTimesNanos"); return null; }
    public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager getStructureTemplateManager() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getStructureTemplateManager"); return null; }
    /** The world's data as far as Kiln has it ({@link kiln.WorldDataView}, D-211): an initialized world named by its folder (REI names a world by it, D-215). */
    public net.minecraft.world.level.storage.WorldData getWorldData() {
        if (worldData != null) {
            return worldData;
        }
        if (kiln$worldData == null) {
            kiln$worldData = kiln.WorldDataView.create(() -> storageSource.getLevelId());
        }
        return kiln$worldData;
    }
    public net.minecraft.server.packs.resources.ResourceManager getResourceManager() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getResourceManager"); return null; }
    public net.minecraft.world.level.storage.SavedDataStorage getDataStorage() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getDataStorage"); return null; }
    public net.minecraft.world.level.gamerules.GameRules getGameRules() { kiln.Missing.called("net.minecraft.server.MinecraftServer.getGameRules"); return null; }
    public void reportChunkLoadFailure(java.lang.Throwable p0, net.minecraft.world.level.chunk.storage.RegionStorageInfo p1, net.minecraft.world.level.ChunkPos p2) { kiln.Missing.called("net.minecraft.server.MinecraftServer.reportChunkLoadFailure"); }
    public void reportChunkSaveFailure(java.lang.Throwable p0, net.minecraft.world.level.chunk.storage.RegionStorageInfo p1, net.minecraft.world.level.ChunkPos p2) { kiln.Missing.called("net.minecraft.server.MinecraftServer.reportChunkSaveFailure"); }
    public net.minecraft.network.PacketProcessor packetProcessor() { kiln.Missing.called("net.minecraft.server.MinecraftServer.packetProcessor"); return null; }
    public static record ReloadableResources(net.minecraft.server.packs.resources.CloseableResourceManager resourceManager, net.minecraft.server.ReloadableServerResources managers) implements java.lang.AutoCloseable {
        public void close() { kiln.Missing.called("net.minecraft.server.MinecraftServer.ReloadableResources.close"); }
    }
    public static enum MultiplayerScope {
        OFF, LAN;
        public net.minecraft.network.chat.Component getDisplayName() { kiln.Missing.called("net.minecraft.server.MinecraftServer.MultiplayerScope.getDisplayName"); return null; }
        public net.minecraft.network.chat.Component getTooltip() { kiln.Missing.called("net.minecraft.server.MinecraftServer.MultiplayerScope.getTooltip"); return null; }
    }
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
