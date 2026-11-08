package kiln.proof.mixins.mixin.twin;

import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A second mixin of the same simple name on the same class, with a handler of the same name and descriptor as the
 * first's (as Xaero's Minimap and World Map have): both apply, each handler once.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "getInstance", at = @At("RETURN"))
    private static void kiln$asked(CallbackInfoReturnable<Minecraft> cir) {
        if (cir.getReturnValue() != null) {
            ProofMixins.asked++;
        }
    }
}
