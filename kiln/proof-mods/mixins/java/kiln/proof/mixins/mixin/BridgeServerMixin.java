package kiln.proof.mixins.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's server tick, at Horizon's {@code player_tick} (Kiln's Mixin bridge, D-102): told once, server side. */
@Mixin(ServerPlayer.class)
public abstract class BridgeServerMixin {
    @Unique
    private static boolean kiln$told;

    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!kiln$told && !self.level().isClientSide()) {
            kiln$told = true;
            self.sendSystemMessage(Component.literal("Kiln's Mixin bridge: ServerPlayer.tick on the server"));
        }
    }
}
