package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kiln.proof.mixins.Gadget;
import kiln.proof.mixins.ProofMixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After GadgetWrapMixin: its wrapper of greet wraps that one's. Its HEAD injection goes into greet's own code, which
 * runs inside both wrappers.
 */
@Mixin(Gadget.class)
public abstract class GadgetWrapOuterMixin {
    @WrapMethod(method = "greet")
    private String kiln$framed(String who, int times, Operation<String> original) {
        return "[" + original.call(who, times) + "]";
    }

    @Inject(method = "greet", at = @At("HEAD"))
    private void kiln$greeted(String who, int times, CallbackInfoReturnable<String> cir) {
        ProofMixins.greeted.add(who);
    }
}
