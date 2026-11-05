package net.minecraft.world.entity.player;

/** Kiln's stand-in for {@code Abilities}: what the player may do (fly, build, take damage), as the game said. */
public class Abilities {
    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    private float flyingSpeed = 0.05F;
    private float walkingSpeed = 0.1F;

    public float getFlyingSpeed() {
        return flyingSpeed;
    }

    public void setFlyingSpeed(float speed) {
        flyingSpeed = speed;
    }

    public float getWalkingSpeed() {
        return walkingSpeed;
    }

    public void setWalkingSpeed(float speed) {
        walkingSpeed = speed;
    }
}
