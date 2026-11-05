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
    /** The listed players by UUID, in the game's order; one stays the same object while listed, as in vanilla. */
    private final Map<UUID, PlayerInfo> players = new LinkedHashMap<>();

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

    /** A command, without its slash, sent as the player typed it: the game sends it with the next tick. */
    public void sendCommand(String command) {
        kiln.Native.send(true, strip(command));
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
