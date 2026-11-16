package kiln.proof.mixins.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob's server tick, at Horizon's {@code entity_tick_pre} in a batch (Kiln's Mixin bridge, D-145): each mob ticked
 * plays a sound at x = its id, so the test sees which ones ticked.
 */
@Mixin(LivingEntity.class)
public abstract class BridgeMobTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$mobTicks(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) && self.level() instanceof ServerLevel level) {
            level.playSound(null, self.getId(), 64.0, 0.0, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }
}
