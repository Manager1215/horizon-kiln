package net.minecraft.sounds;

/** Kiln's stand-in for {@code SoundSource}: the volume slider a sound follows, in vanilla's order (Horizon's too). */
public enum SoundSource {
    MASTER("master"),
    MUSIC("music"),
    RECORDS("record"),
    WEATHER("weather"),
    BLOCKS("block"),
    HOSTILE("hostile"),
    NEUTRAL("neutral"),
    PLAYERS("player"),
    AMBIENT("ambient"),
    VOICE("voice"),
    UI("ui");

    private final String name;

    SoundSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
