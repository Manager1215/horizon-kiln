package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A world joined, as "on join" mods inject into ClientPacketListener.handleLogin (D-106). */
@Mixin(ClientPacketListener.class)
public abstract class BridgeLoginMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void kiln$joined(ClientboundLoginPacket packet, CallbackInfo ci) {
        Moments.saw("handleLogin TAIL " + Moments.has());
    }
}
