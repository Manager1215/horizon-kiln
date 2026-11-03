package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

    /** The block at {@code pos}; Kiln's client side knows none yet: air. */
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
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
}
