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
public abstract class Level implements LevelAccessor {
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

    /** The chunk at chunk x and z: a client level knows the window's game's; elsewhere none, said once in Kiln's log. */
    public net.minecraft.world.level.chunk.LevelChunk getChunk(int x, int z) {
        kiln.Missing.called("the chunks of a server's level");
        return null;
    }

    public boolean isLoaded(BlockPos pos) {
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

    /** What this dimension is like, by its id. */
    public DimensionType dimensionType() {
        return DimensionType.kiln$of(dimension.identifier().toString());
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
    public net.minecraft.core.RegistryAccess registryAccess() { kiln.Missing.called("net.minecraft.world.level.Level.registryAccess"); return null; }
    public net.minecraft.world.damagesource.DamageSources damageSources() { kiln.Missing.called("net.minecraft.world.level.Level.damageSources"); return null; }
    public static enum ExplosionInteraction implements net.minecraft.util.StringRepresentable {
        NONE, BLOCK, MOB, TNT, TRIGGER;
        public java.lang.String getSerializedName() { kiln.Missing.called("net.minecraft.world.level.Level.ExplosionInteraction.getSerializedName"); return null; }
    }
    // End of what tools/kiln-stubs.py generated.
}
