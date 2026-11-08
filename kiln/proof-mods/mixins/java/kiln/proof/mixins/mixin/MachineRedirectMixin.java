package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Machine;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Mixin's other injectors: a call and a field read redirected, an argument, all of them, a local, a constant. */
@Mixin(Machine.class)
public abstract class MachineRedirectMixin {
    @Redirect(method = "redirected", at = @At(value = "INVOKE", target = "Lkiln/proof/mixins/Machine;step(I)I"))
    private int kiln$redirect(Machine self, int v) {
        return v * 100;
    }

    @Redirect(method = "charge", at = @At(value = "FIELD", target = "Lkiln/proof/mixins/Machine;power:I",
            opcode = Opcodes.GETFIELD))
    private int kiln$power(Machine self) {
        return 99;
    }

    @ModifyArg(method = "argued", at = @At(value = "INVOKE",
            target = "Lkiln/proof/mixins/Machine;log(Ljava/lang/String;)V"), index = 0)
    private String kiln$argument(String line) {
        return "argued";
    }

    @ModifyArgs(method = "paired", at = @At(value = "INVOKE", target = "Lkiln/proof/mixins/Machine;pair(II)I"))
    private void kiln$swap(Args args) {
        int a = args.get(0);
        args.set(0, args.<Integer>get(1));
        args.set(1, a);
    }

    @ModifyVariable(method = "variable", at = @At("STORE"), ordinal = 1)
    private int kiln$local(int y) {
        return y + 100;
    }

    @ModifyVariable(method = "argument", at = @At("HEAD"), argsOnly = true)
    private int kiln$argumentTimesThree(int x) {
        return x * 3;
    }

    @ModifyConstant(method = "constant", constant = @Constant(intValue = 42))
    private int kiln$constant(int value) {
        return value + 1;
    }
}
