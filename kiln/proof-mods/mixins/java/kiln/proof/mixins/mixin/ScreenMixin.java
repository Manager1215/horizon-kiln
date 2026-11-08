package kiln.proof.mixins.mixin;

import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code @Inject} at a call in a hand-written stand-in's code: {@code Screen.init(int, int)} calls {@code init()}. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "init(II)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;init()V"))
    private void kiln$beforeInit(int width, int height, CallbackInfo ci) {
        ProofMixins.screenInits++;
    }
}
