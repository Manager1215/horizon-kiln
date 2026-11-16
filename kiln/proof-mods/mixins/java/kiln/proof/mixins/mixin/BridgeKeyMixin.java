package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A key down or up: KeyboardHandler.keyPress, a method Kiln's stand-in hasn't, added for this (D-093, D-102). */
@Mixin(KeyboardHandler.class)
public abstract class BridgeKeyMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void kiln$keys(long window, int action, KeyEvent key, CallbackInfo ci) {
        Moments.bridge("keyPress " + key.key() + " " + action);
    }
}
