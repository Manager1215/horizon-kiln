package kiln.proof.mixins.plugged;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kiln.proof.mixins.Machine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The plugin's config's mixin it keeps. */
@Mixin(Machine.class)
public abstract class KeptMixin {
    @ModifyReturnValue(method = "plugged", at = @At("RETURN"))
    private String kiln$kept(String original) {
        return original + " kept";
    }
}
