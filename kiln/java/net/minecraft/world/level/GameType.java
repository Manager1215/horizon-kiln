package net.minecraft.world.level;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code GameType}: survival, creative, adventure, spectator, by the ids the game counts them. */
public enum GameType implements StringRepresentable {
    SURVIVAL(0, "survival"),
    CREATIVE(1, "creative"),
    ADVENTURE(2, "adventure"),
    SPECTATOR(3, "spectator");

    public static final GameType DEFAULT_MODE = SURVIVAL;

    private final int id;
    private final String name;

    GameType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isBlockPlacingRestricted() {
        return this == ADVENTURE || this == SPECTATOR;
    }

    public boolean isCreative() {
        return this == CREATIVE;
    }

    public boolean isSurvival() {
        return this == SURVIVAL || this == ADVENTURE;
    }

    /** The mode with {@code id}; survival for one there isn't. */
    public static GameType byId(int id) {
        for (GameType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return DEFAULT_MODE;
    }

    public static GameType byName(String name) {
        return byName(name, SURVIVAL);
    }

    public static GameType byName(String name, GameType fallback) {
        for (GameType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return fallback;
    }

    public static boolean isValidId(int id) {
        return id >= 0 && id <= 3;
    }
}
