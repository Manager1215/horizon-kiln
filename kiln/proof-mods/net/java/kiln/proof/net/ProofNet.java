package kiln.proof.net;

import java.util.Collection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kiln's proof of a mod's own payloads (kiln/proof.py): the server hears a player's ping, tells them so in chat, and
 * answers with a pong; the players who see the one who pinged hear of it. A joining player gets the server's welcome
 * as they join, with how many are on the server, and the others hear who joined and who left. Written against
 * Minecraft 26.3 and Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class ProofNet implements ModInitializer {
    public static final String ID = "kiln_proof_net";
    private static int served;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(Ping.TYPE, Ping.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Pong.TYPE, Pong.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(News.TYPE, News.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Collection<ServerPlayer> all = PlayerLookup.all(server);
            String name = handler.getPlayer().getName().getString();
            sender.sendPacket(new News("welcome " + name + ", " + all.size() + " on the server"));
            for (ServerPlayer other : all) {
                if (other != handler.player) {
                    ServerPlayNetworking.send(other, new News(name + " joined, " + all.size() + " on the server"));
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.getUUID().equals(handler.player.getUUID())) {
                    ServerPlayNetworking.send(other, new News(handler.player.getName().getString() + " left"));
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Ping.TYPE, (ping, context) -> {
            ServerPlayer player = context.player();
            served++;
            player.sendSystemMessage(Component.literal("Kiln Proof Net: the server got ping " + ping.number() + " ("
                    + ping.word() + ") from " + player.getName().getString()));
            context.responseSender().sendPacket(new Pong(ping.number(), served, player.blockPosition(),
                    player.getUUID(), ping.word()));
            for (ServerPlayer near : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(near, new News(player.getName().getString() + " pinged near you"));
            }
        });
    }
}
