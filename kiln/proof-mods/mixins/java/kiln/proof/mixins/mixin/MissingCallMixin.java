package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An @Inject at a call the stand-in's code doesn't make (the game's does): Mixin finds no point, its config requires
 * one, so the mixin is left out, its other members too, and the class's other mixins stay.
 */
@Mixin(Slot.class)
public abstract class MissingCallMixin {
    @Unique
    public int kiln$neverInjected() {
        return 1;
    }

    @Inject(method = "setChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getNoSuchCall()Z"))
    private void kiln$never(CallbackInfo ci) {}
}
