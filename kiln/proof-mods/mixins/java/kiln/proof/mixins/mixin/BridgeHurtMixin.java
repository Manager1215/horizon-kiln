package kiln.proof.mixins.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hit on the server, at Horizon's {@code living_incoming_damage} (Kiln's Mixin bridge, D-102), as the mods that change
 * damage do: a mob takes no fall damage (cancelled at the head) and half of any other; a player is told once that
 * their hit came through, and takes it as it is.
 */
@Mixin(LivingEntity.class)
public abstract class BridgeHurtMixin {
    @Unique
    private static boolean kiln$told;

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void kiln$hurts(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof Player player) {
            if (!kiln$told) {
                kiln$told = true;
                player.sendSystemMessage(Component.literal("Kiln's Mixin bridge: hurtServer " + source.getMsgId()
                        + " " + amount + " by " + (source.getEntity() == null ? "nothing" : source.getEntity().getId())));
            }
            return;
        }
        if (source.is(ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.withDefaultNamespace("fall")))) {
            cir.setReturnValue(false);
        }
    }

    /** A mob a player killed (D-106): the player is told. */
    @Inject(method = "die", at = @At("HEAD"))
    private void kiln$dies(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof Player) && source.getEntity() instanceof Player killer) {
            killer.sendSystemMessage(Component.literal("Kiln's Mixin bridge: killed " + ((LivingEntity) self).getId()
                    + " with " + source.getMsgId()));
        }
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float kiln$halves(float amount) {
        Object self = this;
        return self instanceof Player ? amount : amount / 2;
    }
}
