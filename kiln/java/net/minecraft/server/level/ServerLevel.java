package net.minecraft.server.level;

import java.util.HashMap;
import java.util.Map;
import kiln.BlockEntities;
import kiln.Server;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Kiln's stand-in for {@code ServerLevel}: a dimension where the game's rules run, as Horizon's integrated server
 * said it was when it called the mod. What the mod's code does to it, the server then does.
 */
public class ServerLevel extends Level implements net.minecraft.world.level.WorldGenLevel, ServerEntityGetter {
    private final SavedDataStorage storage;
    /** The blocks this call read or set: asked of the game once each. */
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    /** Whether the sky is seen from a position, and the weather: asked once each this call. */
    private final Map<BlockPos, Boolean> sky = new HashMap<>();
    private float[] weather;
    /** Whether the game has a chunk, by chunk x and z: asked once each this call. */
    private final Map<Long, Boolean> chunks = new HashMap<>();
    /** The call this level last answered in: one kept past its call (a mod's cache of it) asks again in the next. */
    private String call = Server.currentCall();
    /** The newest level of each dimension: the running call's, for one a mod kept from an earlier call. */
    private static final Map<String, ServerLevel> NEWEST = new HashMap<>();
    private final String dimensionId;

    /** Kiln's own: a level of the world whose data is in {@code storage}. */
    public ServerLevel(String dimension, long gameTime, SavedDataStorage storage) {
        kiln$update(dimension, gameTime);
        this.storage = storage;
        this.dimensionId = dimension;
        NEWEST.put(dimension, this);
    }

    /**
     * Kiln's own: the running call's level of {@code dimension}, a new one when the call has none yet (a player in
     * another dimension, for {@code PlayerLookup}).
     */
    public static ServerLevel kiln$of(String dimension, long gameTime, SavedDataStorage storage) {
        ServerLevel newest = NEWEST.get(dimension);
        String now = Server.currentCall();
        return newest != null && now != null && now.equals(newest.call) ? newest
                : new ServerLevel(dimension, gameTime, storage);
    }

    /** The players in this level ({@link kiln.Players}). */
    public java.util.List<net.minecraft.server.level.ServerPlayer> players() {
        return new java.util.ArrayList<>(net.fabricmc.fabric.api.networking.v1.PlayerLookup.level(this));
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** The block at {@code pos}, as the game has it now: Kiln asks while the game waits for the mod's answer. */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        fresh();
        return blocks.computeIfAbsent(pos.immutable(), p -> BlockState.kiln$parse(
                Server.ask("block", Integer.toString(p.getX()), Integer.toString(p.getY()), Integer.toString(p.getZ()))));
    }

    /**
     * Whether the game has the chunk at chunk x and z loaded ({@code loaded <block x> <block z>}). A game that doesn't
     * know the question says nothing: loaded, as Kiln took every chunk before.
     */
    @Override
    public boolean hasChunk(int x, int z) {
        fresh();
        return chunks.computeIfAbsent(((long) x << 32) | (z & 0xFFFFFFFFL),
                c -> !Server.ask("loaded", Integer.toString(x << 4), Integer.toString(z << 4)).equals("false"));
    }

