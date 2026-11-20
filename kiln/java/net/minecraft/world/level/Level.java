package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FluidState;

/** Kiln's stand-in for {@code Level}: a dimension of the world, as the game last told Kiln about it. */
public abstract class Level implements LevelAccessor, net.fabricmc.fabric.api.attachment.v1.AttachmentTarget {
    protected Level() {}

    /** The game's constructor, as a mod's own level class calls it: Kiln's levels keep what they know themselves. */
    protected Level(net.minecraft.world.level.storage.WritableLevelData levelData, ResourceKey<Level> dimension,
            net.minecraft.core.RegistryAccess registryAccess, Holder<DimensionType> dimensionType, boolean isClientSide,
            boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {}

    public static final ResourceKey<Level> OVERWORLD = dimension("minecraft:overworld");
    public static final ResourceKey<Level> NETHER = dimension("minecraft:the_nether");
    public static final ResourceKey<Level> END = dimension("minecraft:the_end");
    private ResourceKey<Level> dimension = OVERWORLD;
    private long gameTime;
    private final RandomSource random = RandomSource.create();

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** Ticks since the world was made (vanilla's {@code LevelAccessor.getGameTime}). */
    /**
     * The level's day clock: the window's level has the game's ({@code ClientLevel}), the server's its type's clock
     * ({@code ServerLevel}, D-210); another, its game time.
     */
    public long getDefaultClockTime() {
        return getGameTime();
    }

    public long getGameTime() {
        return gameTime;
    }

    /** True for the client's view of the world; false where the game's rules run (the server). */
    public abstract boolean isClientSide();

    public void playSound(Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        playSound(except, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, source, volume, pitch);
    }

    public void playSound(Entity except, double x, double y, double z, SoundEvent sound, SoundSource source) {
        playSound(except, x, y, z, sound, source, 1.0F, 1.0F);
    }

    public void playSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch) {
        playSound(except, x, y, z, sound.value(), source, volume, pitch);
    }

    public void playSound(Entity except, Entity at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        playSound(except, at.getX(), at.getY(), at.getZ(), sound, source, volume, pitch);
    }

    public void playSound(Entity except, Entity at, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        playSound(except, at, sound.value(), source, volume, pitch);
    }

    /** A sound for the players near {@code x y z}; on a client, Kiln plays nothing (yet). */
    public void playSound(Entity except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch) {}

    /** A sound only this computer's player hears; where the game's rules run (a server), none, as in vanilla. */
    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch, boolean distanceDelay) {}

    /** A particle only this computer's player sees; where the game's rules run (a server), none, as in vanilla. */
    public void addParticle(net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z,
            double dx, double dy, double dz) {}

