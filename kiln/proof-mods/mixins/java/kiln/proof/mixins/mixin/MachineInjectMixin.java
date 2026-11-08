package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import kiln.proof.mixins.Machine;
import kiln.proof.mixins.ProofMixins;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * {@code @Inject} inside a method: at a call (its ordinal, shifted after), at a field read, after an assignment with
 * the method's locals captured, in a slice, with MixinExtras' {@code @Local}; and a static initializer.
 */
@Mixin(Machine.class)
public abstract class MachineInjectMixin {
    @Shadow
    public static int made;

    static {
        made += 10;
    }

    @Shadow
    public abstract void log(String line);

    @Inject(method = "twoLines", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V", ordinal = 1))
    private void kiln$between(CallbackInfo ci) {
        log("between");
    }

    @Inject(method = "twoLines", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void kiln$afterFirst(CallbackInfo ci) {
        log("after a");
    }

    @Inject(method = "power", at = @At(value = "FIELD", target = "Lkiln/proof/mixins/Machine;power:I",
            opcode = Opcodes.GETFIELD))
    private void kiln$read(CallbackInfoReturnable<Integer> cir) {
        ProofMixins.fieldReads++;
    }

    @Inject(method = "assigned", at = @At(value = "INVOKE_ASSIGN", target = "Lkiln/proof/mixins/Machine;step(I)I"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void kiln$captured(int v, CallbackInfoReturnable<Integer> cir, int r) {
        ProofMixins.captured = r;
    }

    @Inject(method = "sliced", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lkiln/proof/mixins/Machine;mark()V")))
    private void kiln$inSlice(CallbackInfo ci) {
        log("sliced");
    }

    @Inject(method = "localed", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V"))
    private void kiln$local(int x, CallbackInfoReturnable<Integer> cir, @Local(ordinal = 1) int y) {
        ProofMixins.local = y;
    }
}
