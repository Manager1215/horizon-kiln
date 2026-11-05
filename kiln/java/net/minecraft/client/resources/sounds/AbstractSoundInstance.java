package net.minecraft.client.resources.sounds;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Kiln's stand-in for {@code AbstractSoundInstance}: a sound's id, slider, volume, pitch and place. */
public abstract class AbstractSoundInstance implements SoundInstance {
    protected final SoundSource source;
    protected final Identifier identifier;
    protected float volume = 1.0F;
    protected float pitch = 1.0F;
    protected double x;
    protected double y;
    protected double z;
    protected boolean looping;
    protected int delay;
    protected Attenuation attenuation = Attenuation.LINEAR;
    protected boolean relative;
    protected final RandomSource random;

    protected AbstractSoundInstance(SoundEvent sound, SoundSource source, RandomSource random) {
        this(sound.location(), source, random);
    }

    protected AbstractSoundInstance(Identifier identifier, SoundSource source, RandomSource random) {
        this.identifier = identifier;
        this.source = source;
        this.random = random;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public SoundSource getSource() {
        return source;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public Attenuation getAttenuation() {
        return attenuation;
    }

    @Override
    public boolean isRelative() {
        return relative;
    }

    @Override
    public String toString() {
        return "SoundInstance[" + identifier + "]";
    }
}
