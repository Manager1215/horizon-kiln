package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A zoom mod's way: the view's field of view halved at Camera.calculateFov's return, once the test zooms (D-105). */
@Mixin(Camera.class)
public abstract class BridgeZoomMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void kiln$zooms(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (Moments.zoomed) {
            cir.setReturnValue(cir.getReturnValue() / 2);
        }
    }
}
