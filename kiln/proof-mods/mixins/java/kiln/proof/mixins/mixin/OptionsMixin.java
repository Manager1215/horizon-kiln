package kiln.proof.mixins.mixin;

import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's options read, as mods inject into it to read their own with them. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "load", at = @At("TAIL"))
    private void kiln$loaded(CallbackInfo ci) {
        ProofMixins.optionsLoaded++;
    }
}
