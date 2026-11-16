package net.minecraft.world.entity.projectile;

import net.minecraft.world.entity.player.Player;

/**
 * Kiln's stand-in for {@code FishingHook}: the window's player's bobber, as the game had it with the tick (Horizon
 * casts it, moves it and hooks the fish): where it is, and its motion over the last tick, which dips as a fish bites.
 */
public class FishingHook extends Projectile {
    private final Player owner;

    public FishingHook(Player owner) {
        super();
        this.owner = owner;
    }

    public Player getPlayerOwner() {
        return owner;
    }

    @Override
    public net.minecraft.world.entity.Entity getOwner() {
        return owner;
    }

    /** What it caught on: an entity Kiln's Java knows none of but the player, so none. */
    public net.minecraft.world.entity.Entity getHookedIn() {
        return null;
    }

    /** Kiln's own: where it is and its motion, as the game said. */
    public void kiln$set(double x, double y, double z, net.minecraft.world.phys.Vec3 motion) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.deltaMovement = motion;
    }
}
