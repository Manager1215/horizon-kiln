package kiln.proof.mixins.mixin;

import kiln.proof.mixins.ProofMixins;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A void method's HEAD, with the target's arguments, cancelled. */
@Mixin(Slot.class)
public class SlotMixin {
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void kiln$frozen(ItemStack stack, CallbackInfo ci) {
        if (ProofMixins.frozen) {
            ProofMixins.refused++;
            ci.cancel();
        }
    }
}
