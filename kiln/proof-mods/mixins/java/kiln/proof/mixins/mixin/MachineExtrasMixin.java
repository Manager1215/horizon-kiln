package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import kiln.proof.mixins.Machine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MixinExtras inside a method: a call and a {@code new} wrapped, a value changed, a call made only on a condition,
 * a value shared between two handlers. */
@Mixin(Machine.class)
public abstract class MachineExtrasMixin {
    @WrapOperation(method = "wrapped", at = @At(value = "INVOKE", target = "Lkiln/proof/mixins/Machine;step(I)I"))
    private int kiln$wrap(Machine self, int v, Operation<Integer> original) {
        return original.call(self, v) * 10;
    }

    @WrapOperation(method = "built", at = @At(value = "NEW", target = "java/lang/StringBuilder"))
    private StringBuilder kiln$made(String start, Operation<StringBuilder> original) {
        return original.call(start + "!");
    }

    @ModifyExpressionValue(method = "expression", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;step(I)I"))
    private int kiln$doubled(int original) {
        return original * 2;
    }

    @WrapWithCondition(method = "conditional", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V"))
    private boolean kiln$only(Machine self, String line) {
        return !line.equals("blocked");
    }

    @Inject(method = "shared", at = @At("HEAD"))
    private void kiln$share(CallbackInfoReturnable<String> cir, @Share("n") LocalIntRef n) {
        n.set(5);
    }

    @ModifyReturnValue(method = "shared", at = @At("RETURN"))
    private String kiln$shared(String original, @Share("n") LocalIntRef n) {
        return original + n.get();
    }
}
