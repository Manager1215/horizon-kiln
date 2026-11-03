package net.minecraft.server.level;

import java.util.HashMap;
import java.util.Map;
import kiln.Server;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Kiln's stand-in for {@code ServerLevel}: a dimension where the game's rules run, as Horizon's integrated server
 * said it was when it called the mod. What the mod's code does to it, the server then does.
 */
public class ServerLevel extends Level {
    private final SavedDataStorage storage;
    /** The blocks this call read or set: asked of the game once each. */
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();

    /** Kiln's own: a level of the world whose data is in {@code storage}. */
    public ServerLevel(String dimension, long gameTime, SavedDataStorage storage) {
        kiln$update(dimension, gameTime);
        this.storage = storage;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** The block at {@code pos}, as the game has it now: Kiln asks while the game waits for the mod's answer. */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.computeIfAbsent(pos.immutable(), p -> BlockState.kiln$parse(
                Server.ask("block", Integer.toString(p.getX()), Integer.toString(p.getY()), Integer.toString(p.getZ()))));
    }

    /** The game sets it once the mod's code is done ({@code block <x> <y> <z> <flags> <state>}). */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        BlockState before = blocks.get(pos);
        if (before == state) {
            return false;
        }
        blocks.put(pos.immutable(), state);
        Server.effect("block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + flags + " " + state.kiln$text());
        return true;
    }

    /** The game schedules it once the mod's code is done ({@code schedule <x> <y> <z> <block> <delay>}). */
    @Override
    public void kiln$scheduleTick(BlockPos pos, Block block, int delay) {
        Server.effect("schedule " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block.kiln$id() + " " + delay);
    }

    @Override
    public void kiln$drop(BlockPos pos, ItemStack stack) {
        Server.effect("drop " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + stack.getItem().kiln$id() + " "
                + stack.getCount());
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
        Server.effect("sound " + sound.location() + " " + source.ordinal() + " " + x + " " + y + " " + z + " " + volume
                + " " + pitch);
    }
}
