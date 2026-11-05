package net.minecraft.client.sounds;

/** Kiln's stand-in for {@code SoundEngine}: Horizon plays the sounds; Kiln names what playing one gives back. */
public class SoundEngine {
    private SoundEngine() {}

    public enum PlayResult {
        STARTED,
        STARTED_SILENTLY,
        NOT_STARTED
    }
}
