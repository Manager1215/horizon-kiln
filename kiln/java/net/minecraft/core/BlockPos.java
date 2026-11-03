package net.minecraft.core;

/** Kiln's stand-in for {@code BlockPos}: a block's position in a level. */
public class BlockPos extends Vec3i {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public BlockPos offset(int x, int y, int z) {
        return new BlockPos(getX() + x, getY() + y, getZ() + z);
    }

    public BlockPos above() {
        return offset(0, 1, 0);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    public BlockPos relative(Direction direction) {
        return relative(direction, 1);
    }

    public BlockPos relative(Direction direction, int steps) {
        return offset(direction.getStepX() * steps, direction.getStepY() * steps, direction.getStepZ() * steps);
    }

    public BlockPos north() {
        return relative(Direction.NORTH);
    }

    public BlockPos south() {
        return relative(Direction.SOUTH);
    }

    public BlockPos west() {
        return relative(Direction.WEST);
    }

    public BlockPos east() {
        return relative(Direction.EAST);
    }

    public BlockPos immutable() {
        return this;
    }
}
