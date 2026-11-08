package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * An @Overwrite of a method Kiln's stand-in hasn't: Kiln adds the method (D-093), and the mod's code becomes it, its
 * other members merged too.
 */
@Mixin(Slot.class)
public abstract class MissingOverwriteMixin {
    @Overwrite
    public int getNoSuchThing() {
        return 1;
    }

    @Unique
    public int kiln$neverOverwritten() {
        return 1;
    }
}
