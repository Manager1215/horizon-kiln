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

    /**
     * Whether the player is an operator: as far as Kiln knows, the player running a server command at operator level 2
     * or more (Horizon says each command's level); anyone else isn't.
     */
    public boolean isOp(NameAndId player) {
        return kiln.ServerCommands.isOperator(player.id());
    }

    /** A line in every player's chat (or over their hotbar). */
    public void broadcastSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) {
        for (ServerPlayer player : getPlayers()) {
            player.sendSystemMessage(message, overlay);
        }
    }

    public ServerPlayer getPlayerByName(String name) {
        for (ServerPlayer p : Players.where(o -> o.player().getScoreboardName().equalsIgnoreCase(name))) {
            return p;
        }
        return null;
    }

    /**
     * A player joins, at their first tick on the server: their connection's listener is made (Fabric's {@code INIT}
     * in it); Fabric's {@code JOIN} comes after (kiln.Server.connect). The mods' mixins in this run there (Kiln's
     * Mixin bridge, D-102).
     */
    public void placeNewPlayer(net.minecraft.network.Connection connection, ServerPlayer player,
            net.minecraft.server.network.CommonListenerCookie cookie) {
        net.minecraft.server.network.ServerGamePacketListenerImpl listener =
                new net.minecraft.server.network.ServerGamePacketListenerImpl(player);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.INIT.invoker().onPlayInit(listener, server);
        kiln$listener = listener;
    }

    /** A player leaves, after Fabric's {@code DISCONNECT} (kiln.Server.disconnect): Horizon takes them off. */
    public void remove(ServerPlayer player) {}

    /**
     * A player respawns: the game makes the new player from the old ({@link ServerPlayer#restoreFrom}); Horizon made
     * them, Kiln hands them in first ({@link #kiln$respawning}). Fabric's {@code ENTITY_LOAD} and {@code
     * AFTER_RESPAWN} for them after.
     */
    public ServerPlayer respawn(ServerPlayer old, boolean alive, net.minecraft.world.entity.Entity.RemovalReason reason) {
        ServerPlayer player = respawning != null ? respawning : old;
        respawning = null;
        player.restoreFrom(old, alive);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(player, player.level());
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, player, alive);
        return player;
    }

    private ServerPlayer respawning;
    private net.minecraft.server.network.ServerGamePacketListenerImpl kiln$listener;

    /** Kiln's own: the player Horizon respawned, for the next {@link #respawn}. */
    public void kiln$respawning(ServerPlayer player) {
        respawning = player;
    }

    /** Kiln's own: the listener the last {@link #placeNewPlayer} made, for Fabric's {@code JOIN}. */
    public net.minecraft.server.network.ServerGamePacketListenerImpl kiln$listener() {
        return kiln$listener;
    }

    public int getPlayerCount() {
        return Players.online().size();
    }

    public String[] getPlayerNamesArray() {
        return Players.where(o -> true).stream().map(ServerPlayer::getScoreboardName).toArray(String[]::new);
    }
}
