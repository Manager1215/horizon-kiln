package kiln.proof.mixins.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A message come to the window's chat, as chat mods inject into ChatComponent.addMessage (D-106). */
@Mixin(ChatComponent.class)
public abstract class BridgeChatMixin implements kiln.proof.mixins.ChatStats {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.Minecraft minecraft;

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage> allMessages;

    @Override
    public net.minecraft.client.Minecraft kiln$minecraft() {
        return minecraft;
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void kiln$hears(Component message, CallbackInfo ci) {
        if (message.getString().startsWith("kiln bridge chat")) {
            org.slf4j.LoggerFactory.getLogger("kiln_proof_mixins").info("Kiln's Mixin bridge: chat addMessage {}{}",
                    message.getString(), allMessages != null ? ", the chat's messages kept" : "");
        }
    }
}
