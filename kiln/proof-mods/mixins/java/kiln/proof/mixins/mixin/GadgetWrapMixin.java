package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Mixin;

/** @WrapMethod on the mod's own class: other arguments, a static method, a void one, the wrong arguments. */
@Mixin(Gadget.class)
public abstract class GadgetWrapMixin {
    @WrapMethod(method = "greet")
    private String kiln$loud(String who, int times, Operation<String> original) {
        return original.call(who.toUpperCase(java.util.Locale.ROOT), times + 1) + "!";
    }

    @WrapMethod(method = "scale")
    private static long kiln$capped(long v, double by, Operation<Long> original) {
        return Math.min(original.call(v, by), 1000L);
    }

    @WrapMethod(method = "touch")
    private void kiln$twice(Operation<Void> original) {
        original.call();
        original.call();
    }

    @WrapMethod(method = "sum")
    private int kiln$sloppy(int a, int b, Operation<Integer> original) {
        return a < 0 ? original.call(a) : original.call(a, b);
    }
}
