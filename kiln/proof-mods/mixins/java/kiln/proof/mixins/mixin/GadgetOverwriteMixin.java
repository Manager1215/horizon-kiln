package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Gadget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** @Overwrite on the mod's own class: a method, a private one by an alias, a static one. */
@Mixin(Gadget.class)
public abstract class GadgetOverwriteMixin {
    @Shadow
    @Final
    private String name;

    /** Beaten by GadgetPriorityMixin's, of a higher priority. */
    @Overwrite
    public String label() {
        return "overwritten " + name;
    }

    /** Named by its alias: the target's private {@code secret}. */
    @Overwrite(aliases = "secret")
    private String kiln$hidden() {
        return "no secret";
    }

    @Overwrite
    public static int twice(int n) {
        return 3 * n;
    }
}
