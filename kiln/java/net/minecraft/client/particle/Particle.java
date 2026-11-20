package net.minecraft.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/**
 * Kiln's stand-in for {@code Particle}: a mod's particle, which lives, moves and ages as the game's does (its speed,
 * gravity and friction, removed at the end of its lifetime). Horizon draws the game's own particles; Kiln draws none
 * of a mod's, and a particle meets no blocks (it falls through them).
 */
public abstract class Particle {
    private static final double MAXIMUM_COLLISION_VELOCITY_SQUARED = 100.0 * 100.0;
    public final ClientLevel level;
    public double xo;
    public double yo;
    public double zo;
    public double x;
    public double y;
    public double z;
    public double xd;
    public double yd;
    public double zd;
    private AABB bb = new AABB(0, 0, 0, 0, 0, 0);
    public boolean onGround;
    public boolean hasPhysics = true;
    public boolean stoppedByCollision;
    public boolean removed;
    public float bbWidth = 0.6F;
    public float bbHeight = 1.8F;
    public final RandomSource random = RandomSource.create();
    public int age;
    public int lifetime;
    public float gravity;
    public float friction = 0.98F;
    public boolean speedUpWhenYMotionIsBlocked;

    protected Particle(ClientLevel level, double x, double y, double z) {
        this.level = level;
        setSize(0.2F, 0.2F);
        setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.lifetime = (int) (4.0F / (random.nextFloat() * 0.9F + 0.1F));
    }

    /** Placed at (x, y, z), moving at the speed given (Kiln adds none of the game's scatter). */
    public Particle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        this(level, x, y, z);
        setParticleSpeed(xd, yd, zd);
    }

    /** Kiln's own: a particle in no level (a generated subclass's). */
    protected Particle() {
        this(null, 0, 0, 0);
    }

    public Particle setPower(float power) {
        xd *= power;
        yd = (yd - 0.1) * power + 0.1;
        zd *= power;
        return this;
    }

    public void setParticleSpeed(double xd, double yd, double zd) {
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
    }

    public Particle scale(float scale) {
        setSize(0.2F * scale, 0.2F * scale);
        return this;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    public int getLifetime() {
        return lifetime;
    }

    /** A tick: older, removed at the end of its life; else it falls by its gravity and slows by its friction. */
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= 0.04 * gravity;
        move(xd, yd, zd);
        if (speedUpWhenYMotionIsBlocked && y == yo) {
            xd *= 1.1;
            zd *= 1.1;
        }
        xd *= friction;
        yd *= friction;
        zd *= friction;
        if (onGround) {
            xd *= 0.7;
            zd *= 0.7;
        }
    }

    /** How it is drawn: Kiln draws no particle of a mod's, so by default none. */
    public ParticleRenderType getGroup() {
        return ParticleRenderType.NO_RENDER;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ", Pos (" + x + "," + y + "," + z + "), Age " + age;
    }

    public void remove() {
        removed = true;
    }

    protected void setSize(float width, float height) {
        if (width != bbWidth || height != bbHeight) {
            bbWidth = width;
            bbHeight = height;
            AABB box = getBoundingBox();
            double middleX = (box.minX + box.maxX) / 2.0;
            double middleZ = (box.minZ + box.maxZ) / 2.0;
            setBoundingBox(new AABB(middleX - width / 2.0, box.minY, middleZ - width / 2.0, middleX + width / 2.0,
                    box.minY + height, middleZ + width / 2.0));
        }
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float half = bbWidth / 2.0F;
        setBoundingBox(new AABB(x - half, y, z - half, x + half, y + bbHeight, z + half));
    }

    /** Moves by (dx, dy, dz): it meets no blocks in Kiln. */
    public void move(double dx, double dy, double dz) {
        if (stoppedByCollision || dx * dx + dy * dy + dz * dz > MAXIMUM_COLLISION_VELOCITY_SQUARED) {
            return;
        }
        AABB box = getBoundingBox();
        setBoundingBox(new AABB(box.minX + dx, box.minY + dy, box.minZ + dz, box.maxX + dx, box.maxY + dy,
                box.maxZ + dz));
        setLocationFromBoundingbox();
        onGround = false;
    }

    protected void setLocationFromBoundingbox() {
        AABB box = getBoundingBox();
        x = (box.minX + box.maxX) / 2.0;
        y = box.minY;
        z = (box.minZ + box.maxZ) / 2.0;
    }

    /** Its light: Kiln reads no level light here, so full brightness. */
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    public boolean isAlive() {
        return !removed;
    }

    public AABB getBoundingBox() {
        return bb;
    }

    public void setBoundingBox(AABB box) {
        this.bb = box;
    }

    public Optional<ParticleLimit> getParticleLimit() {
        return Optional.empty();
    }

    /** How opaque a particle is over its life: from {@code startAlpha} to {@code endAlpha} between two points of it. */
    public record LifetimeAlpha(float startAlpha, float endAlpha, float startAtNormalizedAge, float endAtNormalizedAge) {
        public static final LifetimeAlpha ALWAYS_OPAQUE = new LifetimeAlpha(1.0F, 1.0F, 0.0F, 1.0F);

        public boolean isOpaque() {
            return startAlpha >= 1.0F && endAlpha >= 1.0F;
        }

        public float currentAlphaForAge(int age, int lifetime, float partialTick) {
            if (startAlpha == endAlpha) {
                return startAlpha;
            }
            float t = lifetime <= 0 ? 1.0F : (age + partialTick) / lifetime;
            float span = endAtNormalizedAge - startAtNormalizedAge;
            float along = span <= 0 ? (t >= endAtNormalizedAge ? 1.0F : 0.0F) : (t - startAtNormalizedAge) / span;
            along = Math.max(0.0F, Math.min(1.0F, along));
            return startAlpha + (endAlpha - startAlpha) * along;
        }
    }
}
