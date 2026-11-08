package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An @Inject at the HEAD of a method Kiln's stand-in hasn't, named without its descriptor: Kiln adds the method, its
 * descriptor from the handler (no arguments before the callback, an Integer in it: {@code ()I}), and the injection
 * goes in.
 */
@Mixin(Slot.class)
public abstract class AddedMethodMixin {
    @Inject(method = "getAddedCount", at = @At("HEAD"), cancellable = true)
    private void kiln$seven(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(7);
    }
}
