package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Kiln's stand-in for {@code BlockHitResult}: the block a player clicked, and its face. */
public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean inside;

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        super(location);
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
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
