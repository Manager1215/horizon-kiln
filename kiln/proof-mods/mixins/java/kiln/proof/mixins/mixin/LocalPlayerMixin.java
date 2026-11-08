package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import kiln.proof.mixins.ProofMixins;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The window's player's tick, and a method the game's LocalPlayer declares and Kiln's inherits from Player
 * ({@code getInventory}): Kiln adds it to LocalPlayer, calling Player's, so the injection has its method.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        Moments.saw("LocalPlayer.tick HEAD");
    }

    @Inject(method = "getInventory", at = @At("RETURN"))
    private void kiln$inventory(CallbackInfoReturnable<Inventory> cir) {
        if (cir.getReturnValue() != null) {
            ProofMixins.inventoryAsked++;
        }
    }
}
