package net.minecraft.client.multiplayer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * Kiln's stand-in for {@code PlayerInfo}: a player the connection lists (the player list, Tab), as the window's game
 * has them each tick: who, their game mode and latency. Their profile's skin and properties aren't known.
 */
public class PlayerInfo {
    private final UUID id;
    private final String name;
    private GameType gameMode;
    private int latency;
    private Component tabListDisplayName;
    private boolean showHat = true;
    private int tabListOrder;

    private PlayerInfo(UUID id, String name, GameType gameMode, int latency) {
        this.id = id;
        this.name = name;
        this.gameMode = gameMode;
        this.latency = latency;
    }

    public GameType getGameMode() {
        return gameMode;
    }

    public int getLatency() {
        return latency;
    }

    public Component getTabListDisplayName() {
        return tabListDisplayName;
    }

    public void setTabListDisplayName(Component name) {
        tabListDisplayName = name;
    }

    public boolean showHat() {
        return showHat;
    }

    public void setShowHat(boolean showHat) {
        this.showHat = showHat;
    }

    public int getTabListOrder() {
        return tabListOrder;
    }

    public void setTabListOrder(int order) {
        tabListOrder = order;
    }

    /** Kiln's own: the player's UUID (vanilla's {@code getProfile().id()}). */
    public UUID kiln$id() {
        return id;
    }

    /** Kiln's own: the player's name (vanilla's {@code getProfile().name()}). */
    public String kiln$name() {
        return name;
    }

    /** Kiln's own: this player now, as the game listed them again. */
    void kiln$update(PlayerInfo now) {
        gameMode = now.gameMode;
        latency = now.latency;
    }

    /**
     * Kiln's own: a listed player as the tick carries them, {@code <uuid, 32 hex digits>:<game mode>:<latency>:<name,
     * %-escaped>}; null when it doesn't read. A UUID of 0 (Horizon without one) is the name's offline one.
     */
    static PlayerInfo kiln$parse(String entry) {
        String[] f = entry.split(":", 4);
        if (f.length != 4 || f[0].length() != 32) {
            return null;
        }
        try {
            String name = URLDecoder.decode(f[3], StandardCharsets.UTF_8);
            UUID id = new UUID(Long.parseUnsignedLong(f[0].substring(0, 16), 16),
                    Long.parseUnsignedLong(f[0].substring(16), 16));
            if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) {
                id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            }
            return new PlayerInfo(id, name, GameType.byId(Integer.parseInt(f[1])), Integer.parseInt(f[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
