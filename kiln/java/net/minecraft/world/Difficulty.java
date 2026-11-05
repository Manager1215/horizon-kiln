package net.minecraft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code Difficulty}: the world's difficulty, by vanilla's names. */
public enum Difficulty implements StringRepresentable {
    PEACEFUL(0, "peaceful"),
    EASY(1, "easy"),
    NORMAL(2, "normal"),
    HARD(3, "hard");

    private final int id;
    private final String key;

    Difficulty(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    public Component getDisplayName() {
        return Component.translatable("options.difficulty." + key);
    }

    public static Difficulty byId(int id) {
        return values()[Math.floorMod(id, 4)];
    }

    /** Kiln's own: the difficulty vanilla calls {@code key}, or NORMAL. */
    public static Difficulty kiln$byKey(String key) {
        for (Difficulty d : values()) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return NORMAL;
    }
}
