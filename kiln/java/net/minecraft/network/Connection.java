package net.minecraft.network;

import net.minecraft.network.protocol.PacketFlow;

/**
 * Kiln's stand-in for {@code Connection}: the client's link to its server, which is the game's. Kiln's says whether
 * the world is the player's own (an integrated server in memory) and that it is open while the world is.
 */
public class Connection {
    private final PacketFlow flow;
    private boolean memory;
    private boolean connected = true;

    public Connection(PacketFlow flow) {
        this.flow = flow;
    }

    protected Connection() {
        this(PacketFlow.CLIENTBOUND);
    }

    /** Kiln's own: whether the world is a single-player one; whether it is still open. */
    public void kiln$state(boolean memory, boolean connected) {
        this.memory = memory;
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return false;
    }

    public boolean isMemoryConnection() {
        return memory;
    }

    public PacketFlow getReceiving() {
        return flow;
    }

    public PacketFlow getSending() {
        return flow == PacketFlow.CLIENTBOUND ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
    }

    /** The server's address: Kiln doesn't know it, so a single-player world is {@code local} and others {@code server}. */
    public String getLoggableAddress(boolean logIps) {
        return memory ? "local" : "server";
    }
}
