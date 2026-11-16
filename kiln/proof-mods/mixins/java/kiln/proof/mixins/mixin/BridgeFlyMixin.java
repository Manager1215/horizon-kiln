package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A flight mod's way: the flying speed its own, at Player.getFlyingSpeed's head, once the test zooms (D-105). */
@Mixin(Player.class)
public abstract class BridgeFlyMixin {
    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void kiln$flies(CallbackInfoReturnable<Float> cir) {
        if (Moments.zoomed) {
            cir.setReturnValue(0.2F);
        }
    }
}
