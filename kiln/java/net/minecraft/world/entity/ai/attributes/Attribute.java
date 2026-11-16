package net.minecraft.world.entity.ai.attributes;

import net.minecraft.ChatFormatting;

/**
 * Kiln's stand-in for {@code Attribute}: a living entity's value (maximum health, speed...), its default, and whether
 * more of it is good, as the game's (D-177): an item's tooltip shows a gain in blue and a loss in red, the other way
 * round for a bad one (burning time, fall damage), grey for a neutral one (gravity, scale).
 */
public class Attribute {
    private final String descriptionId;
    private final double defaultValue;
    private Sentiment sentiment = Sentiment.POSITIVE;

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

    public Attribute setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
        return this;
    }

    /** The colour of a tooltip line that raises it ({@code positive}) or lowers it. */
    public ChatFormatting getStyle(boolean positive) {
        return sentiment.getStyle(positive);
    }

    public static enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean positive) {
            return switch (this) {
                case POSITIVE -> positive ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> positive ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}
