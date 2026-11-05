package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for {@code BucketPickup}: a block a bucket can take up (a liquid's source). */
public interface BucketPickup {
    ItemStack pickupBlock(LivingEntity entity, LevelAccessor level, BlockPos pos, BlockState state);

    Optional<SoundEvent> getPickupSound();
}
