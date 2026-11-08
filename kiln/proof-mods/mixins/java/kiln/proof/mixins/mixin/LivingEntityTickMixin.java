package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A living entity's tick, as mods inject into it most after the player's. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        Moments.saw("LivingEntity.tick HEAD");
    }
}
