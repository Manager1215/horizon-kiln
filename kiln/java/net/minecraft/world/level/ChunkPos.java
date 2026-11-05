package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/** Kiln's stand-in for {@code ChunkPos}: a chunk's column, 16 by 16 blocks. */
public record ChunkPos(int x, int z) {
    public static final ChunkPos ZERO = new ChunkPos(0, 0);

    public static ChunkPos containing(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static long pack(int x, int z) {
        return x & 0xFFFFFFFFL | (z & 0xFFFFFFFFL) << 32;
    }

    public static long pack(BlockPos pos) {
        return pack(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static ChunkPos unpack(long packed) {
        return new ChunkPos(getX(packed), getZ(packed));
    }

    public static int getX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int getZ(long packed) {
        return (int) (packed >>> 32 & 0xFFFFFFFFL);
    }

    public long pack() {
        return pack(x, z);
    }

    public int getMinBlockX() {
        return x << 4;
    }

    public int getMinBlockZ() {
        return z << 4;
    }

    public int getMaxBlockX() {
        return (x << 4) + 15;
    }

    public int getMaxBlockZ() {
        return (z << 4) + 15;
    }

    public int getMiddleBlockX() {
        return (x << 4) + 7;
    }

    public int getMiddleBlockZ() {
        return (z << 4) + 7;
    }

    public BlockPos getBlockAt(int dx, int y, int dz) {
        return new BlockPos(getMinBlockX() + dx, y, getMinBlockZ() + dz);
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(getMinBlockX(), 0, getMinBlockZ());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >> 4 == x && pos.getZ() >> 4 == z;
    }

    public int getChessboardDistance(ChunkPos other) {
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }

    @Override
    public String toString() {
        return "[" + x + ", " + z + "]";
    }
}
