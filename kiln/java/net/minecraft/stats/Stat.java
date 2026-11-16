package net.minecraft.stats;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Kiln's stand-in for {@code Stat}: one statistic, a type and what it counts ({@code minecraft.custom:minecraft.play_time}). */
public class Stat<T> {
    private final StatType<T> type;
    private final T value;

    Stat(StatType<T> type, T value) {
        this.type = type;
        this.value = value;
    }

    protected Stat() {
        this(null, null);
    }

    public StatType<T> getType() {
        return type;
    }

    public T getValue() {
        return value;
    }

    /** Kiln's own: what it counts, by id ({@code minecraft:play_time}, an item's or a block's id), as the game keys it. */
    public String kiln$key() {
        if (value instanceof Identifier id) {
            return id.toString();
        }
        if (value instanceof Item item) {
            return item.kiln$id().toString();
        }
        if (value instanceof Block block) {
            return block.kiln$id().toString();
        }
        if (value instanceof EntityType<?> entity) {
            return String.valueOf(EntityType.getKey(entity));
        }
        return String.valueOf(value);
    }

    public static <T> String buildName(StatType<T> type, T value) {
        return type.kiln$id().replace(':', '.') + ":" + type.get(value).kiln$key().replace(':', '.');
    }

    public String format(int value) {
        return Integer.toString(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Stat<?> s && Objects.equals(type, s.type) && Objects.equals(value, s.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return type == null ? "stat" : type.kiln$id().replace(':', '.') + ":" + kiln$key().replace(':', '.');
    }
}
