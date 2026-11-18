package kiln.proof.mixins.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player picking up an experience orb (D-200): an orb worth more than 10 is left; at the orb's {@code take}
 * (pickup-notifications' place, D-213) the player is told its worth.
 */
@Mixin(ExperienceOrb.class)
public abstract class BridgeOrbMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void kiln$touched(Player player, CallbackInfo ci) {
        if (((ExperienceOrb) (Object) this).getValue() > 10) {
            ci.cancel();
        }
    }

    @Inject(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void kiln$taken(Player player, CallbackInfo ci) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Kiln's Mixin bridge: took an orb worth "
                + ((ExperienceOrb) (Object) this).getValue()));
    }
}
