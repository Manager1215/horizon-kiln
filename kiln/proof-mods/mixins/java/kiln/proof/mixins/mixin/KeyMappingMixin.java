package kiln.proof.mixins.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import kiln.proof.mixins.KeyMappingStats;
import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Injections into a hand-written stand-in: HEAD that cancels with a value, RETURN that reads it, a constructor's TAIL. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements KeyMappingStats {
    @Shadow
    private int clickCount;

    @Unique
    private int kiln$consumed;

    @Unique
    private static int kiln$made;

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void kiln$blocked(CallbackInfoReturnable<Boolean> cir) {
        if (ProofMixins.blockClicks) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("RETURN"))
    private void kiln$count(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            kiln$consumed++;
        }
    }

    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;"
            + "ILnet/minecraft/client/KeyMapping$Category;)V", at = @At("TAIL"))
    private void kiln$made(String name, InputConstants.Type type, int key, KeyMapping.Category category,
            CallbackInfo ci) {
        kiln$made++;
    }

    @Override
    public int kiln$consumed() {
        return kiln$consumed;
    }

    @Override
    public int kiln$waiting() {
        return clickCount;
    }

    @Override
    public int kiln$made() {
        return kiln$made;
    }
}
