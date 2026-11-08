package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A @Redirect at a call Kiln's stand-in makes in its own code ({@code Slot.set} calls {@code setChanged}). */
@Mixin(Slot.class)
public class RedirectMixin {
    @Unique
    public int kiln$neverMerged() {
        return 1;
    }

    @Redirect(method = "set", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;setChanged()V"))
    private void kiln$redirected(Slot slot) {}
}
