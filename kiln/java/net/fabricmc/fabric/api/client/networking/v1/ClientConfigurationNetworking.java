package net.fabricmc.fabric.api.client.networking.v1;

import kiln.Net;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code ClientConfigurationNetworking}: the configuration phase's payloads on the
 * player's window (D-199). The server's come over Kiln's channel, marked as the configuration's, as the player joins,
 * ahead of play's; they reach their receivers at once, as in vanilla's configuration, before the window has its player.
 * There is no configuration connection of the window's own: {@code packetListener()} is null.
 */
public final class ClientConfigurationNetworking {
    private ClientConfigurationNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type,
            ConfigurationPayloadHandler<T> handler) {
        return Net.CLIENT_CONFIGURATION_RECEIVERS.putIfAbsent(type.id(), handler) == null;
    }

    public static ConfigurationPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        return Net.CLIENT_CONFIGURATION_RECEIVERS.remove(id);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return Net.SERVERBOUND_CONFIGURATION.has(type.id());
    }

    public static boolean canSend(Identifier id) {
        return Net.SERVERBOUND_CONFIGURATION.has(id);
    }

    public static void send(CustomPacketPayload payload) {
        Net.sendConfigurationToServer(payload);
    }

    @FunctionalInterface
    public interface ConfigurationPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        Minecraft client();

        ClientConfigurationPacketListenerImpl packetListener();

        PacketSender responseSender();
    }
}
