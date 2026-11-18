package kiln.proof.mixins.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server's tick (D-200), as per-tick mods inject: it counts the ticks, which the test reads in the log. */
@Mixin(MinecraftServer.class)
public abstract class BridgeServerTickMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void kiln$ticks(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        kiln.proof.mixins.Moments.serverTicks++;
        if (kiln.proof.mixins.Moments.serverTicks == 1) {
            org.slf4j.LoggerFactory.getLogger("kiln_proof_mixins").info("Kiln's Mixin bridge: MinecraftServer.tickServer ran");
        }
    }
}
