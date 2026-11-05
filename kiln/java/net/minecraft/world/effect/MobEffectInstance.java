package net.minecraft.world.effect;

import java.util.Objects;
import net.minecraft.core.Holder;

/**
 * Kiln's stand-in for {@code MobEffectInstance}: an effect on an entity, how long it lasts (in ticks, -1 for ever),
 * its amplifier (0 is level I), whether it is ambient (a beacon's), shows its particles and its icon.
 */
public class MobEffectInstance implements Comparable<MobEffectInstance> {
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;

    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    private MobEffectInstance hiddenEffect;

    public MobEffectInstance(Holder<MobEffect> effect) {
        this(effect, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible,
            boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, null);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible,
            boolean showIcon, MobEffectInstance hiddenEffect) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Math.max(MIN_AMPLIFIER, Math.min(MAX_AMPLIFIER, amplifier));
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
    }

    public MobEffectInstance(MobEffectInstance other) {
        this(other.effect, other.duration, other.amplifier, other.ambient, other.visible, other.showIcon,
                other.hiddenEffect == null ? null : new MobEffectInstance(other.hiddenEffect));
    }

    public Holder<MobEffect> getEffect() {
        return effect;
    }

    public int getDuration() {
        return duration;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean showIcon() {
        return showIcon;
    }

    public boolean isInfiniteDuration() {
        return duration == INFINITE_DURATION;
    }

    /** Over within {@code ticks} (never, when it lasts for ever). */
    public boolean endsWithin(int ticks) {
        return !isInfiniteDuration() && duration <= ticks;
    }

    public boolean is(Holder<MobEffect> other) {
        return effect.equals(other) || effect.value() == other.value();
    }

    public String getDescriptionId() {
        return effect.value().getDescriptionId();
    }

    @Override
    public String toString() {
        String s = getDescriptionId() + (amplifier > 0 ? " x " + (amplifier + 1) : "") + ", Duration: "
                + (isInfiniteDuration() ? "infinite" : Integer.toString(duration));
        if (!visible) {
            s += ", Particles: false";
        }
        if (!showIcon) {
            s += ", Show Icon: false";
        }
        return s;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MobEffectInstance e && duration == e.duration && amplifier == e.amplifier
                && ambient == e.ambient && visible == e.visible && showIcon == e.showIcon && effect.value() == e.effect.value();
    }

    @Override
    public int hashCode() {
        return Objects.hash(effect.value(), duration, amplifier, ambient);
    }

    /** Ambient ones after the rest, then shorter before longer. */
    @Override
    public int compareTo(MobEffectInstance other) {
        int c = Boolean.compare(ambient, other.ambient);
        return c != 0 ? c : Integer.compare(duration, other.duration);
    }

    /** Kiln's own: as the game said with the tick (it keeps no icon flag of its own: the icon shows with the particles). */
    public void kiln$set(int duration, int amplifier, boolean ambient, boolean visible) {
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = visible;
    }
}
