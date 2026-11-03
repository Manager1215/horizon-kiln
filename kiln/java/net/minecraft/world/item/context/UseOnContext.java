package net.minecraft.world.item.context;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Kiln's stand-in for {@code UseOnContext}: an item used on a block. */
public class UseOnContext {
    private final Player player;
    private final InteractionHand hand;
    private final BlockHitResult hit;
    private final Level level;
    private final ItemStack stack;
    /** The user's rotation: yaw and pitch, in degrees. */
    protected final float yaw;
    protected final float pitch;
    protected final boolean sneaking;

    public UseOnContext(Player player, InteractionHand hand, BlockHitResult hit) {
        this(player.level(), player, hand, player.getItemInHand(hand), hit, 0, 0, false);
    }

    /** Kiln's own: a context as the game described it, its user perhaps not a player (null). */
    public UseOnContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit, float yaw,
            float pitch, boolean sneaking) {
        this.level = level;
        this.player = player;
        this.hand = hand;
        this.stack = stack;
        this.hit = hit;
        this.yaw = yaw;
        this.pitch = pitch;
        this.sneaking = sneaking;
    }

    public BlockPos getClickedPos() {
        return hit.getBlockPos();
    }

    public Direction getClickedFace() {
        return hit.getDirection();
    }

    public Vec3 getClickLocation() {
        return hit.getLocation();
    }

    public boolean isInside() {
        return hit.isInside();
    }

    public ItemStack getItemInHand() {
        return stack;
    }

    public Player getPlayer() {
        return player;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public Level getLevel() {
        return level;
    }

    /** The horizontal direction the user faces, from their yaw (south at 0, west at 90). */
    public Direction getHorizontalDirection() {
        int quarter = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
        return switch (quarter) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    public boolean isSecondaryUseActive() {
        return sneaking;
    }

    public float getRotation() {
        return yaw;
    }
}
