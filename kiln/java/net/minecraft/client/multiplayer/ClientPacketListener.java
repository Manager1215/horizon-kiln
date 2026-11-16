package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kiln's stand-in for {@code ClientPacketListener}: the connection to the world, for what it knows: the advancements,
 * the players it lists (as the window's player list has them each tick), and the commands a mod sends as the player,
 * which the game sends on (the mods' client commands first, as Fabric's take them).
 */
public class ClientPacketListener {
    private final ClientAdvancements advancements = new ClientAdvancements();
    /** The level of this connection, as the game's field (mods' mixins read it): the window's, once it has one. */
    private ClientLevel level;

    public ClientLevel getLevel() {
        return level;
    }

    /** Kiln's own: the level the game set. */
    public void kiln$level(ClientLevel level) {
        this.level = level;
    }
    /** The listed players by UUID, in the game's order; one stays the same object while listed, as in vanilla. */
    private final Map<UUID, PlayerInfo> players = new LinkedHashMap<>();

    /** The registries the game holds, for the codecs that read and write registry entries by name. */
    /** What the client knows of the recipes, as the level's (Just Enough Breeding's plugin asks here, D-171). */
    public net.minecraft.world.item.crafting.RecipeAccess recipes() {
        return ClientLevel.RECIPES;
    }

    public net.minecraft.core.RegistryAccess.Frozen registryAccess() {
        return REGISTRIES;
    }

    /** Kiln's registries (what the mods register and the game's entries Kiln names), as every level's. */
    private static final net.minecraft.core.RegistryAccess.Frozen REGISTRIES = net.minecraft.core.RegistryAccess.KILN;

    public ClientAdvancements getAdvancements() {
        return advancements;
    }

    public Collection<PlayerInfo> getOnlinePlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** The players the list shows: Horizon's list has only those. */
    public Collection<PlayerInfo> getListedOnlinePlayers() {
        return getOnlinePlayers();
    }

    public Collection<UUID> getOnlinePlayerIds() {
        return Collections.unmodifiableSet(players.keySet());
    }

    public PlayerInfo getPlayerInfo(UUID id) {
        return players.get(id);
    }

    public PlayerInfo getPlayerInfo(String name) {
        for (PlayerInfo info : players.values()) {
            if (info.kiln$name().equals(name)) {
                return info;
            }
        }
        return null;
    }

    public PlayerInfo getPlayerInfoIgnoreCase(String name) {
        for (PlayerInfo info : players.values()) {
            if (info.kiln$name().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    private final net.minecraft.network.Connection connection = new net.minecraft.network.Connection(
            net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);

    /** The link to the server: a single-player world's is in memory. */
    public net.minecraft.network.Connection getConnection() {
        connection.kiln$state(net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer(),
                net.minecraft.client.Minecraft.getInstance().getConnection() == this);
        return connection;
    }

    /** The world's experimental features: Kiln knows of none. */
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
        return net.minecraft.world.flag.FeatureFlagSet.of();
    }

    /** The server's brand: the game's server is a vanilla one to the mods. */
    public String serverBrand() {
        return "vanilla";
    }

    /**
     * The server's recipes came (vanilla's item sets and stonecutter list): the mods' mixins into it run here. Kiln's
     * client recipes are {@code kiln.ClientRecipes}'s, given with Fabric's recipe sync just before (D-152).
     */
    public void handleUpdateRecipes(net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet) {}

    /** A command, without its slash, sent as the player typed it: the game sends it with the next tick. */
    /**
     * The world joined, the level and the player made: Horizon joins; the mods' mixins in this run as the client's
     * level and player come, before Fabric's {@code JOIN} (Kiln's Mixin bridge, D-106). Kiln has no packet to give.
     */
    public void handleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet) {
        kiln.Bridge.body(null, packet);
    }

    public void sendCommand(String command) {
        kiln.Native.send(true, strip(command));
    }

    /**
     * A chat message sent as the player, as typed in the chat: the game sends it signed (a message that starts with a
     * slash is a command) with the next tick.
     */
    public void sendChat(String message) {
        kiln.Native.sendChat(message);
    }

    /** A command, without its slash, sent as code's own (a clicked link's): the game sends it with the next tick. */
    public void sendUnattendedCommand(String command, net.minecraft.client.gui.screens.Screen screen) {
        kiln.Native.send(false, strip(command));
    }

    private static String strip(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /**
     * Kiln's own: the players the game lists now, {@code <uuid>:<game mode>:<latency>:<name>} entries joined by
     * {@code ,} ({@link PlayerInfo}). A player still listed keeps their object.
     */
    public void kiln$players(String field) {
        List<PlayerInfo> now = new ArrayList<>();
        for (String entry : field.split(",")) {
            PlayerInfo info = entry.isEmpty() ? null : PlayerInfo.kiln$parse(entry);
            if (info != null) {
                now.add(info);
            }
        }
        Map<UUID, PlayerInfo> kept = new LinkedHashMap<>();
        for (PlayerInfo info : now) {
            PlayerInfo old = players.get(info.kiln$id());
            if (old != null) {
                old.kiln$update(info);
                info = old;
            }
            kept.put(info.kiln$id(), info);
        }
        players.clear();
        players.putAll(kept);
    }
}
