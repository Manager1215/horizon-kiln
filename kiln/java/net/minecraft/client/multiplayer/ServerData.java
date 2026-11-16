package net.minecraft.client.multiplayer;

/**
 * Kiln's stand-in for {@code ServerData}: a server as the server list keeps it, its name and address. Kiln makes one
 * for the server the window's game is on ({@code Minecraft.getCurrentServer}, D-178), so that a mod's files per server
 * (JEI's bookmarks) are kept by its address as under Fabric.
 */
public class ServerData {
    public String name;
    public String ip;
    private Type type;

    public ServerData(String name, String ip, Type type) {
        this.name = name;
        this.ip = ip;
        this.type = type;
    }

    protected ServerData() {
        this("", "", Type.OTHER);
    }

    /** A world of another computer on the local network, found by its broadcast. */
    public boolean isLan() {
        return type == Type.LAN;
    }

    /** A Realms server. */
    public boolean isRealm() {
        return type == Type.REALM;
    }

    public Type type() {
        return type;
    }

    public static enum Type {
        LAN, REALM, OTHER;
    }
}
