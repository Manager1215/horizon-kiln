package net.minecraft.world.attribute;

/**
 * Kiln's stand-in for {@code EnvironmentAttribute}: something a dimension's environment says at a place and time (the
 * sun's angle), by its id.
 */
public class EnvironmentAttribute<Value> {
    private final String id;

    protected EnvironmentAttribute() {
        this("");
    }

    /** Kiln's own: the attribute {@code id} ({@code visual/sun_angle}). */
    public EnvironmentAttribute(String id) {
        this.id = id;
    }

    /** Kiln's own: its id. */
    public String kiln$id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}
