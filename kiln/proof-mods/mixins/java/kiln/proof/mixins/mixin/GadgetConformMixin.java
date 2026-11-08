package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** A private @Overwrite of a public method: its config's overwrites.conformVisibility makes it public. */
@Mixin(Gadget.class)
public abstract class GadgetConformMixin {
    @Overwrite
    private String describe() {
        return "conformed";
    }
}
