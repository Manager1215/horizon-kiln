package net.minecraft.stats;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Kiln's stand-in for {@code ServerStatsCounter}: a player's statistics on the server. They are Horizon's: during a
 * server call (a command, a use) Kiln asks the game for the acting player's value (`stat`); outside one, or for another
 * player, what a mod's code set here, else 0. A value a mod sets stays in Java (Horizon's own counting goes on).
 */
public class ServerStatsCounter extends StatsCounter {
    private final UUID player;

    public ServerStatsCounter(MinecraftServer server, Path file) {
        this((UUID) null);
    }

    /** Kiln's own: the statistics of the player {@code player}. */
    public ServerStatsCounter(UUID player) {
        this.player = player;
    }

    @Override
    public int getValue(Stat<?> stat) {
        Integer set = stats.get(stat);
        if (set != null || player == null || !kiln.Server.inCall() || stat.getType() == null) {
            return set != null ? set : 0;
        }
        String answer = kiln.Server.ask("stat", player.toString(), stat.getType().kiln$id(), stat.kiln$key()).trim();
        try {
            return answer.isEmpty() ? 0 : Integer.parseInt(answer);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void save() {}

    public void parse(com.mojang.datafixers.DataFixer fixer, com.google.gson.JsonElement json) {}

    public void markAllDirty() {}

    public void sendStats(ServerPlayer player) {}

    @Override
    public void setValue(Player player, Stat<?> stat, int value) {
        super.setValue(player, stat, value);
    }
}
