package net.fabricmc.fabric.api.client.networking.v1;

import java.util.Set;
import kiln.Net;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code ClientPlayNetworking}: the window's side of a mod's play payloads, over Horizon's
 * channel for mods ({@link Net}). What a mod sends leaves with the game's next tick; a receiver runs in Java's client
 * thread, as the game hands Kiln the payload.
 */
public final class ClientPlayNetworking {
    private ClientPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler) {
        return Net.CLIENT_RECEIVERS.putIfAbsent(type.id(), handler) == null;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        return Net.CLIENT_RECEIVERS.remove(id);
    }

    public static Set<Identifier> getGlobalReceivers() {
        return Set.copyOf(Net.CLIENT_RECEIVERS.keySet());
    }

    /** Kiln has one connection: a receiver of this one is a global one, until the mod unregisters it. */
    public static <T extends CustomPacketPayload> boolean registerReceiver(CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler) {
        return registerGlobalReceiver(type, handler);
    }

    public static PlayPayloadHandler<?> unregisterReceiver(Identifier id) {
        return unregisterGlobalReceiver(id);
    }

    public static Set<Identifier> getReceived() {
        return getGlobalReceivers();
    }

    /**
     * Whether the server takes the type: in a world, with a mod's code registered to send it (Kiln's Java sides load
     * the same mods). Not asked of the server itself (Fabric's channel registration isn't carried).
     */
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return canSend(type.id());
    }

    public static boolean canSend(Identifier id) {
        return Minecraft.getInstance().player != null && Net.SERVERBOUND_PLAY.has(id);
    }

    public static PacketSender getSender() {
        return Net.TO_SERVER;
    }

    public static void send(CustomPacketPayload payload) {
        Net.sendToServer(payload);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        Minecraft client();

        LocalPlayer player();

        PacketSender responseSender();
    }
}
