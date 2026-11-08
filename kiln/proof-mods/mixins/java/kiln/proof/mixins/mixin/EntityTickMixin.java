package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An entity's tick, the bottom of the player's. */
@Mixin(Entity.class)
public abstract class EntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        Moments.saw("Entity.tick HEAD");
    }
}
