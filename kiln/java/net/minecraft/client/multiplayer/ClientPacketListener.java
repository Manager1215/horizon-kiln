package net.minecraft.client.multiplayer;

/** Kiln's stand-in for {@code ClientPacketListener}: the connection to the world, for what it knows. */
public class ClientPacketListener {
    private final ClientAdvancements advancements = new ClientAdvancements();

    public ClientAdvancements getAdvancements() {
        return advancements;
    }
}
