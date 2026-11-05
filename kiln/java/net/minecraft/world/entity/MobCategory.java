package net.minecraft.world.entity;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code MobCategory}: what kind of mob an entity type is, for spawning (the game spawns them). */
public enum MobCategory implements StringRepresentable {
    MONSTER,
    CREATURE,
    AMBIENT,
    AXOLOTLS,
    UNDERGROUND_WATER_CREATURE,
    WATER_CREATURE,
    WATER_AMBIENT,
    MISC;

    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return getName();
    }
}
