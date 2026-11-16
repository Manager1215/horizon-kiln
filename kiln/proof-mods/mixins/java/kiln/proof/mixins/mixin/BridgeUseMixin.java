package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The attack and use keys on what the crosshair points at (Kiln's Mixin bridge, D-102). */
@Mixin(Minecraft.class)
public abstract class BridgeUseMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void kiln$attacks(CallbackInfoReturnable<Boolean> cir) {
        Moments.bridge("startAttack");
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void kiln$uses(CallbackInfo ci) {
        Moments.bridge("startUseItem");
    }
}
