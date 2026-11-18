package kiln.proof.mixins.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player picking up an item, at Horizon's {@code item_picked_up} (Kiln's Mixin bridge, D-200), as pickup filter mods
 * inject: a stick stays on the ground, and the player is told what they touched; at the statistic vanilla counts after
 * the pickup (pickup-notifications' place, D-213), what they picked up.
 */
@Mixin(ItemEntity.class)
public abstract class BridgePickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void kiln$touched(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        player.sendSystemMessage(Component.literal("Kiln's Mixin bridge: touched "
                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem().getItem()) + " x"
                + self.getItem().getCount()));
        if (self.getItem().is(net.minecraft.world.item.Items.STICK)) {
            ci.cancel();
        }
    }

    @Inject(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;I)V"))
    private void kiln$awarded(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        player.sendSystemMessage(Component.literal("Kiln's Mixin bridge: picked up "
                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem().getItem()) + " x"
                + self.getItem().getCount()));
    }
}
