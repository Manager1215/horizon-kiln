package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An invoker of a protected method of a hand-written stand-in, inherited by the mod's own menu. */
@Mixin(AbstractContainerMenu.class)
public interface MenuInvoker {
    @Invoker("addSlot")
    Slot kiln$addSlot(Slot slot);
}
