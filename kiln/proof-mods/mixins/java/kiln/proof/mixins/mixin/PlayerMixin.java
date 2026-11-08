package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's tick, a common class's method, as RebornCore and others inject into it. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        Moments.saw("Player.tick HEAD");
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void kiln$ticked(CallbackInfo ci) {
        Moments.saw("Player.tick TAIL");
    }
}
