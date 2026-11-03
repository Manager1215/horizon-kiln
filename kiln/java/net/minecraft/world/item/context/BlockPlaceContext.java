package net.minecraft.world.item.context;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Kiln's stand-in for {@code BlockPlaceContext}: a block about to be placed, and how. */
public class BlockPlaceContext extends UseOnContext {
    private final BlockPos placePos;
    private final boolean replaceClicked;

    public BlockPlaceContext(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        this(player.level(), player, hand, stack, hit, 0, 0, false, hit.getBlockPos().relative(hit.getDirection()), false);
    }

    /** Kiln's own: as the game described the placement; {@code player} is null when Horizon doesn't say. */
    public BlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit,
            float yaw, float pitch, boolean sneaking, BlockPos placePos, boolean replaceClicked) {
        super(level, player, hand, stack, hit, yaw, pitch, sneaking);
        this.placePos = placePos;
        this.replaceClicked = replaceClicked;
    }

    @Override
    public BlockPos getClickedPos() {
        return placePos;
    }

    public boolean canPlace() {
        return true;
    }

    public boolean replacingClickedOnBlock() {
        return replaceClicked;
    }

    /** The directions nearest the user's look, nearest first ({@code Direction.orderedByNearest}). */
    public Direction[] getNearestLookingDirections() {
        double yawRad = Math.toRadians(-yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        Direction[] all = Direction.values().clone();
        Arrays.sort(all, Comparator.comparingDouble(d -> -(d.getStepX() * x + d.getStepY() * y + d.getStepZ() * z)));
        return all;
    }

    public Direction getNearestLookingDirection() {
        return getNearestLookingDirections()[0];
    }

    public Direction getNearestLookingVerticalDirection() {
        return pitch < 0 ? Direction.UP : Direction.DOWN;
    }
}
