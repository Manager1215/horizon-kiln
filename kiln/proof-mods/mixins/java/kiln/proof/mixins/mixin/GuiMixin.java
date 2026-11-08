package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A screen set: the one coming, over the one open at the start; the one open at the end. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void kiln$opens(Screen screen, CallbackInfo ci) {
        Moments.saw("setScreen HEAD " + Moments.name(screen) + " over " + Moments.name(((Gui) (Object) this).screen()));
    }

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void kiln$opened(Screen screen, CallbackInfo ci) {
        Moments.saw("setScreen RETURN " + Moments.name(((Gui) (Object) this).screen()));
    }
}
