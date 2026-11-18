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
public class ClientPacketListener extends ClientCommonPacketListenerImpl {
    public ClientPacketListener() {
        this.minecraft = net.minecraft.client.Minecraft.getInstance();
    }

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

    /** The recipe book's recipes came: the game's, never told Kiln's client (the mods' mixins into it, D-215). */
    public void handleRecipeBookAdd(net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket packet) {}

    public void handleRecipeBookRemove(net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket packet) {}

    /**
     * The player respawned or changed dimension: the game's; Kiln never calls it (the window's player stays one
     * object). As vanilla's, the new player goes into the level (Architectury's mixins hook that call, D-215).
     */
    public void handleRespawn(net.minecraft.network.protocol.game.ClientboundRespawnPacket packet) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (level != null && mc.player != null) {
            level.addEntity(mc.player);
        }
    }

    /** The server's tags came: Kiln's tags are the game's data, read where asked (the mods' mixins into it). */
    @Override
    public void handleUpdateTags(net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket packet) {}

    // What the game's connection handles itself, never told Kiln's client.
    @Override
    public void handleKeepAlive(net.minecraft.network.protocol.common.ClientboundKeepAlivePacket packet) {}

    @Override
    public void handlePing(net.minecraft.network.protocol.common.ClientboundPingPacket packet) {}

    @Override
    public void handleCustomPayload(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet) {}

    @Override
    public void handleRequestCookie(net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket packet) {}

    @Override
    public net.minecraft.network.protocol.PacketFlow flow() {
        return net.minecraft.network.protocol.PacketFlow.CLIENTBOUND;
    }

    @Override
    public net.minecraft.network.ConnectionProtocol protocol() {
        return net.minecraft.network.ConnectionProtocol.PLAY;
    }

    /** A command, without its slash, sent as the player typed it: the game sends it with the next tick. */
    /**
     * The world joined, the level and the player made: Horizon joins; the mods' mixins in this run as the client's
     * level and player come, before Fabric's {@code JOIN} (Kiln's Mixin bridge, D-106). Kiln has no packet to give.
     */
    public void handleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet) {
        // As vanilla's: the server's view distance (the window's own, Kiln has no packet), where Architectury's
        // CLIENT_PLAYER_JOIN fires after (D-215).
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.options.setServerRenderDistance(packet != null ? packet.chunkRadius() : mc.options.renderDistance().get());
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    private net.minecraft.client.multiplayer.ClientSuggestionProvider suggestionsProvider;
    public net.minecraft.util.RandomSource random;
    private com.mojang.brigadier.CommandDispatcher<net.minecraft.client.multiplayer.ClientSuggestionProvider> commands;
    public net.minecraft.network.chat.LocalChatSession chatSession;
    public net.minecraft.network.chat.SignedMessageChain.Encoder signedMessageEncoder;
    public net.minecraft.client.multiplayer.ClientSuggestionProvider getSuggestionsProvider() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.getSuggestionsProvider"); return null; }
    public void handleSystemChat(net.minecraft.network.protocol.game.ClientboundSystemChatPacket p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.handleSystemChat"); }
    public void handleContainerSetSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.handleContainerSetSlot"); }
    public void handleCommands(net.minecraft.network.protocol.game.ClientboundCommandsPacket p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.handleCommands"); }
    public boolean onlineMode() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.onlineMode"); return false; }
    public boolean isAcceptingMessages() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.isAcceptingMessages"); return false; }
    public com.mojang.authlib.GameProfile getLocalGameProfile() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.getLocalGameProfile"); return null; }
    public com.mojang.brigadier.CommandDispatcher<net.minecraft.client.multiplayer.ClientSuggestionProvider> getCommands() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.getCommands"); return null; }
    public net.minecraft.client.DebugQueryHandler getDebugQueryHandler() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.getDebugQueryHandler"); return null; }
    public java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> levels() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.levels"); return null; }
    public void setKeyPair(net.minecraft.world.entity.player.ProfileKeyPair p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.setKeyPair"); }
    public net.minecraft.client.multiplayer.ServerData getServerData() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.getServerData"); return null; }
    public net.minecraft.world.scores.Scoreboard scoreboard() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.scoreboard"); return null; }
    public net.minecraft.client.multiplayer.SessionSearchTrees searchTrees() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.searchTrees"); return null; }
    public void setClientLoaded(boolean p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientPacketListener.setClientLoaded"); }
    public static enum CommandCheckResult {
        NO_ISSUES, PARSE_ERRORS, SIGNATURE_REQUIRED, PERMISSIONS_REQUIRED;
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
