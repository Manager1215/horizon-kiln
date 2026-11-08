package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Gadget;
import kiln.proof.mixins.GadgetMarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** After GadgetOverwriteMixin at the same priority: its @Overwrite of twice is skipped, the rest merges. */
@Mixin(Gadget.class)
public abstract class GadgetSamePriorityMixin implements GadgetMarks {
    @Overwrite
    public static int twice(int n) {
        return 100 * n;
    }

    @Override
    public int kiln$marks() {
        return 7;
    }
}
