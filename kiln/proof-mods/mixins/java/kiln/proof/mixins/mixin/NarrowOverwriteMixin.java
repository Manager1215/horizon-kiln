package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/** An @Overwrite less visible than its target, its config not conforming: as Mixin, the mixin is left out. */
@Mixin(Gadget.class)
public abstract class NarrowOverwriteMixin {
    @Overwrite
    protected int size() {
        return -1;
    }

    @Unique
    public int kiln$neverNarrowed() {
        return 1;
    }
}
