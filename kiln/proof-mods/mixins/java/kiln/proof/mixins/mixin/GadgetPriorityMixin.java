package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Listed before GadgetOverwriteMixin, but of a higher priority: its @Overwrite is the one that stays. */
@Mixin(value = Gadget.class, priority = 1100)
public abstract class GadgetPriorityMixin {
    @Overwrite
    public String label() {
        return "by priority";
    }
}
