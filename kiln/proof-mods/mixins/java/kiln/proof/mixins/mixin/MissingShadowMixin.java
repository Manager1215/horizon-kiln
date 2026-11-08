package kiln.proof.mixins.mixin;

import kiln.proof.mixins.SlotTag;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A shadow of a field Kiln's stand-in hasn't: as Mixin, the mixin is left out (its interface too). */
@Mixin(Slot.class)
public abstract class MissingShadowMixin implements SlotTag {
    @Shadow
    private int noSuchField;

    @Override
    public int kiln$tag() {
        return noSuchField;
    }
}
