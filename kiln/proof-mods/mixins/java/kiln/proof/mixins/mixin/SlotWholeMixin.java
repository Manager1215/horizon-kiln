package kiln.proof.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import kiln.proof.mixins.ProofMixins;
import kiln.proof.mixins.SlotStats;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The whole-method kinds on a hand-written stand-in: an overwrite, a return value, a wrapper, field initializers. */
@Mixin(Slot.class)
public abstract class SlotWholeMixin implements SlotStats {
    @Unique
    private int kiln$asked = 0;

    @Unique
    private final List<String> kiln$notes = new ArrayList<>();

    @Overwrite
    public boolean isActive() {
        return !ProofMixins.slotsHidden;
    }

    @ModifyReturnValue(method = "getContainerSlot", at = @At("RETURN"))
    private int kiln$shifted(int original) {
        return original + ProofMixins.slotShift;
    }

    @WrapMethod(method = "mayPlace")
    private boolean kiln$guarded(ItemStack stack, Operation<Boolean> original) {
        kiln$asked++;
        return !ProofMixins.slotsLocked && original.call(stack);
    }

    @Override
    public int kiln$asked() {
        return kiln$asked;
    }

    @Override
    public List<String> kiln$notes() {
        return kiln$notes;
    }
}
