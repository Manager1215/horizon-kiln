package kiln.proof.net;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * The player's side: {@code /kiln_proof_net} sends the server a ping when it takes one; its pong comes back into the
 * chat, with what the server saw, and so does the server's news (a welcome, who joined, left or pinged nearby).
 */
public final class ProofNetClient implements ClientModInitializer {
    private static int sent;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommands.literal(ProofNet.ID).executes(c -> {
                    if (!ClientPlayNetworking.canSend(Ping.TYPE)) {
                        c.getSource().sendFeedback(Component.literal("Kiln Proof Net: the server takes no pings"));
                        return 0;
                    }
                    ClientPlayNetworking.send(new Ping(++sent, "hello"));
                    return 1;
                })));
        ClientPlayNetworking.registerGlobalReceiver(Pong.TYPE, (pong, context) ->
                context.player().sendSystemMessage(Component.literal("Kiln Proof Net: pong " + pong.number() + " ("
                        + pong.word() + ") back, " + pong.served() + " served, at " + pong.at().getX() + " "
                        + pong.at().getY() + " " + pong.at().getZ() + " for " + pong.player())));
        ClientPlayNetworking.registerGlobalReceiver(News.TYPE, (news, context) ->
                context.player().sendSystemMessage(Component.literal("Kiln Proof Net: " + news.line())));
    }
}
