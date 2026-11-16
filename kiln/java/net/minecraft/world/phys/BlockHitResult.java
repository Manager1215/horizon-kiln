package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Kiln's stand-in for {@code BlockHitResult}: the block a player clicked, and its face. */
public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean inside;
    private final boolean miss;

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        this(false, location, direction, blockPos, inside);
    }

    private BlockHitResult(boolean miss, Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    /** Nothing met, as a look that reached no block: the type is {@link HitResult.Type#MISS}. */
    public static BlockHitResult miss(Vec3 location, Direction direction, BlockPos blockPos) {
        return new BlockHitResult(true, location, direction, blockPos, false);
    }

    @Override
    public HitResult.Type getType() {
        return miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public BlockHitResult withDirection(Direction direction) {
        return new BlockHitResult(miss, location, direction, blockPos, inside);
    }

    public BlockHitResult withPosition(BlockPos blockPos) {
        return new BlockHitResult(miss, location, direction, blockPos, inside);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isInside() {
        return inside;
    }
}
