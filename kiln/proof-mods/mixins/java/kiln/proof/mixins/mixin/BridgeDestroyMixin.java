package kiln.proof.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player breaking a block (D-204): the block at x = 7 stays. */
@Mixin(ServerPlayerGameMode.class)
public abstract class BridgeDestroyMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void kiln$kept(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (pos.getX() == 7) {
            cir.setReturnValue(false);
        }
    }
}
