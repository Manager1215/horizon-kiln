package kiln.proof.mixins.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's blow and death on the server, at Horizon's hooks (Kiln's Mixin bridge, D-106), as combat and death
 * coordinates mods inject: a blow on entity 99 is cancelled, and a player is told of their death's cause.
 */
@Mixin(ServerPlayer.class)
public abstract class BridgeAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void kiln$attacks(Entity target, CallbackInfo ci) {
        if (target.getId() == 99) {
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void kiln$dies(DamageSource source, CallbackInfo ci) {
        ((Player) (Object) this).sendSystemMessage(Component.literal("Kiln's Mixin bridge: died of "
                + source.getMsgId()));
    }
}
