package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kiln's stand-in for {@code LevelChunk}: a 16 by 16 column of a level. On the client it is one the window's game
 * has; its blocks are the level's ({@link Level#getBlockState}). An empty one stands for a chunk the game hasn't.
 */
public class LevelChunk {
    private final Level level;
    private final ChunkPos pos;
    private final boolean empty;

    public LevelChunk(Level level, ChunkPos pos) {
        this(level, pos, false);
    }

    private LevelChunk(Level level, ChunkPos pos, boolean empty) {
        this.level = level;
        this.pos = pos;
        this.empty = empty;
    }

    /** Kiln's own: the stand-in for a chunk the game hasn't (vanilla's {@code EmptyLevelChunk}). */
    public static LevelChunk kiln$empty(Level level, ChunkPos pos) {
        return new LevelChunk(level, pos, true);
    }

    public ChunkPos getPos() {
        return pos;
    }

    public Level getLevel() {
        return level;
    }

    /** No blocks: a chunk the game hasn't. */
    public boolean isEmpty() {
        return empty;
    }

    public BlockState getBlockState(BlockPos at) {
        return level.getBlockState(at);
    }

    @Override
    public String toString() {
        return "LevelChunk[" + pos.x() + ", " + pos.z() + "]";
    }
}
