package kiln.proof.mixins.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * An anvil's result, at Horizon's {@code prepare_anvil} (Kiln's Mixin bridge, D-202), as the "no Too Expensive" mods
 * change it: the game's 40 levels in {@code createResult} are never reached, so a rename costing more isn't capped at
 * 39 and a result costing 40 or more isn't refused.
 */
@Mixin(AnvilMenu.class)
public abstract class BridgeAnvilMixin {
    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private int kiln$neverTooExpensive(int forty) {
        return Integer.MAX_VALUE;
    }
}
