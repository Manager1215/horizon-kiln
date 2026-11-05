package net.minecraft.world.food;

/** Kiln's stand-in for {@code FoodData}: the player's hunger bar, as the game said. */
public class FoodData {
    private int foodLevel = 20;
    private float saturationLevel = 5.0F;

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturationLevel() {
        return saturationLevel;
    }

    public boolean needsFood() {
        return foodLevel < 20;
    }

    /** Kiln's own: the hunger bar the game said. */
    public void kiln$set(int food, float saturation) {
        foodLevel = food;
        saturationLevel = saturation;
    }
}
