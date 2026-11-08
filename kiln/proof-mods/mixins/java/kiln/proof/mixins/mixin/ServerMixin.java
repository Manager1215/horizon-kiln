package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The integrated server loading its world, as Fabric's template mixin injects into it (by its name only). */
@Mixin(MinecraftServer.class)
public abstract class ServerMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void kiln$loads(CallbackInfo ci) {
        Moments.saw("loadLevel HEAD");
    }
}
