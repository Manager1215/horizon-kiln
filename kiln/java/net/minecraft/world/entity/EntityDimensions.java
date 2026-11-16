package net.minecraft.world.entity;

/**
 * Kiln's stand-in for {@code EntityDimensions}: an entity type's size, as the game's {@code entity-types.json} has it
 * (D-173). Kiln keeps no attachment points.
 */
public final class EntityDimensions {
    private final float width;
    private final float height;
    private final float eyeHeight;
    private final boolean fixed;

    private EntityDimensions(float width, float height, float eyeHeight, boolean fixed) {
        this.width = width;
        this.height = height;
        this.eyeHeight = eyeHeight;
        this.fixed = fixed;
    }

    public static EntityDimensions scalable(float width, float height) {
        return new EntityDimensions(width, height, height * 0.85F, false);
    }

    public static EntityDimensions fixed(float width, float height) {
        return new EntityDimensions(width, height, height * 0.85F, true);
    }

    /** Kiln's own: a size with the game's eye height. */
    public static EntityDimensions kiln$of(float width, float height, float eyeHeight) {
        return new EntityDimensions(width, height, eyeHeight, false);
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float eyeHeight() {
        return eyeHeight;
    }

    public boolean fixed() {
        return fixed;
    }

    public EntityDimensions scale(float factor) {
        return scale(factor, factor);
    }

    public EntityDimensions scale(float widthFactor, float heightFactor) {
        return fixed || widthFactor == 1.0F && heightFactor == 1.0F ? this
                : new EntityDimensions(width * widthFactor, height * heightFactor, eyeHeight * heightFactor, false);
    }

    public EntityDimensions withEyeHeight(float eyeHeight) {
        return new EntityDimensions(width, height, eyeHeight, fixed);
    }
}
