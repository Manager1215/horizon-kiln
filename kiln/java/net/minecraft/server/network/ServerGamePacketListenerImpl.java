package net.minecraft.server.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Kiln's stand-in for {@code ServerGamePacketListenerImpl}: a player's connection to the server, as Fabric's {@code
 * ServerPlayConnectionEvents} hand it over. The player is the one the server's call describes; their packets are
 * Horizon's.
 */
public class ServerGamePacketListenerImpl {
    public final ServerPlayer player;

    /** Kiln's own: the connection of {@code player}. */
    public ServerGamePacketListenerImpl(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * A packet to the player: Horizon's server sends the game's packets, and Kiln doesn't carry a mod's own yet, so it
     * goes nowhere; Kiln's log names its class once, as it names any member that is only a name.
     */
    public void send(net.minecraft.network.protocol.Packet<?> packet) {
        kiln.Missing.called("net.minecraft.server.network.ServerGamePacketListenerImpl.send("
                + (packet == null ? "null" : packet.getClass().getSimpleName()) + ")");
    }
}
