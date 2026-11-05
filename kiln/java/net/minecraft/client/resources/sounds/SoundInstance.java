package net.minecraft.client.resources.sounds;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Kiln's stand-in for {@code SoundInstance}: a sound to play, by its event's id, where and how loud. */
public interface SoundInstance {
    Identifier getIdentifier();

    SoundSource getSource();

    boolean isLooping();

    /** Played where the listener is (the UI's sounds), not at its place in the level. */
    boolean isRelative();

    int getDelay();

    float getVolume();

    float getPitch();

    double getX();

    double getY();

    double getZ();

    Attenuation getAttenuation();

    default boolean canStartSilent() {
        return false;
    }

    default boolean canPlaySound() {
        return true;
    }

    static RandomSource createUnseededRandom() {
        return RandomSource.create();
    }

    enum Attenuation {
        NONE,
        LINEAR
    }
}
