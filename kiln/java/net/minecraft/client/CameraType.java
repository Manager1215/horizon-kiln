package net.minecraft.client;

/** Kiln's stand-in for {@code CameraType}: where the camera looks from (F5 cycles it), as vanilla's. */
public enum CameraType {
    FIRST_PERSON(true, false),
    THIRD_PERSON_BACK(false, false),
    THIRD_PERSON_FRONT(false, true);

    private static final CameraType[] VALUES = values();
    private final boolean firstPerson;
    private final boolean mirrored;

    CameraType(boolean firstPerson, boolean mirrored) {
        this.firstPerson = firstPerson;
        this.mirrored = mirrored;
    }

    public boolean isFirstPerson() {
        return firstPerson;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    /** The next one, as F5 goes: first person, behind, in front, first person again. */
    public CameraType cycle() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }
}
