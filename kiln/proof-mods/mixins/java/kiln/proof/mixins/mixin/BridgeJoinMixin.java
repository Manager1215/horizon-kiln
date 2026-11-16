package kiln.proof.mixins.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player joining, at Kiln's own call for it (PlayerList.placeNewPlayer, D-102), as welcome mods inject. */
@Mixin(PlayerList.class)
public abstract class BridgeJoinMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void kiln$joined(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        player.sendSystemMessage(Component.literal("Kiln's Mixin bridge: placeNewPlayer " + player.getScoreboardName()));
    }
}
