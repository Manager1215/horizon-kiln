package kiln.proof.mixins.mixin;

import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A static method's RETURN, on the class most mods mix into. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "getInstance", at = @At("RETURN"))
    private static void kiln$asked(CallbackInfoReturnable<Minecraft> cir) {
        if (cir.getReturnValue() != null) {
            ProofMixins.asked++;
        }
    }
}
