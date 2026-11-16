package net.minecraft.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/** Kiln's stand-in for {@code StatsCounter}: a player's statistics, by stat. */
public class StatsCounter {
    /** What was set here (by a mod's code), on top of the game's. */
    protected final Map<Stat<?>, Integer> stats = new ConcurrentHashMap<>();

    public void increment(Player player, Stat<?> stat, int amount) {
        setValue(player, stat, (int) Math.min((long) getValue(stat) + amount, Integer.MAX_VALUE));
    }

    public void setValue(Player player, Stat<?> stat, int value) {
        stats.put(stat, value);
    }

    public <T> int getValue(StatType<T> type, T value) {
        return getValue(type.get(value));
    }

    public int getValue(Stat<?> stat) {
        return stats.getOrDefault(stat, 0);
    }
}
