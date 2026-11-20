package net.minecraft.world.item;

import net.minecraft.world.level.material.Fluid;

/**
 * Kiln's stand-in for {@code BucketItem}: an item that holds a block of one fluid. Horizon empties and fills the game's
 * buckets in the world; Kiln knows which fluid each holds, so a mod's tank takes it (Fabric's {@code FluidStorage}).
 */
public class BucketItem extends Item {
    private final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    /**
     * The game's use, as far as the block it aims at: Horizon fills and empties the buckets, so past the aim nothing
     * happens here. Architectury's mixin hooks the aim's first {@code getType} with the stack and the aim as its
     * locals, in this order (D-218).
     */
    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        net.minecraft.world.phys.BlockHitResult hit = getPlayerPOVHitResult(level, player,
                content == net.minecraft.world.level.material.Fluids.EMPTY
                        ? net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY
                        : net.minecraft.world.level.ClipContext.Fluid.NONE);
        if (hit == null || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        return super.use(level, player, hand);
    }

    /** Kiln's own: the fluid it holds (Fabric reads vanilla's private field). */
    public Fluid kiln$content() {
        return content;
    }
}