    /** The block at {@code pos}; Kiln's client side knows none yet: air. */
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    /** The fluid in the block at {@code pos}: the game's water and lava hold theirs. */
    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    /** The block entity at {@code pos}; Kiln's client side knows none. */
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity entity = getBlockEntity(pos);
        return entity != null && entity.getType() == type ? Optional.of((T) entity) : Optional.empty();
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return setBlock(pos, state, flags, 512);
    }

    /** Sets a block where the game's rules run; a client's view changes nothing. */
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        return false;
    }

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
        return setBlock(pos, state, Block.UPDATE_ALL);
    }

    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    public boolean destroyBlock(BlockPos pos, boolean drop) {
        return destroyBlock(pos, drop, null);
    }

    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker) {
        return destroyBlock(pos, drop, breaker, 512);
    }

    /**
     * Breaks the block at {@code pos} as the game breaks one (its sound and particles, its drops when {@code drop}):
     * where the game's rules run; a client's view changes nothing.
     */
    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth) {
        return false;
    }

    /** The chunk at chunk x and z: a client level knows the window's game's; elsewhere none, said once in Kiln's log. */
    public net.minecraft.world.level.chunk.LevelChunk getChunk(int x, int z) {
        kiln.Missing.called("the chunks of a server's level");
        return null;
    }

    public boolean isLoaded(BlockPos pos) {
        return hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Whether the game has the chunk at chunk x and z: a client's level takes it as so; a server's asks. */
    public boolean hasChunk(int x, int z) {
        return true;
    }

    public boolean hasChunkAt(BlockPos pos) {
        return isLoaded(pos);
    }

    /** Whether the game has every chunk from {@code from} to {@code to} (RebornCore's multiblocks ask, D-161). */
    public boolean hasChunksAt(BlockPos from, BlockPos to) {
        for (int x = from.getX() >> 4; x <= to.getX() >> 4; x++) {
            for (int z = from.getZ() >> 4; z <= to.getZ() >> 4; z++) {
                if (!hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public RandomSource getRandom() {
        return random;
    }

    /** A client schedules nothing. */
    @Override
    public void kiln$scheduleTick(BlockPos pos, Block block, int delay) {}

    /** Kiln's own: {@code stack} dropped at {@code pos} ({@code Block.popResource}); nothing on a client. */
    public void kiln$drop(BlockPos pos, ItemStack stack) {}

    /** Kiln's own: what the block {@code state} drops, dropped at {@code pos} ({@code Block.dropResources}). */
    public void kiln$dropResources(BlockPos pos, BlockState state) {}

    /** Kiln's own: what the game said this tick. */
    public void kiln$update(String dimension, long gameTime) {
        if (!this.dimension.identifier().toString().equals(dimension)) {
            this.dimension = dimension(dimension);
        }
        this.gameTime = gameTime;
    }

    private static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }

    /** The server, on the server's side; null on a client's. */
    public net.minecraft.server.MinecraftServer getServer() {
        return null;
    }

    public int getMinY() {
        return dimensionType().minY();
    }

    public int getHeight() {
        return dimensionType().height();
    }

    public int getMinSectionY() {
        return getMinY() >> 4;
    }

    public int getMaxY() {
        return getMinY() + getHeight() - 1;
    }

    /** The index of the 16-high section that holds height {@code y}, counted from the lowest one. */
    public int getSectionIndex(int y) {
        return (y >> 4) - (getMinY() >> 4);
    }

    /** The light of a layer at {@code pos}: Kiln keeps no light, so the sky is full where it is seen, the rest dark. */
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return layer == LightLayer.SKY && canSeeSky(pos) ? 15 : 0;
    }

    /** Redstone power from the neighbours: Kiln's levels carry none. */
    public boolean hasNeighborSignal(BlockPos pos) {
        return false;
    }

    /** What this dimension is like, by its id. */
    public DimensionType dimensionType() {
        return DimensionType.kiln$of(dimension.identifier().toString());
    }

    /** Its type as the game's entry ({@code minecraft:overworld}): the type and its key. */
    public Holder<DimensionType> dimensionTypeRegistration() {
        DimensionType type = dimensionType();
        return Holder.Reference.kiln$of(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(type.kiln$id())),
                type);
    }

    /** Whether the sky is seen from {@code pos} (full sky light there); a client's view knows no light yet. */
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    /** How much it rains, 0 to 1; a client's view knows no weather yet. */
    public float getRainLevel(float partialTick) {
        return 0;
    }

    /** How much it thunders, 0 to 1 (and no more than it rains). */
    public float getThunderLevel(float partialTick) {
        return 0;
    }

    public boolean isRaining() {
        return getRainLevel(1.0F) > 0.2F;
    }

    public boolean isThundering() {
        return dimensionType().hasSkyLight() && !dimensionType().hasCeiling() && getThunderLevel(1.0F) > 0.9F;
    }

    /** The dimension's environment (the sun's angle): a client's view knows none of it yet. */
    public EnvironmentAttributeSystem environmentAttributes() {
        return new EnvironmentAttributeSystem((attribute, pos) -> null);
    }
    /** Kiln's registries: one for every world ({@code RegistryAccess.KILN}). */
    public net.minecraft.core.RegistryAccess registryAccess() {
        return net.minecraft.core.RegistryAccess.KILN;
    }

    /**
     * The players of this level (the game's {@code EntityGetter}): Kiln's server and client levels give theirs. They
     * are the only entities Kiln's levels know, so the entity queries below answer with them.
     */
    public abstract java.util.List<? extends net.minecraft.world.entity.player.Player> players();

    /** The entities in {@code box} that {@code filter} takes, but {@code except}: of Kiln's, the players. */
    public java.util.List<Entity> getEntities(Entity except, net.minecraft.world.phys.AABB box,
            java.util.function.Predicate<? super Entity> filter) {
        java.util.List<Entity> found = new java.util.ArrayList<>();
        for (Entity e : players()) {
            if (e != except && e.getBoundingBox().intersects(box) && (filter == null || filter.test(e))) found.add(e);
        }
        return found;
    }

    /** The entities in {@code box} that {@code test} casts and {@code filter} takes: of Kiln's, the players. */
    public <T extends Entity> java.util.List<T> getEntities(
            net.minecraft.world.level.entity.EntityTypeTest<Entity, T> test, net.minecraft.world.phys.AABB box,
            java.util.function.Predicate<? super T> filter) {
        java.util.List<T> found = new java.util.ArrayList<>();
        for (Entity e : players()) {
            T t = e.getBoundingBox().intersects(box) ? test.tryCast(e) : null;
            if (t != null && (filter == null || filter.test(t))) found.add(t);
        }
        return found;
    }

    /** The entities of {@code type} in {@code box} that {@code filter} takes: of Kiln's, the players. */
    public <T extends Entity> java.util.List<T> getEntitiesOfClass(Class<T> type, net.minecraft.world.phys.AABB box,
            java.util.function.Predicate<? super T> filter) {
        java.util.List<T> found = new java.util.ArrayList<>();
        for (Entity e : players()) {
            if (type.isInstance(e) && e.getBoundingBox().intersects(box) && (filter == null || filter.test(type.cast(e)))) {
                found.add(type.cast(e));
            }
        }
        return found;
    }

    public <T extends Entity> java.util.List<T> getEntitiesOfClass(Class<T> type, net.minecraft.world.phys.AABB box) {
        return getEntitiesOfClass(type, box, e -> !e.isSpectator());
    }

    public java.util.List<Entity> getEntities(Entity except, net.minecraft.world.phys.AABB box) {
        return getEntities(except, box, e -> !e.isSpectator());
    }

    /** The player nearest {@code x y z} within {@code distance} ({@code -1}: any) that {@code filter} takes. */
    public net.minecraft.world.entity.player.Player getNearestPlayer(double x, double y, double z, double distance,
            java.util.function.Predicate<Entity> filter) {
        net.minecraft.world.entity.player.Player nearest = null;
        double best = -1;
        for (net.minecraft.world.entity.player.Player p : players()) {
            if (filter != null && !filter.test(p)) continue;
            double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z, d = dx * dx + dy * dy + dz * dz;
            if ((distance < 0 || d < distance * distance) && (best < 0 || d < best)) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    public net.minecraft.world.entity.player.Player getNearestPlayer(Entity entity, double distance) {
        return getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), distance, false);
    }

    /** As the game's: with {@code creativeOnlyExcluded}, players in creative or spectator mode don't count. */
    public net.minecraft.world.entity.player.Player getNearestPlayer(double x, double y, double z, double distance,
            boolean creativeOnlyExcluded) {
        return getNearestPlayer(x, y, z, distance, e -> !e.isSpectator() && (!creativeOnlyExcluded
                || !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative())));
    }

    /** Whether a living player who isn't a spectator is within {@code distance} of {@code x y z}. */
    public boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        return getNearestPlayer(x, y, z, distance, e -> e.isAlive() && !e.isSpectator()) != null;
    }

    public net.minecraft.world.entity.player.Player getPlayerByUUID(java.util.UUID uuid) {
        for (net.minecraft.world.entity.player.Player p : players()) {
            if (p.getUUID().equals(uuid)) return p;
        }
        return null;
    }

    /** The blocks a moving entity collides with near chunk {@code x z}: Kiln's level reads them itself. */
    public BlockGetter getChunkForCollisions(int x, int z) {
        return this;
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void sendBlockUpdated(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.world.level.block.state.BlockState p2, int p3) { kiln.Missing.called("net.minecraft.world.level.Level.sendBlockUpdated"); }
    public void updateNeighborsAt(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1, net.minecraft.world.level.redstone.Orientation p2) { kiln.Missing.called("net.minecraft.world.level.Level.updateNeighborsAt"); }
    public void neighborChanged(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1, net.minecraft.world.level.redstone.Orientation p2) { kiln.Missing.called("net.minecraft.world.level.Level.neighborChanged"); }
    public void explode(net.minecraft.world.entity.Entity p0, double p1, double p2, double p3, float p4, net.minecraft.world.level.Level.ExplosionInteraction p5) { kiln.Missing.called("net.minecraft.world.level.Level.explode"); }
    public void removeBlockEntity(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.removeBlockEntity"); }
    public void blockEntityChanged(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.blockEntityChanged"); }
    public boolean mayInteract(net.minecraft.world.entity.Entity p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.Level.mayInteract"); return false; }
    public void destroyBlockProgress(int p0, net.minecraft.core.BlockPos p1, int p2) { kiln.Missing.called("net.minecraft.world.level.Level.destroyBlockProgress"); }
    public void updateNeighbourForOutputSignal(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1) { kiln.Missing.called("net.minecraft.world.level.Level.updateNeighbourForOutputSignal"); }

    public net.minecraft.world.damagesource.DamageSources damageSources() { kiln.Missing.called("net.minecraft.world.level.Level.damageSources"); return null; }
    public static enum ExplosionInteraction implements net.minecraft.util.StringRepresentable {
        NONE, BLOCK, MOB, TNT, TRIGGER;
        public java.lang.String getSerializedName() { kiln.Missing.called("net.minecraft.world.level.Level.ExplosionInteraction.getSerializedName"); return null; }
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final com.mojang.serialization.Codec<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> RESOURCE_KEY_CODEC = kiln.Constants.codec("net.minecraft.world.level.Level.RESOURCE_KEY_CODEC");
    public java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> blockEntityTickers;
    private java.lang.Thread thread;
    protected float rainLevel;
    protected float thunderLevel;
    public net.minecraft.util.RandomSource soundSeedGenerator;
    public boolean isInWorldBounds(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.isInWorldBounds"); return false; }
    public boolean isInValidBounds(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.isInValidBounds"); return false; }
    public static boolean isInSpawnableBounds(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.isInSpawnableBounds"); return false; }
    public net.minecraft.world.level.chunk.LevelChunk getChunkAt(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.getChunkAt"); return null; }
    public net.minecraft.world.level.chunk.ChunkAccess getChunk(int p0, int p1, net.minecraft.world.level.chunk.status.ChunkStatus p2, boolean p3) { kiln.Missing.called("net.minecraft.world.level.Level.getChunk"); return null; }
    public void addDestroyBlockEffect(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.world.level.Level.addDestroyBlockEffect"); }
    public void setBlocksDirty(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.world.level.block.state.BlockState p2) { kiln.Missing.called("net.minecraft.world.level.Level.setBlocksDirty"); }
    public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types p0, int p1, int p2) { kiln.Missing.called("net.minecraft.world.level.Level.getHeight"); return 0; }
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { kiln.Missing.called("net.minecraft.world.level.Level.getLightEngine"); return null; }
    public boolean isBrightOutside() { kiln.Missing.called("net.minecraft.world.level.Level.isBrightOutside"); return false; }
    public boolean isDarkOutside() { kiln.Missing.called("net.minecraft.world.level.Level.isDarkOutside"); return false; }
    public void playLocalSound(net.minecraft.core.BlockPos p0, net.minecraft.sounds.SoundEvent p1, net.minecraft.sounds.SoundSource p2, float p3, float p4, boolean p5) { kiln.Missing.called("net.minecraft.world.level.Level.playLocalSound"); }
    public void playLocalSound(net.minecraft.world.entity.Entity p0, net.minecraft.sounds.SoundEvent p1, net.minecraft.sounds.SoundSource p2, float p3, float p4) { kiln.Missing.called("net.minecraft.world.level.Level.playLocalSound"); }
    public void playPlayerSound(net.minecraft.sounds.SoundEvent p0, net.minecraft.sounds.SoundSource p1, float p2, float p3) { kiln.Missing.called("net.minecraft.world.level.Level.playPlayerSound"); }
    public void addParticle(net.minecraft.core.particles.ParticleOptions p0, boolean p1, boolean p2, double p3, double p4, double p5, double p6, double p7, double p8) { kiln.Missing.called("net.minecraft.world.level.Level.addParticle"); }
    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions p0, double p1, double p2, double p3, double p4, double p5, double p6) { kiln.Missing.called("net.minecraft.world.level.Level.addAlwaysVisibleParticle"); }
    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions p0, boolean p1, double p2, double p3, double p4, double p5, double p6, double p7) { kiln.Missing.called("net.minecraft.world.level.Level.addAlwaysVisibleParticle"); }
    public <T extends net.minecraft.world.entity.Entity> void guardEntityTick(java.util.function.Consumer<T> p0, T p1) { kiln.Missing.called("net.minecraft.world.level.Level.guardEntityTick"); }
    public boolean shouldTickBlocksAt(long p0) { kiln.Missing.called("net.minecraft.world.level.Level.shouldTickBlocksAt"); return false; }
    public void explode(net.minecraft.world.entity.Entity p0, net.minecraft.world.damagesource.DamageSource p1, net.minecraft.world.level.ExplosionDamageCalculator p2, double p3, double p4, double p5, float p6, boolean p7, net.minecraft.world.level.Level.ExplosionInteraction p8) { kiln.Missing.called("net.minecraft.world.level.Level.explode"); }
    public java.lang.String gatherChunkSourceStats() { kiln.Missing.called("net.minecraft.world.level.Level.gatherChunkSourceStats"); return null; }
    public void setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity p0) { kiln.Missing.called("net.minecraft.world.level.Level.setBlockEntity"); }
    public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData p0) { kiln.Missing.called("net.minecraft.world.level.Level.setRespawnData"); }
    public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() { kiln.Missing.called("net.minecraft.world.level.Level.getRespawnData"); return new net.minecraft.world.level.storage.LevelData.RespawnData((net.minecraft.core.GlobalPos) null, (float) 0.0F, (float) 0.0F); }
    public <T extends net.minecraft.world.entity.Entity> void getEntities(net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, T> p0, net.minecraft.world.phys.AABB p1, java.util.function.Predicate<? super T> p2, java.util.List<? super T> p3) { kiln.Missing.called("net.minecraft.world.level.Level.getEntities"); }
    public <T extends net.minecraft.world.entity.Entity> void getEntities(net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, T> p0, net.minecraft.world.phys.AABB p1, java.util.function.Predicate<? super T> p2, java.util.List<? super T> p3, int p4) { kiln.Missing.called("net.minecraft.world.level.Level.getEntities"); }
    public net.minecraft.world.entity.Entity getEntity(int p0) { kiln.Missing.called("net.minecraft.world.level.Level.getEntity"); return null; }
    public net.minecraft.world.entity.Entity getEntity(java.util.UUID p0) { kiln.Missing.called("net.minecraft.world.level.Level.getEntity"); return null; }
    public java.util.Collection<net.minecraft.world.entity.boss.enderdragon.EnderDragonPart> dragonParts() { kiln.Missing.called("net.minecraft.world.level.Level.dragonParts"); return null; }
    public long getOverworldClockTime() { kiln.Missing.called("net.minecraft.world.level.Level.getOverworldClockTime"); return 0L; }
    public void broadcastEntityEvent(net.minecraft.world.entity.Entity p0, byte p1) { kiln.Missing.called("net.minecraft.world.level.Level.broadcastEntityEvent"); }
    public void blockEvent(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1, int p2, int p3) { kiln.Missing.called("net.minecraft.world.level.Level.blockEvent"); }
    public net.minecraft.world.level.storage.LevelData getLevelData() { kiln.Missing.called("net.minecraft.world.level.Level.getLevelData"); return null; }
    public net.minecraft.world.TickRateManager tickRateManager() { kiln.Missing.called("net.minecraft.world.level.Level.tickRateManager"); return null; }
    public void setThunderLevel(float p0) { kiln.Missing.called("net.minecraft.world.level.Level.setThunderLevel"); }
    public void setRainLevel(float p0) { kiln.Missing.called("net.minecraft.world.level.Level.setRainLevel"); }
    public boolean isRainingAt(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.Level.isRainingAt"); return false; }
    public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId p0) { kiln.Missing.called("net.minecraft.world.level.Level.getMapData"); return null; }
    public net.minecraft.world.scores.Scoreboard getScoreboard() { kiln.Missing.called("net.minecraft.world.level.Level.getScoreboard"); return null; }
    public int getSkyDarken() { kiln.Missing.called("net.minecraft.world.level.Level.getSkyDarken"); return 0; }
    public boolean isStateAtPosition(net.minecraft.core.BlockPos p0, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> p1) { kiln.Missing.called("net.minecraft.world.level.Level.isStateAtPosition"); return false; }
    public boolean isFluidAtPosition(net.minecraft.core.BlockPos p0, java.util.function.Predicate<net.minecraft.world.level.material.FluidState> p1) { kiln.Missing.called("net.minecraft.world.level.Level.isFluidAtPosition"); return false; }
    public net.minecraft.core.BlockPos getBlockRandomPos(int p0, int p1, int p2, int p3) { kiln.Missing.called("net.minecraft.world.level.Level.getBlockRandomPos"); return null; }
    public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { kiln.Missing.called("net.minecraft.world.level.Level.getBiomeManager"); return null; }
    public boolean isDebug() { kiln.Missing.called("net.minecraft.world.level.Level.isDebug"); return false; }
    public long nextSubTickCount() { kiln.Missing.called("net.minecraft.world.level.Level.nextSubTickCount"); return 0L; }
    public net.minecraft.world.clock.ClockManager clockManager() { kiln.Missing.called("net.minecraft.world.level.Level.clockManager"); return null; }
    public net.minecraft.world.level.chunk.PalettedContainerFactory palettedContainerFactory() { kiln.Missing.called("net.minecraft.world.level.Level.palettedContainerFactory"); return new net.minecraft.world.level.chunk.PalettedContainerFactory((net.minecraft.world.level.chunk.Strategy) null, (net.minecraft.world.level.block.state.BlockState) null, kiln.Constants.codec("net.minecraft.world.level.chunk.PalettedContainerFactory.blockStatesContainerCodec"), (net.minecraft.world.level.chunk.Strategy) null, (net.minecraft.core.Holder) null, kiln.Constants.codec("net.minecraft.world.level.chunk.PalettedContainerFactory.biomeContainerCodec")); }
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
