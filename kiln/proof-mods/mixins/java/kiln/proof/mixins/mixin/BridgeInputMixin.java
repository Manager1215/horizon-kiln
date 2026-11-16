package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wheel and the mouse's buttons, where Horizon's hooks fire (Kiln's Mixin bridge, D-102). */
@Mixin(MouseHandler.class)
public abstract class BridgeInputMixin {
    /** The game's field, which 92 mods' mixins name (D-108). */
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void kiln$scrolls(long window, double x, double y, CallbackInfo ci) {
        Moments.bridge("onScroll " + x + " " + y + (minecraft == net.minecraft.client.Minecraft.getInstance() ? ""
                : " without the game"));
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void kiln$clicks(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        Moments.bridge("onButton " + button.button() + " " + action);
    }
}
