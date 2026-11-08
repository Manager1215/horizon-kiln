package kiln.proof.mixins.plugged;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kiln.proof.mixins.Machine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The plugin's config's mixin it refuses. */
@Mixin(Machine.class)
public abstract class RefusedMixin {
    @ModifyReturnValue(method = "plugged", at = @At("RETURN"))
    private String kiln$refused(String original) {
        return original + " refused";
    }
}