    /**
     * A mod's block entity there, as the game has it now; for the game's own containers (a chest's, a furnace's, a
     * brewing stand's), a {@code Container} of their slots for the call (D-199); none for the game's other block
     * entities.
     */
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        BlockEntity entity = BlockEntities.at(this, pos);
        return entity != null ? entity : kiln.GameContainers.entity(this, pos);
    }

    /** The game sets it once the mod's code is done ({@code block <x> <y> <z> <flags> <state>}). */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        fresh();
        BlockState before = blocks.get(pos);
        if (before == state) {
            return false;
        }
        blocks.put(pos.immutable(), state);
        BlockEntities.blockChanged(this, pos, state);
        Server.effect("block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + flags + " " + state.kiln$text());
        return true;
    }

    /**
     * The game breaks it once the mod's code is done ({@code destroy <x> <y> <z> <1 to drop>}), as its {@code
     * Level.destroyBlock}: the block's drops, its sound and particles, then the fluid there or air.
     */
    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth) {
        BlockState before = getBlockState(pos);
        if (before.isAir()) {
            return false;
        }
        BlockState after = Blocks.AIR.defaultBlockState();
        blocks.put(pos.immutable(), after);
        BlockEntities.blockChanged(this, pos, after);
        Server.effect("destroy " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + (drop ? 1 : 0));
        return true;
    }

    /** The game drops what the block drops ({@code drops <x> <y> <z> <state>}), by its loot table. */
    @Override
    public void kiln$dropResources(BlockPos pos, BlockState state) {
        Server.effect("drops " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state.kiln$text());
    }

    /** The game schedules it once the mod's code is done ({@code schedule <x> <y> <z> <block> <delay>}). */
    @Override
    public void kiln$scheduleTick(BlockPos pos, Block block, int delay) {
        Server.effect("schedule " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block.kiln$id() + " " + delay);
    }

    @Override
    public void kiln$drop(BlockPos pos, ItemStack stack) {
        Server.effect("drop " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + stack.kiln$snbt());
    }

    /** The world's saved data: as in vanilla 26, the world's, not the dimension's. */
    public SavedDataStorage getDataStorage() {
        return storage;
    }

    /**
     * Heard by the players near {@code x y z}. Vanilla leaves out {@code except}, whose own client played it; Kiln
     * runs no client half of the mod's code, so they hear it too.
     */
    @Override
    public void playSound(Entity except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch) {
        if (sound == null) {
            return;
        }
        Server.effect("sound " + sound.location() + " " + source.ordinal() + " " + x + " " + y + " " + z + " " + volume
                + " " + pitch);
    }

    @Override
    public net.minecraft.server.MinecraftServer getServer() {
        return net.minecraft.server.MinecraftServer.kiln$server();
    }

    public net.minecraft.world.item.crafting.RecipeManager recipeAccess() {
        return getServer().getRecipeManager();
    }

    /** The world's clocks: the server's, as the game has them (D-210). */
    public net.minecraft.world.clock.ServerClockManager clockManager() {
        return net.minecraft.world.clock.ServerClockManager.kiln$server();
    }

    /** Its type's clock's ticks (vanilla's): the time of day where the overworld's clock runs, 0 in the Nether. */
    @Override
    public long getDefaultClockTime() {
        return dimensionType().defaultClock().map(clock -> clockManager().getInstance(clock).totalTicks()).orElse(0L);
    }

    /** The structures the game generated in this level (D-210). */
    public net.minecraft.world.level.StructureManager structureManager() {
        return net.minecraft.world.level.StructureManager.kiln$of(this);
    }

    /** Whether the sky is seen from {@code pos}, as the game's light says: asked once a position each call. */
    @Override
    public boolean canSeeSky(BlockPos pos) {
        fresh();
        return sky.computeIfAbsent(pos.immutable(), p -> "true".equals(
                Server.ask("sky", Integer.toString(p.getX()), Integer.toString(p.getY()), Integer.toString(p.getZ()))));
    }

    @Override
    public float getRainLevel(float partialTick) {
        return weather()[0];
    }

    @Override
    public float getThunderLevel(float partialTick) {
        return weather()[1];
    }

    /** The dimension's environment: the sun's angle at a position, as the game has it now. */
    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return new EnvironmentAttributeSystem((attribute, pos) -> {
            if (attribute != EnvironmentAttributes.SUN_ANGLE) {
                return null;
            }
            String angle = Server.ask("sun_angle", Integer.toString(pos.getX()), Integer.toString(pos.getY()),
                    Integer.toString(pos.getZ()));
            return angle.isEmpty() ? null : Float.parseFloat(angle);
        });
    }

    /**
     * Kiln's own: this dimension's level as the running call has it, for a level a mod kept from an earlier call (a
     * Fabric {@code BlockApiCache}): what it has asked this call, and the call's game time. Itself when the call made
     * none of this dimension.
     */
    public ServerLevel kiln$now() {
        ServerLevel newest = NEWEST.get(dimensionId);
        String now = Server.currentCall();
        return newest != null && now != null && now.equals(newest.call) ? newest : this;
    }

    /** What this level learnt of the game in an earlier call is forgotten in a new one: the game has gone on. */
    private void fresh() {
        String now = Server.currentCall();
        if (now != null && !now.equals(call)) {
            call = now;
            blocks.clear();
            sky.clear();
            chunks.clear();
            weather = null;
        }
    }

    /** How much it rains and thunders now ({@code weather}, answered {@code <rain> <thunder>}), asked once a call. */
    private float[] weather() {
        fresh();
        if (weather == null) {
            String[] answer = Server.ask("weather").split(" ");
            weather = new float[] {0, 0};
            for (int i = 0; i < 2 && i < answer.length; i++) {
                try {
                    weather[i] = Float.parseFloat(answer[i]);
                } catch (NumberFormatException e) {
                    // The game said nothing: no rain.
                }
            }
        }
        return weather;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void resetEmptyTime() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.resetEmptyTime"); }
    public boolean addFreshEntity(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.addFreshEntity"); return false; }
    public void updateNeighborsAt(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1, net.minecraft.world.level.redstone.Orientation p2) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.updateNeighborsAt"); }
    // End of what tools/kiln-stubs.py generated.

    // The game's methods in the game's shape, as far as the calls the mods' mixins hook in them (Architectury's level
    // save, entity added and explosion events, D-218). Horizon saves the level, adds its entities and makes its
    // explosions: Kiln calls none of them, and what they call does nothing.

    /** The game's {@code entityManager}: the level's entities by section (Horizon keeps them). */
    @SuppressWarnings("rawtypes")
    final net.minecraft.world.level.entity.PersistentEntitySectionManager entityManager =
            new net.minecraft.world.level.entity.PersistentEntitySectionManager();

    /** The game's: the level saved, its chunks with {@code flush} unless {@code skip}. */
    public void save(net.minecraft.util.ProgressListener progress, boolean flush, boolean skip) {
        if (!skip) {
            getChunkSource().save(flush);
        }
    }

    /** The game's: a new entity handed to the level's sections; whether it was added. */
    @SuppressWarnings("unchecked")
    private boolean addEntity(Entity entity) {
        return entityManager.addNewEntity((net.minecraft.world.level.entity.EntityAccess) (Object) entity);
    }

    /** The game's explosion: made, then exploded. */
    public void explode(Entity source, net.minecraft.world.damagesource.DamageSource damageSource,
            net.minecraft.world.level.ExplosionDamageCalculator calculator, double x, double y, double z, float radius,
            boolean fire, Level.ExplosionInteraction interaction, net.minecraft.core.particles.ParticleOptions small,
            net.minecraft.core.particles.ParticleOptions large,
            net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> blocks,
            net.minecraft.core.Holder<SoundEvent> sound) {
        net.minecraft.world.level.ServerExplosion explosion = new net.minecraft.world.level.ServerExplosion(this,
                source, damageSource, calculator, new net.minecraft.world.phys.Vec3(x, y, z), radius, fire,
                net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        explosion.explode();
    }

    /** The level's chunks as far as Kiln knows them: whether the game has one ({@link #hasChunk}; D-161). */
    public ServerChunkCache getChunkSource() {
        ServerLevel level = this;
        return new ServerChunkCache() {
            @Override
            public boolean hasChunk(int x, int z) {
                return level.hasChunk(x, z);
            }

            @Override
            public Level getLevel() {
                return level;
            }
        };
    }

    /** The chunk at chunk x and z: its blocks are the level's; an empty one where the game has none loaded. */
    @Override
    public net.minecraft.world.level.chunk.LevelChunk getChunk(int x, int z) {
        net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);
        return hasChunk(x, z) ? new net.minecraft.world.level.chunk.LevelChunk(this, pos)
                : net.minecraft.world.level.chunk.LevelChunk.kiln$empty(this, pos);
    }

    /**
     * The level's tick, where Horizon ticks it (the {@code server_tick} level start call, D-200): Fabric's {@code
     * START_LEVEL_TICK} at its head; the level's own work is Horizon's, and {@code END_LEVEL_TICK} comes with the
     * level's end call.
     */
    public void tick(java.util.function.BooleanSupplier hasTimeLeft) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_LEVEL_TICK.invoker().onStartTick(this);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.core.BlockPos END_SPAWN_POINT = null;
    public static final net.minecraft.util.valueproviders.IntProvider RAIN_DELAY = null;
    public static final net.minecraft.util.valueproviders.IntProvider RAIN_DURATION = null;
    public static final net.minecraft.util.valueproviders.IntProvider THUNDER_DURATION = null;
    public boolean noSave;
    private java.util.List<net.minecraft.world.level.CustomSpawner> customSpawners;
    public boolean shouldTickBlocksAt(long p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.shouldTickBlocksAt"); return false; }
    public net.minecraft.server.ServerScoreboard getScoreboard() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getScoreboard"); return null; }
    public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getCurrentDifficultyAt"); return null; }
    public void updateNeighboursOnBlockSet(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.updateNeighboursOnBlockSet"); }
    public <T extends net.minecraft.world.entity.Entity> java.util.List<? extends T> getEntities(net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, T> p0, java.util.function.Predicate<? super T> p1) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getEntities"); return null; }
    public java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers(java.util.function.Predicate<? super net.minecraft.server.level.ServerPlayer> p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getPlayers"); return null; }
    public boolean addWithUUID(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.addWithUUID"); return false; }
    public boolean tryAddFreshEntityWithPassengers(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.tryAddFreshEntityWithPassengers"); return false; }
    public void unload(net.minecraft.world.level.chunk.LevelChunk p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.unload"); }
    public void levelEvent(net.minecraft.world.entity.Entity p0, int p1, net.minecraft.core.BlockPos p2, int p3) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.levelEvent"); }
    public int getLogicalHeight() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getLogicalHeight"); return 0; }
    public void broadcastEntityEvent(net.minecraft.world.entity.Entity p0, byte p1) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.broadcastEntityEvent"); }
    public net.minecraft.world.ticks.LevelTicks<net.minecraft.world.level.block.Block> getBlockTicks() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getBlockTicks"); return null; }
    public net.minecraft.world.ticks.LevelTicks<net.minecraft.world.level.material.Fluid> getFluidTicks() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getFluidTicks"); return null; }
    public net.minecraft.world.level.portal.PortalForcer getPortalForcer() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getPortalForcer"); return null; }
    public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager getStructureTemplateManager() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getStructureTemplateManager"); return null; }
    public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T p0, double p1, double p2, double p3, int p4, double p5, double p6, double p7, double p8) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.sendParticles"); return 0; }
    public <T extends net.minecraft.core.particles.ParticleOptions> boolean sendParticles(net.minecraft.server.level.ServerPlayer p0, T p1, boolean p2, boolean p3, double p4, double p5, double p6, int p7, double p8, double p9, double p10, double p11) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.sendParticles"); return false; }
    public boolean sendParticles(net.minecraft.server.level.ServerPlayer p0, boolean p1, double p2, double p3, double p4, net.minecraft.network.protocol.Packet<?> p5) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.sendParticles"); return false; }
    public net.minecraft.world.entity.Entity getEntity(int p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getEntity"); return null; }
    public net.minecraft.world.entity.Entity getEntityOrPart(int p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getEntityOrPart"); return null; }
    public com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> findClosestBiome3d(java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> p0, net.minecraft.core.BlockPos p1, int p2, int p3, int p4) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.findClosestBiome3d"); return null; }
    public boolean noSave() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.noSave"); return false; }
    public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getMapData"); return null; }
    public void setMapData(net.minecraft.world.level.saveddata.maps.MapId p0, net.minecraft.world.level.saveddata.maps.MapItemSavedData p1) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.setMapData"); }
    public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.setRespawnData"); }
    public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getRespawnData"); return new net.minecraft.world.level.storage.LevelData.RespawnData((net.minecraft.core.GlobalPos) null, (float) 0.0F, (float) 0.0F); }
    public it.unimi.dsi.fastutil.longs.LongSet getForceLoadedChunks() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getForceLoadedChunks"); return null; }
    public void updatePOIOnBlockStateChange(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.world.level.block.state.BlockState p2) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.updatePOIOnBlockStateChange"); }
    public net.minecraft.world.entity.ai.village.poi.PoiManager getPoiManager() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getPoiManager"); return null; }
    public boolean isCloseToVillage(net.minecraft.core.BlockPos p0, int p1) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.isCloseToVillage"); return false; }
    public java.lang.Iterable<net.minecraft.world.entity.Entity> getAllEntities() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getAllEntities"); return null; }
    public boolean isFlat() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.isFlat"); return false; }
    public long getSeed() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getSeed"); return 0L; }
    public net.minecraft.world.level.saveddata.WeatherData getWeatherData() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getWeatherData"); return null; }
    public net.minecraft.server.level.ServerLevel getLevel() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getLevel"); return this; }
    public void addWorldGenChunkEntities(java.util.stream.Stream<net.minecraft.world.entity.Entity> p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.addWorldGenChunkEntities"); }
    public void startTickingChunk(net.minecraft.world.level.chunk.LevelChunk p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.startTickingChunk"); }
    public void close() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.close"); }
    public boolean areEntitiesLoaded(long p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.areEntitiesLoaded"); return false; }
    public boolean isPositionEntityTicking(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.isPositionEntityTicking"); return false; }
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.enabledFeatures"); return null; }
    public net.minecraft.world.level.gamerules.GameRules getGameRules() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getGameRules"); return null; }
    public int getSeaLevel() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getSeaLevel"); return 0; }
    public class EntityCallbacks implements net.minecraft.world.level.entity.LevelCallback<net.minecraft.world.entity.Entity> {
        public void onCreated(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onCreated"); }
        public void onDestroyed(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onDestroyed"); }
        public void onTickingStart(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onTickingStart"); }
        public void onTickingEnd(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onTickingEnd"); }
        public void onTrackingStart(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onTrackingStart"); }
        public void onTrackingEnd(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onTrackingEnd"); }
        public void onSectionChange(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.EntityCallbacks.onSectionChange"); }
        protected EntityCallbacks() {  }
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
