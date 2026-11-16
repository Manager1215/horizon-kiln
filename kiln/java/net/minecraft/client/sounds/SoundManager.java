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

    /** Every sound Horizon's game plays stops (vanilla's {@code stopsound} for all). */
    public void stop() {
        kiln.Back.act("stop_sound", "");
    }

    /** {@code sound} stops: Horizon stops what plays of its event (Kiln doesn't follow one instance). */
    public void stop(SoundInstance sound) {
        if (sound != null) {
            kiln.Back.act("stop_sound", sound.getIdentifier().toString());
        }
    }

    public void stop(net.minecraft.resources.Identifier id, net.minecraft.sounds.SoundSource source) {
        if (id == null) {
            stop();
        } else {
            kiln.Back.act("stop_sound", id.toString());
        }
    }

    /** The sound events the game knows: Kiln can't list Horizon's yet, so none. */
    public java.util.Collection<net.minecraft.resources.Identifier> getAvailableSounds() {
        kiln.Missing.called("the list of the game's sounds");
        return java.util.List.of();
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
