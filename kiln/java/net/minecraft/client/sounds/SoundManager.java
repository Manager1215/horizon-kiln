package net.minecraft.client.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Kiln's stand-in for {@code SoundManager}: the game plays a mod's sounds, by their event's id, with the next tick
 * (Horizon's sliders and sound settings apply). Kiln doesn't follow a sound once it plays (looping, stopping one).
 */
public class SoundManager {
    public SoundEngine.PlayResult play(SoundInstance sound) {
        return start(sound) ? SoundEngine.PlayResult.STARTED : SoundEngine.PlayResult.NOT_STARTED;
    }

    /** {@code sound}, {@code delay} ticks later: Kiln plays it with the next tick, delay or not. */
    public void playDelayed(SoundInstance sound, int delay) {
        start(sound);
    }

    private static boolean start(SoundInstance sound) {
        if (sound == null || !sound.canPlaySound()) {
            return false;
        }
        if (sound.isLooping()) {
            kiln.Missing.called("a looping sound");
        }
        kiln.Native.sound(sound.getIdentifier().toString(), sound.isRelative() ? null
                : new double[] {sound.getX(), sound.getY(), sound.getZ()}, sound.getVolume(), sound.getPitch());
        return true;
    }
}
