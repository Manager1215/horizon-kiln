package net.minecraft.world.entity.ai.attributes;

/** Kiln's stand-in for {@code Attribute}: a living entity's value (maximum health, speed...) and its default. */
public class Attribute {
    private final String descriptionId;
    private final double defaultValue;

    protected Attribute(String descriptionId, double defaultValue) {
        this.descriptionId = descriptionId;
        this.defaultValue = defaultValue;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    public String getDescriptionId() {
        return descriptionId;
    }
}
