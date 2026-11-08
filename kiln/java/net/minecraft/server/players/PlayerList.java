package net.minecraft.server.players;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kiln.Players;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Kiln's stand-in for {@code PlayerList}: the players on Horizon's integrated server ({@link Players}). */
public class PlayerList {
    private final MinecraftServer server;

    /** Kiln's own: the list of {@code server}'s players. */
    public PlayerList(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** The players, in the order the game lists them; a new list each time, as Kiln asks the game for them. */
    public List<ServerPlayer> getPlayers() {
        return new ArrayList<>(Players.where(o -> true));
    }

    public ServerPlayer getPlayer(UUID uuid) {
        for (ServerPlayer p : Players.where(o -> o.player().getUUID().equals(uuid))) {
            return p;
        }
        return null;
    }

    public ServerPlayer getPlayerByName(String name) {
        for (ServerPlayer p : Players.where(o -> o.player().getScoreboardName().equalsIgnoreCase(name))) {
            return p;
        }
        return null;
    }

    public int getPlayerCount() {
        return Players.online().size();
    }

    public String[] getPlayerNamesArray() {
        return Players.where(o -> true).stream().map(ServerPlayer::getScoreboardName).toArray(String[]::new);
    }
}
