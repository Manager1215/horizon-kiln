package net.fabricmc.fabric.api.networking.v1;

import java.util.Set;
import kiln.Net;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Kiln's stand-in for Fabric's {@code ServerPlayNetworking}: the server's side of a mod's play payloads, over Horizon's
 * channel for mods ({@link Net}). A receiver runs on the server's thread, in a call of Horizon's server at the
 * sender's next tick; what it sends a player leaves at the end of that tick.
 */
public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler) {
        return Net.SERVER_RECEIVERS.putIfAbsent(type.id(), handler) == null;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        return Net.SERVER_RECEIVERS.remove(id);
    }

    public static Set<Identifier> getGlobalReceivers() {
        return Set.copyOf(Net.SERVER_RECEIVERS.keySet());
    }

    /**
     * Whether the player's client takes the type: Kiln's Java sides load the same mods, so whether a mod registered it
     * for the client. Not asked of the client itself (Fabric's channel registration isn't carried).
     */
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return canSend(player, type.id());
    }

    public static boolean canSend(ServerPlayer player, Identifier id) {
        return Net.CLIENTBOUND_PLAY.has(id);
    }

    /** The same for a player's connection, as {@code ServerPlayConnectionEvents} hands it over. */
    public static boolean canSend(ServerGamePacketListenerImpl handler, CustomPacketPayload.Type<?> type) {
        return canSend(handler.getPlayer(), type.id());
    }

    public static boolean canSend(ServerGamePacketListenerImpl handler, Identifier id) {
        return canSend(handler.getPlayer(), id);
    }

    public static PacketSender getSender(ServerPlayer player) {
        return Net.toPlayer(player);
    }

    public static PacketSender getSender(ServerGamePacketListenerImpl handler) {
        return Net.toPlayer(handler.getPlayer());
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        Net.sendToPlayer(player, payload);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        MinecraftServer server();

        ServerPlayer player();

        PacketSender responseSender();
    }
}
