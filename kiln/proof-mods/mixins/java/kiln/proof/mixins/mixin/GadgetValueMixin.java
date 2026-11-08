package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** @ModifyReturnValue on the mod's own class: two on one method in turn, a static one, captured arguments, TAIL. */
@Mixin(Gadget.class)
public abstract class GadgetValueMixin {
    @ModifyReturnValue(method = "size", at = @At("RETURN"))
    private int kiln$bigger(int original) {
        return original + 10;
    }

    @ModifyReturnValue(method = "size", at = @At("RETURN"))
    private static int kiln$doubled(int original) {
        return original * 2;
    }

    @ModifyReturnValue(method = "total", at = @At("RETURN"))
    private long kiln$withArguments(long original, long a, int b) {
        return original * 100 + b;
    }

    @ModifyReturnValue(method = "ratio", at = @At("TAIL"))
    private double kiln$last(double original) {
        return original + 1000;
    }
}
