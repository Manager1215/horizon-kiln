package net.minecraft.client.resources.sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Kiln's stand-in for {@code SimpleSoundInstance}: a sound played once, at a place or as the UI's. */
public class SimpleSoundInstance extends AbstractSoundInstance {
    public SimpleSoundInstance(SoundEvent sound, SoundSource source, float volume, float pitch, RandomSource random,
            BlockPos pos) {
        this(sound, source, volume, pitch, random, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public SimpleSoundInstance(SoundEvent sound, SoundSource source, float volume, float pitch, RandomSource random,
            double x, double y, double z) {
        this(sound.location(), source, volume, pitch, random, false, 0, Attenuation.LINEAR, x, y, z, false);
    }

    public SimpleSoundInstance(Identifier identifier, SoundSource source, float volume, float pitch,
            RandomSource random, boolean looping, int delay, Attenuation attenuation, double x, double y, double z,
            boolean relative) {
        super(identifier, source, random);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = looping;
        this.delay = delay;
        this.attenuation = attenuation;
        this.relative = relative;
    }

    /** A UI sound (a button's click) at a quarter of its volume. */
    public static SimpleSoundInstance forUI(SoundEvent sound, float pitch) {
        return forUI(sound, pitch, 0.25F);
    }

    public static SimpleSoundInstance forUI(Holder<SoundEvent> sound, float pitch) {
        return forUI(sound.value(), pitch);
    }

    public static SimpleSoundInstance forUI(SoundEvent sound, float pitch, float volume) {
        return new SimpleSoundInstance(sound.location(), SoundSource.UI, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true);
    }

    /** A sound where the listener is, on a slider of the level's (an ambience). */
    public static SimpleSoundInstance forLocalAmbience(SoundEvent sound, float pitch, float volume) {
        return new SimpleSoundInstance(sound.location(), SoundSource.AMBIENT, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true);
    }
}
