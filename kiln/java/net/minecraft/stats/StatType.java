package net.minecraft.stats;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code StatType}: a kind of statistic the game counts for each player ({@code minecraft:custom},
 * {@code minecraft:used}...), one {@link Stat} for each thing it counts.
 */
public class StatType<T> implements Iterable<Stat<T>> {
    private final String id;
    private final Map<T, Stat<T>> stats = new ConcurrentHashMap<>();

    public StatType(Registry<T> registry, Component name) {
        this("minecraft:unknown");
    }

    protected StatType() {
        this("minecraft:unknown");
    }

    StatType(String id) {
        this.id = id;
    }

    /** Kiln's own: its id in the game's stat type registry ({@code minecraft:custom}). */
    public String kiln$id() {
        return id;
    }

    public boolean contains(T value) {
        return stats.containsKey(value);
    }

    public Stat<T> get(T value) {
        return stats.computeIfAbsent(value, v -> new Stat<>(this, v));
    }

    @Override
    public Iterator<Stat<T>> iterator() {
        return stats.values().iterator();
    }

    public Component getDisplayName() {
        return Component.translatable("stat_type." + id.replace(':', '.'));
    }
}
