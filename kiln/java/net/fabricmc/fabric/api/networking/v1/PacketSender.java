package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Kiln's stand-in for Fabric's {@code PacketSender}: where a mod's payload goes, the server from a client, a player
 * from the server ({@link kiln.Net}). One handed over where Kiln has no connection sends nothing.
 */
public interface PacketSender {
    default void sendPacket(CustomPacketPayload payload) {}

    /** Kiln doesn't close a connection for a mod: the reason goes to the log. */
    default void disconnect(Component reason) {
        kiln.Native.log("a mod asked to disconnect: " + reason.getString());
    }
}
