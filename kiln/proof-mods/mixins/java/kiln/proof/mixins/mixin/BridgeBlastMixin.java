package kiln.proof.mixins.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block's explosion resistance, at Horizon's {@code explosion_detonate} (Kiln's Mixin bridge, D-203), as the
 * blast-proof mods raise it: glass panes stand any explosion.
 */
@Mixin(Block.class)
public abstract class BridgeBlastMixin {
    @Inject(method = "getExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void kiln$blastProof(CallbackInfoReturnable<Float> cir) {
        if ("minecraft:glass_pane".equals(String.valueOf(BuiltInRegistries.BLOCK.getKey((Block) (Object) this)))) {
            cir.setReturnValue(3600000.0F);
        }
    }
}
