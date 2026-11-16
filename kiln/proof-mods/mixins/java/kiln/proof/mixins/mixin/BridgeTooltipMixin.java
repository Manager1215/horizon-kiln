package kiln.proof.mixins.mixin;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An item's tooltip, as tooltip mods add a line at its end (Kiln's Mixin bridge's tooltip row, D-145). */
@Mixin(ItemStack.class)
public abstract class BridgeTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void kiln$line(Item.TooltipContext context, Player player, TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir) {
        cir.getReturnValue().add(Component.literal("Kiln bridge tooltip " + ((ItemStack) (Object) this).getCount()));
    }
}
