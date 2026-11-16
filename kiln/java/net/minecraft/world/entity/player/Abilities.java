package net.minecraft.world.entity.player;

/** Kiln's stand-in for {@code Abilities}: what the player may do (fly, build, take damage), as the game said. */
public class Abilities {
    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    /** The window's player's goes back to Horizon when a mod changes it (kiln.Back, D-105). */
    private final kiln.Back.Value<Float> flyingSpeed = new kiln.Back.Value<>("flying_speed", 0.05F);
    private float walkingSpeed = 0.1F;

    public float getFlyingSpeed() {
        return flyingSpeed.get();
    }

    public void setFlyingSpeed(float speed) {
        flyingSpeed.set(speed);
    }

    /** Kiln's own: the flying speed as the game said it. */
    public void kiln$gameFlyingSpeed(float speed) {
        flyingSpeed.game(speed);
    }

    /** Kiln's own: the flying speed, as Kiln sends it back when a mod changed it. */
    public kiln.Back.Value<Float> kiln$flyingSpeed() {
        return flyingSpeed;
    }

    public float getWalkingSpeed() {
        return walkingSpeed;
    }

    public void setWalkingSpeed(float speed) {
        walkingSpeed = speed;
    }
}
