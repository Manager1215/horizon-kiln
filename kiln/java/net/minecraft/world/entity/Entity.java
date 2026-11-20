package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code Entity}: an entity as the game said it was when it called the mod. */
public abstract class Entity implements net.fabricmc.fabric.api.attachment.v1.AttachmentTarget {
    protected UUID uuid = new UUID(0, 0);
    protected String name = "";
    protected Level level;
    protected double x;
    protected double y;
    protected double z;

    public Entity() {}

    /** The game's constructor, as the mods' entities call it: the entity is in the level; its type is the mod's. */
    public Entity(EntityType<?> type, Level level) {
        this.level = level;
    }

    public Level level() {
        return level;
    }

    /** The registries its level has: Kiln's (D-112), as every level's. */
    public net.minecraft.core.RegistryAccess registryAccess() {
        return level().registryAccess();
    }

    /**
     * The entity's tick. The game ticks its entities itself; Kiln ticks the window's player only, with the client's
     * level tick ({@code Minecraft.tick}), down through the classes it is ({@code LocalPlayer}, {@code Player},
     * {@code LivingEntity}, {@code Entity}), so what the mods' mixins put in each runs (D-093).
     */
    public void tick() {}

    /** Gone from the client's level: a mob the game no longer has near the player (D-211). */
    private boolean removed;

    /** Kiln lists only the entities the game has: one is removed when the game no longer has it. */
    public boolean isRemoved() {
        return removed;
    }

    /** Kiln's own: the game's mob gone from the client's level (D-211). */
    public void kiln$removed() {
        removed = true;
    }

    /** Kiln's own: where the game says it is (a mob of the client's level, D-211). */
    public void kiln$at(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Its box: its size (its type's) about where it stands, as vanilla's {@code makeBoundingBox}. */
    public net.minecraft.world.phys.AABB getBoundingBox() {
        EntityDimensions size = getDimensions(Pose.STANDING);
        double half = size.width() / 2.0;
        return new net.minecraft.world.phys.AABB(x - half, y, z - half, x + half, y + size.height(), z + half);
    }

    /** Whether it is still in the world: not removed. */
    public boolean isAlive() {
        return !isRemoved();
    }

    /** Its kind: Kiln names the player's ({@link EntityType#PLAYER}); another's is said once in Kiln's log. */
    public EntityType<?> getType() {
        kiln.Missing.called("the kinds of entities other than the player");
        return null;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getStringUUID() {
        return uuid.toString();
    }

    public Component getName() {
        return Component.literal(name);
    }

    public String getScoreboardName() {
        return name;
    }

    /** Its own random numbers (a sound's pitch, a particle's spread), as the game gives each entity. */
    private net.minecraft.util.RandomSource random;

    public net.minecraft.util.RandomSource getRandom() {
        if (random == null) {
            random = net.minecraft.util.RandomSource.create();
        }
        return random;
    }

    /** Its name as chat shows an entity: its name (vanilla's team colours and entity hover aren't Kiln's yet). */
    public Component getDisplayName() {
        return getName();
    }

    /** Its name as plain text. */
    public String getPlainTextName() {
        return getName().getString();
    }

    public final double getX() {
        return x;
    }

    public final double getY() {
        return y;
    }

    public final double getZ() {
        return z;
    }

    public BlockPos blockPosition() {
        return BlockPos.containing(x, y, z);
    }

    /** Kiln's own entity numbers, as the game's are per entity: each entity Kiln makes gets the next. */
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger(1);
    private int id = NEXT_ID.getAndIncrement();
    /** Motion in blocks a tick, rotation in degrees, and what it touches, as the game said. */
    protected net.minecraft.world.phys.Vec3 deltaMovement = net.minecraft.world.phys.Vec3.ZERO;
    protected float yRot;
    protected float xRot;
    protected boolean onGround;
    protected boolean wasTouchingWater;
    protected boolean sprinting;
    /** It ran into a wall this tick, as the game said (the window's player's). */
    public boolean horizontalCollision;
    /** The wall was only brushed, not run into (a sprint goes on through it), as the game said. */
    public boolean minorHorizontalCollision;
    /** Its motion is to be sent to the players (the game's server sends it; Horizon's keeps its own motion yet). */
    public boolean syncVelocity;

    public int getId() {
        return id;
    }

    /** A number of the mod's own (Just Enough Breeding gives its previews negative ones, D-173). */
    public void setId(int id) {
        this.id = id;
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
    }

    /** Its size: its type's, as the game's {@code entity-types.json} has it (D-173); a player's size without one. */
    public EntityDimensions getDimensions(Pose pose) {
        EntityType<?> type = getType();
        return type != null ? type.getDimensions() : EntityDimensions.scalable(0.6F, 1.8F);
    }

    public final float getBbWidth() {
        return getDimensions(Pose.STANDING).width();
    }

    public final float getBbHeight() {
        return getDimensions(Pose.STANDING).height();
    }

    /**
     * Saves what Kiln knows of it: its place, motion, rotation and UUID. The game keeps the rest of an entity's data
     * itself, under its own names.
     */
    public void saveWithoutId(net.minecraft.world.level.storage.ValueOutput out) {
        out.putDouble("kiln:x", x);
        out.putDouble("kiln:y", y);
        out.putDouble("kiln:z", z);
        out.putFloat("kiln:yaw", yRot);
        out.putFloat("kiln:pitch", xRot);
        out.putBoolean("OnGround", onGround);
        out.putString("kiln:uuid", uuid.toString());
    }

    /** Reads what {@link #saveWithoutId} wrote; what isn't there stays as it is. */
    public void load(net.minecraft.world.level.storage.ValueInput in) {
        x = in.getDoubleOr("kiln:x", x);
        y = in.getDoubleOr("kiln:y", y);
        z = in.getDoubleOr("kiln:z", z);
        yRot = in.getFloatOr("kiln:yaw", yRot);
        xRot = in.getFloatOr("kiln:pitch", xRot);
        onGround = in.getBooleanOr("OnGround", onGround);
        in.getString("kiln:uuid").ifPresent(u -> uuid = UUID.fromString(u));
    }

    public net.minecraft.world.phys.Vec3 position() {
        return new net.minecraft.world.phys.Vec3(x, y, z);
    }

    /** Its motion pushed by {@code delta} (a mod's launch): in Java; Horizon's entity keeps its own motion yet. */
    public void addDeltaMovement(net.minecraft.world.phys.Vec3 delta) {
        deltaMovement = new net.minecraft.world.phys.Vec3(deltaMovement.x + delta.x, deltaMovement.y + delta.y,
                deltaMovement.z + delta.z);
    }

    /** Its motion set (a mod's launch): in Java, and to the game when the mod sends it (D-187). */
    public void setDeltaMovement(net.minecraft.world.phys.Vec3 movement) {
        deltaMovement = movement;
    }

    public void setDeltaMovement(double x, double y, double z) {
        deltaMovement = new net.minecraft.world.phys.Vec3(x, y, z);
    }

    public net.minecraft.world.phys.Vec3 getDeltaMovement() {
        return deltaMovement;
    }

    public float getYRot() {
        return yRot;
    }

    /** The horizontal direction it faces, from its yaw (a machine placed by a player turns its front to them). */
    public net.minecraft.core.Direction getDirection() {
        return net.minecraft.core.Direction.fromYRot(getYRot());
    }

    public float getXRot() {
        return xRot;
    }

    /** The way it looks, a vector of length 1: from its pitch and yaw, as vanilla's {@code calculateViewVector}. */
    public net.minecraft.world.phys.Vec3 getLookAngle() {
        return calculateViewVector(xRot, yRot);
    }

    /** A pitch and yaw in degrees as the vector of length 1 they look along, as vanilla's. */
    public static net.minecraft.world.phys.Vec3 calculateViewVector(float xRot, float yRot) {
        float pitch = xRot * ((float) Math.PI / 180.0F);
        float yaw = -yRot * ((float) Math.PI / 180.0F);
        float cos = net.minecraft.util.Mth.cos(pitch);
        return new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.sin(yaw) * cos,
                -net.minecraft.util.Mth.sin(pitch), net.minecraft.util.Mth.cos(yaw) * cos);
    }

    /**
     * The way it looks at a moment within the tick. Kiln keeps the rotation the game said with the tick, not the last
     * tick's, so every moment is the tick's own (as vanilla's at {@code partialTick} 1).
     */
    public net.minecraft.world.phys.Vec3 getViewVector(float partialTick) {
        return calculateViewVector(getViewXRot(partialTick), getViewYRot(partialTick));
    }

    public float getViewXRot(float partialTick) {
        return getXRot();
    }

    public float getViewYRot(float partialTick) {
        return getYRot();
    }

    /** Of the six directions, the one nearest the way it looks (a coordinates mod's "facing"). */
    public net.minecraft.core.Direction getNearestViewDirection() {
        return net.minecraft.core.Direction.getApproximateNearest(getViewVector(1.0F));
    }

    /** The head's yaw: an entity that isn't living has no head of its own, as vanilla's. */
    public float getYHeadRot() {
        return 0.0F;
    }

    /**
     * The yaw a mod sets (a key that snaps the camera, a smooth turn). On the window's player it goes back to the game
     * as well, which turns the player and the camera ({@link #kiln$turned}); a yaw that isn't a number is left out,
     * as vanilla's.
     */
    public void setYRot(float yRot) {
        if (Float.isFinite(yRot)) {
            this.yRot = yRot;
            kiln$turned();
        }
    }

    /** The pitch a mod sets, as {@link #setYRot}. */
    public void setXRot(float xRot) {
        if (Float.isFinite(xRot)) {
            this.xRot = xRot;
            kiln$turned();
        }
    }

    /** Kiln's own: a mod set the rotation; only the window's player tells the game ({@code LocalPlayer}). */
    protected void kiln$turned() {}

    public boolean onGround() {
        return onGround;
    }

    public boolean isInWater() {
        return wasTouchingWater;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    /** Where its eyes are above its feet: a player's 1.62 blocks, 1.27 while sneaking. */
    public float getEyeHeight() {
        return 0.0F;
    }

    public net.minecraft.world.phys.Vec3 getEyePosition() {
        return new net.minecraft.world.phys.Vec3(x, y + getEyeHeight(), z);
    }

    /** Kiln's own: something the mod's code changed, for the game to do (on the server; nothing on a client). */
    protected void kiln$effect(String effect) {}
    /** Holding Shift (sneaking), as the game said. */
    protected boolean shiftKeyDown;

    public boolean isShiftKeyDown() {
        return shiftKeyDown;
    }

    public void setShiftKeyDown(boolean down) {
        shiftKeyDown = down;
    }

    /** The game's: what the entity tells when it moves or leaves its level (Horizon keeps the level's entities). */
    public void setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback callback) {
        levelCallback = callback;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void igniteForSeconds(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.igniteForSeconds"); }
    public void clearFire() { kiln.Missing.called("net.minecraft.world.entity.Entity.clearFire"); }
    public void setOnGround(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setOnGround"); }
    public boolean isUnderWater() { kiln.Missing.called("net.minecraft.world.entity.Entity.isUnderWater"); return false; }
    public void snapTo(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.snapTo"); }
    public double distanceToSqr(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.distanceToSqr"); return 0.0; }
    public void push(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.push"); }
    public boolean hurtServer(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.damagesource.DamageSource p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.hurtServer"); return false; }
    public net.minecraft.world.phys.HitResult pick(double p0, float p1, boolean p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.pick"); return null; }
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.item.ItemStack p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.spawnAtLocation"); return null; }
    public boolean isOnFire() { kiln.Missing.called("net.minecraft.world.entity.Entity.isOnFire"); return false; }
    public boolean isSteppingCarefully() { kiln.Missing.called("net.minecraft.world.entity.Entity.isSteppingCarefully"); return false; }
    public boolean isInvisible() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInvisible"); return false; }
    public void setInvisible(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setInvisible"); }
    public net.minecraft.world.entity.Entity teleport(net.minecraft.world.level.portal.TeleportTransition p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.teleport"); return this; }
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.startSeenByPlayer"); }
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.stopSeenByPlayer"); }
    public static enum RemovalReason {
        KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION;
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public boolean shouldDestroy() { kiln.Missing.called("net.minecraft.world.entity.Entity.RemovalReason.shouldDestroy"); return false; }
        // End of what tools/kiln-stubs.py generated.
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean blocksBuilding;
    private com.google.common.collect.ImmutableList<net.minecraft.world.entity.Entity> passengers;
    public double xo;
    public double yo;
    public double zo;
    private net.minecraft.world.phys.Vec3 position;
    public float yRotO;
    public float xRotO;
    public boolean verticalCollisionBelow;
    public float moveDist;
    public float flyDist;
    public double fallDistance;
    public double xOld;
    public double yOld;
    public double zOld;
    public boolean noPhysics;
    public int tickCount;
    protected boolean wasEyeInWater;
    protected boolean firstTick;
    public net.minecraft.world.level.entity.EntityInLevelCallback levelCallback;
    public boolean needsSync;
    public net.minecraft.world.entity.PortalProcessor portalProcess;
    public net.minecraft.world.entity.EntityDimensions dimensions;
    public float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public boolean isColliding(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.isColliding"); return false; }
    public int getTeamColor() { kiln.Missing.called("net.minecraft.world.entity.Entity.getTeamColor"); return 0; }
    public boolean isSpectator() { kiln.Missing.called("net.minecraft.world.entity.Entity.isSpectator"); return false; }
    public void syncPacketPositionCodec(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.syncPacketPositionCodec"); }
    public net.minecraft.network.protocol.game.VecDeltaCodec getPositionCodec() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPositionCodec"); return null; }
    public net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>> typeHolder() { kiln.Missing.called("net.minecraft.world.entity.Entity.typeHolder"); return null; }
    public java.util.Set<java.lang.String> entityTags() { kiln.Missing.called("net.minecraft.world.entity.Entity.entityTags"); return null; }
    public boolean addTag(java.lang.String p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.addTag"); return false; }
    public boolean removeTag(java.lang.String p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.removeTag"); return false; }
    public void kill(net.minecraft.server.level.ServerLevel p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.kill"); }
    public void discard() { kiln.Missing.called("net.minecraft.world.entity.Entity.discard"); }
    public net.minecraft.network.syncher.SynchedEntityData getEntityData() { kiln.Missing.called("net.minecraft.world.entity.Entity.getEntityData"); return null; }
    public void remove(net.minecraft.world.entity.Entity.RemovalReason p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.remove"); }
    public void setPose(net.minecraft.world.entity.Pose p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPose"); }
    public net.minecraft.world.entity.Pose getPose() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPose"); return null; }
    public boolean hasPose(net.minecraft.world.entity.Pose p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.hasPose"); return false; }
    public boolean closerThan(net.minecraft.world.entity.Entity p0, double p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.closerThan"); return false; }
    public void setRot(float p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.setRot"); }
    public void setPos(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPos"); }
    public void setPos(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPos"); }
    protected net.minecraft.world.phys.AABB makeBoundingBox() { kiln.Missing.called("net.minecraft.world.entity.Entity.makeBoundingBox"); return null; }
    public void turn(double p0, double p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.turn"); }
    public void baseTick() { kiln.Missing.called("net.minecraft.world.entity.Entity.baseTick"); }
    public void setPortalCooldown(int p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPortalCooldown"); }
    public boolean isOnPortalCooldown() { kiln.Missing.called("net.minecraft.world.entity.Entity.isOnPortalCooldown"); return false; }
    public void setRemainingFireTicks(int p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setRemainingFireTicks"); }
    public int getRemainingFireTicks() { kiln.Missing.called("net.minecraft.world.entity.Entity.getRemainingFireTicks"); return 0; }
    public void move(net.minecraft.world.entity.MoverType p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.move"); }
    protected void playEntityOnFireExtinguishedSound() { kiln.Missing.called("net.minecraft.world.entity.Entity.playEntityOnFireExtinguishedSound"); }
    public void extinguishFire() { kiln.Missing.called("net.minecraft.world.entity.Entity.extinguishFire"); }
    public net.minecraft.core.BlockPos getBlockPosBelowThatAffectsMyMovement() { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockPosBelowThatAffectsMyMovement"); return null; }
    public net.minecraft.core.BlockPos getOnPos() { kiln.Missing.called("net.minecraft.world.entity.Entity.getOnPos"); return null; }
    public static net.minecraft.world.phys.Vec3 collideBoundingBox(net.minecraft.world.entity.Entity p0, net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.AABB p2, net.minecraft.world.level.Level p3, java.util.List<net.minecraft.world.phys.shapes.VoxelShape> p4) { kiln.Missing.called("net.minecraft.world.entity.Entity.collideBoundingBox"); return null; }
    public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> p0, net.minecraft.world.entity.Entity p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.gameEvent"); }
    public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.gameEvent"); }
    protected void playStepSound(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.playStepSound"); }
    public void playSound(net.minecraft.sounds.SoundEvent p0, float p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.playSound"); }
    public void playSound(net.minecraft.sounds.SoundEvent p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.playSound"); }
    public boolean isSilent() { kiln.Missing.called("net.minecraft.world.entity.Entity.isSilent"); return false; }
    public void setSilent(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setSilent"); }
    public boolean isNoGravity() { kiln.Missing.called("net.minecraft.world.entity.Entity.isNoGravity"); return false; }
    public void setNoGravity(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setNoGravity"); }
    public double getGravity() { kiln.Missing.called("net.minecraft.world.entity.Entity.getGravity"); return 0.0; }
    protected void checkFallDamage(double p0, boolean p1, net.minecraft.world.level.block.state.BlockState p2, net.minecraft.core.BlockPos p3) { kiln.Missing.called("net.minecraft.world.entity.Entity.checkFallDamage"); }
    public boolean fireImmune() { kiln.Missing.called("net.minecraft.world.entity.Entity.fireImmune"); return false; }
    public boolean causeFallDamage(double p0, float p1, net.minecraft.world.damagesource.DamageSource p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.causeFallDamage"); return false; }
    public boolean isInWaterOrRain() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInWaterOrRain"); return false; }
    public boolean isInLiquid() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInLiquid"); return false; }
    public boolean isInShallowWater() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInShallowWater"); return false; }
    protected void doWaterSplashEffect() { kiln.Missing.called("net.minecraft.world.entity.Entity.doWaterSplashEffect"); }
    public net.minecraft.world.level.block.state.BlockState getBlockStateOn() { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockStateOn"); return null; }
    public boolean canSpawnSprintParticle() { kiln.Missing.called("net.minecraft.world.entity.Entity.canSpawnSprintParticle"); return false; }
    public boolean isEyeInFluid(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.isEyeInFluid"); return false; }
    public boolean isInLava() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInLava"); return false; }
    public void moveRelative(float p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.moveRelative"); }
    public float getLightLevelDependentMagicValue() { kiln.Missing.called("net.minecraft.world.entity.Entity.getLightLevelDependentMagicValue"); return 0.0F; }
    public void absSnapTo(double p0, double p1, double p2, float p3, float p4) { kiln.Missing.called("net.minecraft.world.entity.Entity.absSnapTo"); }
    public void absSnapRotationTo(float p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.absSnapRotationTo"); }
    public void absSnapTo(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.absSnapTo"); }
    public void snapTo(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.snapTo"); }
    public void snapTo(net.minecraft.core.BlockPos p0, float p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.snapTo"); }
    public void snapTo(net.minecraft.world.phys.Vec3 p0, float p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.snapTo"); }
    public void snapTo(double p0, double p1, double p2, float p3, float p4) { kiln.Missing.called("net.minecraft.world.entity.Entity.snapTo"); }
    public void setOldPosAndRot() { kiln.Missing.called("net.minecraft.world.entity.Entity.setOldPosAndRot"); }
    public void setOldRot() { kiln.Missing.called("net.minecraft.world.entity.Entity.setOldRot"); }
    public net.minecraft.world.phys.Vec3 oldPosition() { kiln.Missing.called("net.minecraft.world.entity.Entity.oldPosition"); return null; }
    public float distanceTo(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.distanceTo"); return 0.0F; }
    public double distanceToSqr(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.distanceToSqr"); return 0.0; }
    public double distanceToSqr(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.distanceToSqr"); return 0.0; }
    public void push(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.push"); }
    public void push(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.push"); }
    public void hurt(net.minecraft.world.damagesource.DamageSource p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.hurt"); }
    public boolean hurtOrSimulate(net.minecraft.world.damagesource.DamageSource p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.hurtOrSimulate"); return false; }
    public boolean hurtClient(net.minecraft.world.damagesource.DamageSource p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.hurtClient"); return false; }
    public float getXRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getXRot"); return 0.0F; }
    public float getYRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getYRot"); return 0.0F; }
    public net.minecraft.world.phys.Vec3 getEyePosition(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getEyePosition"); return null; }
    public net.minecraft.world.phys.Vec3 getLightProbePosition(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getLightProbePosition"); return null; }
    public net.minecraft.world.phys.Vec3 getPosition(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getPosition"); return null; }
    public boolean isPickable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isPickable"); return false; }
    public boolean isPushable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isPushable"); return false; }
    public boolean shouldRender(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.shouldRender"); return false; }
    public boolean shouldRenderAtSqrDistance(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.shouldRenderAtSqrDistance"); return false; }
    public boolean saveAsPassenger(net.minecraft.world.level.storage.ValueOutput p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.saveAsPassenger"); return false; }
    public boolean save(net.minecraft.world.level.storage.ValueOutput p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.save"); return false; }
    public java.lang.String getEncodeId() { kiln.Missing.called("net.minecraft.world.entity.Entity.getEncodeId"); return null; }
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.readAdditionalSaveData"); }
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.level.ItemLike p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.spawnAtLocation"); return null; }
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.item.ItemStack p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.spawnAtLocation"); return null; }
    public boolean isInWall() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInWall"); return false; }
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player p0, net.minecraft.world.InteractionHand p1, net.minecraft.world.phys.Vec3 p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.interact"); return null; }
    public boolean canCollideWith(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.canCollideWith"); return false; }
    public boolean canBeCollidedWith(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.canBeCollidedWith"); return false; }
    public void rideTick() { kiln.Missing.called("net.minecraft.world.entity.Entity.rideTick"); }
    public void positionRider(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.positionRider"); }
    protected void positionRider(net.minecraft.world.entity.Entity p0, net.minecraft.world.entity.Entity.MoveFunction p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.positionRider"); }
    public void onPassengerTurned(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.onPassengerTurned"); }
    public net.minecraft.world.phys.Vec3 getVehicleAttachmentPoint(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getVehicleAttachmentPoint"); return null; }
    public net.minecraft.world.phys.Vec3 getPassengerRidingPosition(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getPassengerRidingPosition"); return null; }
    public boolean startRiding(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.startRiding"); return false; }
    public boolean startRiding(net.minecraft.world.entity.Entity p0, boolean p1, boolean p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.startRiding"); return false; }
    protected boolean canRide(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.canRide"); return false; }
    public void ejectPassengers() { kiln.Missing.called("net.minecraft.world.entity.Entity.ejectPassengers"); }
    public void removeVehicle() { kiln.Missing.called("net.minecraft.world.entity.Entity.removeVehicle"); }
    public void stopRiding() { kiln.Missing.called("net.minecraft.world.entity.Entity.stopRiding"); }
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.canAddPassenger"); return false; }
    public boolean isInterpolating() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInterpolating"); return false; }
    public void moveOrInterpolateTo(net.minecraft.world.phys.Vec3 p0, float p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.moveOrInterpolateTo"); }
    public net.minecraft.world.entity.InterpolationHandler getInterpolation() { kiln.Missing.called("net.minecraft.world.entity.Entity.getInterpolation"); return null; }
    public float getPickRadius() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPickRadius"); return 0.0F; }
    public org.joml.Quaternionf getLookQuaternion() { kiln.Missing.called("net.minecraft.world.entity.Entity.getLookQuaternion"); return null; }
    public net.minecraft.world.phys.Vec2 getRotationVector() { kiln.Missing.called("net.minecraft.world.entity.Entity.getRotationVector"); return null; }
    public net.minecraft.world.phys.Vec3 getForward() { kiln.Missing.called("net.minecraft.world.entity.Entity.getForward"); return null; }
    public void setAsInsidePortal(net.minecraft.world.level.block.Portal p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.setAsInsidePortal"); }
    public void lerpMotion(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.lerpMotion"); }
    public void handleEntityEvent(byte p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.handleEntityEvent"); }
    public boolean isPassenger() { kiln.Missing.called("net.minecraft.world.entity.Entity.isPassenger"); return false; }
    public boolean isVehicle() { kiln.Missing.called("net.minecraft.world.entity.Entity.isVehicle"); return false; }
    public boolean isSuppressingBounce() { kiln.Missing.called("net.minecraft.world.entity.Entity.isSuppressingBounce"); return false; }
    public boolean isDiscrete() { kiln.Missing.called("net.minecraft.world.entity.Entity.isDiscrete"); return false; }
    public boolean isDescending() { kiln.Missing.called("net.minecraft.world.entity.Entity.isDescending"); return false; }
    public boolean isCrouching() { kiln.Missing.called("net.minecraft.world.entity.Entity.isCrouching"); return false; }
    public boolean isSwimming() { kiln.Missing.called("net.minecraft.world.entity.Entity.isSwimming"); return false; }
    public boolean isVisuallyCrawling() { kiln.Missing.called("net.minecraft.world.entity.Entity.isVisuallyCrawling"); return false; }
    public boolean hasGlowingTag() { kiln.Missing.called("net.minecraft.world.entity.Entity.hasGlowingTag"); return false; }
    public void setGlowingTag(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setGlowingTag"); }
    public boolean isInvisibleTo(net.minecraft.world.entity.player.Player p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.isInvisibleTo"); return false; }
    public net.minecraft.world.scores.PlayerTeam getTeam() { kiln.Missing.called("net.minecraft.world.entity.Entity.getTeam"); return null; }
    public boolean isAlliedTo(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.isAlliedTo"); return false; }
    public boolean considersEntityAsAlly(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.considersEntityAsAlly"); return false; }
    public void setSharedFlag(int p0, boolean p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.setSharedFlag"); }
    public int getMaxAirSupply() { kiln.Missing.called("net.minecraft.world.entity.Entity.getMaxAirSupply"); return 0; }
    public int getAirSupply() { kiln.Missing.called("net.minecraft.world.entity.Entity.getAirSupply"); return 0; }
    public void setAirSupply(int p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setAirSupply"); }
    public int getTicksFrozen() { kiln.Missing.called("net.minecraft.world.entity.Entity.getTicksFrozen"); return 0; }
    public void setTicksFrozen(int p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setTicksFrozen"); }
    public boolean isFullyFrozen() { kiln.Missing.called("net.minecraft.world.entity.Entity.isFullyFrozen"); return false; }
    public int getTicksRequiredToFreeze() { kiln.Missing.called("net.minecraft.world.entity.Entity.getTicksRequiredToFreeze"); return 0; }
    public void thunderHit(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.entity.LightningBolt p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.thunderHit"); }
    public void onAboveBubbleColumn(boolean p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.onAboveBubbleColumn"); }
    public void onInsideBubbleColumn(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.onInsideBubbleColumn"); }
    public boolean killedEntity(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.entity.LivingEntity p1, net.minecraft.world.damagesource.DamageSource p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.killedEntity"); return false; }
    public void resetFallDistance() { kiln.Missing.called("net.minecraft.world.entity.Entity.resetFallDistance"); }
    public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.makeStuckInBlock"); }
    protected net.minecraft.network.chat.Component getTypeName() { kiln.Missing.called("net.minecraft.world.entity.Entity.getTypeName"); return null; }
    public boolean is(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.is"); return false; }
    public void setYHeadRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setYHeadRot"); }
    public void setYBodyRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setYBodyRot"); }
    public boolean isAttackable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isAttackable"); return false; }
    public boolean isInvulnerable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isInvulnerable"); return false; }
    public boolean isPermanentlyInvulnerable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isPermanentlyInvulnerable"); return false; }
    public boolean isTemporarilyInvulnerable() { kiln.Missing.called("net.minecraft.world.entity.Entity.isTemporarilyInvulnerable"); return false; }
    public void setPermanentlyInvulnerable(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPermanentlyInvulnerable"); }
    public void copyPosition(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.copyPosition"); }
    public void restoreFrom(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.restoreFrom"); }
    public void placePortalTicket(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.placePortalTicket"); }
    public net.minecraft.world.phys.Vec3 getRelativePortalPosition(net.minecraft.core.Direction.Axis p0, net.minecraft.util.BlockUtil.FoundRectangle p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.getRelativePortalPosition"); return null; }
    public boolean canUsePortal(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.canUsePortal"); return false; }
    public float getBlockExplosionResistance(net.minecraft.world.level.Explosion p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, net.minecraft.world.level.block.state.BlockState p3, net.minecraft.world.level.material.FluidState p4, float p5) { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockExplosionResistance"); return 0.0F; }
    public boolean shouldBlockExplode(net.minecraft.world.level.Explosion p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, net.minecraft.world.level.block.state.BlockState p3, float p4) { kiln.Missing.called("net.minecraft.world.entity.Entity.shouldBlockExplode"); return false; }
    public boolean isIgnoringBlockTriggers() { kiln.Missing.called("net.minecraft.world.entity.Entity.isIgnoringBlockTriggers"); return false; }
    public void fillCrashReportCategory(net.minecraft.CrashReportCategory p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.fillCrashReportCategory"); }
    public boolean displayFireAnimation() { kiln.Missing.called("net.minecraft.world.entity.Entity.displayFireAnimation"); return false; }
    public boolean isPushedByFluid() { kiln.Missing.called("net.minecraft.world.entity.Entity.isPushedByFluid"); return false; }
    public static void setViewScale(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setViewScale"); }
    public void setCustomName(net.minecraft.network.chat.Component p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setCustomName"); }
    public net.minecraft.network.chat.Component getCustomName() { kiln.Missing.called("net.minecraft.world.entity.Entity.getCustomName"); return null; }
    public boolean hasCustomName() { kiln.Missing.called("net.minecraft.world.entity.Entity.hasCustomName"); return false; }
    public void setCustomNameVisible(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setCustomNameVisible"); }
    public boolean isCustomNameVisible() { kiln.Missing.called("net.minecraft.world.entity.Entity.isCustomNameVisible"); return false; }
    public net.minecraft.network.chat.Component belowNameDisplay() { kiln.Missing.called("net.minecraft.world.entity.Entity.belowNameDisplay"); return null; }
    public boolean teleportTo(net.minecraft.server.level.ServerLevel p0, double p1, double p2, double p3, java.util.Set<net.minecraft.world.entity.Relative> p4, float p5, float p6, boolean p7) { kiln.Missing.called("net.minecraft.world.entity.Entity.teleportTo"); return false; }
    public void teleportTo(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.teleportTo"); }
    public boolean shouldShowName() { kiln.Missing.called("net.minecraft.world.entity.Entity.shouldShowName"); return false; }
    public void refreshDimensions() { kiln.Missing.called("net.minecraft.world.entity.Entity.refreshDimensions"); }
    protected net.minecraft.network.chat.HoverEvent createHoverEvent() { kiln.Missing.called("net.minecraft.world.entity.Entity.createHoverEvent"); return null; }
    public void setBoundingBox(net.minecraft.world.phys.AABB p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setBoundingBox"); }
    public float getEyeHeight(net.minecraft.world.entity.Pose p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getEyeHeight"); return 0.0F; }
    public float rotate(net.minecraft.world.level.block.Rotation p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.rotate"); return 0.0F; }
    public float mirror(net.minecraft.world.level.block.Mirror p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.mirror"); return 0.0F; }
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() { kiln.Missing.called("net.minecraft.world.entity.Entity.getControllingPassenger"); return null; }
    public java.util.List<net.minecraft.world.entity.Entity> getPassengers() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPassengers"); return null; }
    public net.minecraft.world.entity.Entity getFirstPassenger() { kiln.Missing.called("net.minecraft.world.entity.Entity.getFirstPassenger"); return this; }
    public boolean hasPassenger(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.hasPassenger"); return false; }
    public boolean hasPassenger(java.util.function.Predicate<net.minecraft.world.entity.Entity> p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.hasPassenger"); return false; }
    public java.util.stream.Stream<net.minecraft.world.entity.Entity> getSelfAndPassengers() { kiln.Missing.called("net.minecraft.world.entity.Entity.getSelfAndPassengers"); return null; }
    public java.util.stream.Stream<net.minecraft.world.entity.Entity> getPassengersAndSelf() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPassengersAndSelf"); return null; }
    public net.minecraft.world.entity.Entity getRootVehicle() { kiln.Missing.called("net.minecraft.world.entity.Entity.getRootVehicle"); return this; }
    public boolean hasIndirectPassenger(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.hasIndirectPassenger"); return false; }
    public boolean isLocalInstanceAuthoritative() { kiln.Missing.called("net.minecraft.world.entity.Entity.isLocalInstanceAuthoritative"); return false; }
    public boolean isClientAuthoritative() { kiln.Missing.called("net.minecraft.world.entity.Entity.isClientAuthoritative"); return false; }
    public boolean canSimulateMovement() { kiln.Missing.called("net.minecraft.world.entity.Entity.canSimulateMovement"); return false; }
    public boolean isEffectiveAi() { kiln.Missing.called("net.minecraft.world.entity.Entity.isEffectiveAi"); return false; }
    protected static net.minecraft.world.phys.Vec3 getCollisionHorizontalEscapeVector(double p0, double p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.getCollisionHorizontalEscapeVector"); return null; }
    public net.minecraft.world.entity.Entity getVehicle() { kiln.Missing.called("net.minecraft.world.entity.Entity.getVehicle"); return this; }
    public net.minecraft.world.entity.Entity getControlledVehicle() { kiln.Missing.called("net.minecraft.world.entity.Entity.getControlledVehicle"); return this; }
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPistonPushReaction"); return null; }
    public net.minecraft.sounds.SoundSource getSoundSource() { kiln.Missing.called("net.minecraft.world.entity.Entity.getSoundSource"); return null; }
    public net.minecraft.commands.CommandSourceStack createCommandSourceStackForNameResolution(net.minecraft.server.level.ServerLevel p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.createCommandSourceStackForNameResolution"); return null; }
    public void lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.world.entity.Entity.lookAt"); }
    public float getPreciseBodyRotation(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getPreciseBodyRotation"); return 0.0F; }
    public boolean touchingUnloadedChunk() { kiln.Missing.called("net.minecraft.world.entity.Entity.touchingUnloadedChunk"); return false; }
    public double getFluidHeight(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getFluidHeight"); return 0.0; }
    public double getFluidJumpThreshold() { kiln.Missing.called("net.minecraft.world.entity.Entity.getFluidJumpThreshold"); return 0.0; }
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getAddEntityPacket"); return null; }
    public net.minecraft.world.entity.EntityAttachments getAttachments() { kiln.Missing.called("net.minecraft.world.entity.Entity.getAttachments"); return null; }
    public net.minecraft.world.phys.Vec3 trackingPosition() { kiln.Missing.called("net.minecraft.world.entity.Entity.trackingPosition"); return null; }
    public net.minecraft.world.level.block.state.BlockState getInBlockState() { kiln.Missing.called("net.minecraft.world.entity.Entity.getInBlockState"); return null; }
    public net.minecraft.world.level.ChunkPos chunkPosition() { kiln.Missing.called("net.minecraft.world.entity.Entity.chunkPosition"); return new net.minecraft.world.level.ChunkPos((int) 0, (int) 0); }
    public int getBlockX() { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockX"); return 0; }
    public double getX(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getX"); return 0.0; }
    public double getRandomX(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getRandomX"); return 0.0; }
    public int getBlockY() { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockY"); return 0; }
    public double getY(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getY"); return 0.0; }
    public double getRandomY() { kiln.Missing.called("net.minecraft.world.entity.Entity.getRandomY"); return 0.0; }
    public double getEyeY() { kiln.Missing.called("net.minecraft.world.entity.Entity.getEyeY"); return 0.0; }
    public int getBlockZ() { kiln.Missing.called("net.minecraft.world.entity.Entity.getBlockZ"); return 0; }
    public double getZ(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getZ"); return 0.0; }
    public double getRandomZ(double p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getRandomZ"); return 0.0; }
    public void setPosRaw(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.world.entity.Entity.setPosRaw"); }
    public net.minecraft.world.phys.Vec3 getRopeHoldPosition(float p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.getRopeHoldPosition"); return null; }
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.recreateFromPacket"); }
    public net.minecraft.world.item.ItemStack getPickResult() { kiln.Missing.called("net.minecraft.world.entity.Entity.getPickResult"); return null; }
    public void setIsInPowderSnow(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setIsInPowderSnow"); }
    public boolean canFreeze() { kiln.Missing.called("net.minecraft.world.entity.Entity.canFreeze"); return false; }
    public boolean isFreezing() { kiln.Missing.called("net.minecraft.world.entity.Entity.isFreezing"); return false; }
    public float getVisualRotationYInDegrees() { kiln.Missing.called("net.minecraft.world.entity.Entity.getVisualRotationYInDegrees"); return 0.0F; }
    public float maxUpStep() { kiln.Missing.called("net.minecraft.world.entity.Entity.maxUpStep"); return 0.0F; }
    public void onExplosionHit(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.onExplosionHit"); }
    public boolean isAlwaysTicking() { kiln.Missing.called("net.minecraft.world.entity.Entity.isAlwaysTicking"); return false; }
    public net.minecraft.world.damagesource.DamageSources damageSources() { kiln.Missing.called("net.minecraft.world.entity.Entity.damageSources"); return null; }
    public void lerpPositionAndRotationStep(int p0, double p1, double p2, double p3, double p4, double p5) { kiln.Missing.called("net.minecraft.world.entity.Entity.lerpPositionAndRotationStep"); }
    public net.minecraft.world.phys.Vec3 getKnownMovement() { kiln.Missing.called("net.minecraft.world.entity.Entity.getKnownMovement"); return null; }
    public java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> getLootTable() { kiln.Missing.called("net.minecraft.world.entity.Entity.getLootTable"); return java.util.Optional.empty(); }
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.applyImplicitComponents"); }
    public void applyComponentsFromItemStack(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.applyComponentsFromItemStack"); }
    public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.get"); return null; }
    public net.minecraft.util.ProblemReporter.PathElement problemPath() { kiln.Missing.called("net.minecraft.world.entity.Entity.problemPath"); return null; }
    public void setInvulnerableTime(int p0) { kiln.Missing.called("net.minecraft.world.entity.Entity.setInvulnerableTime"); }
    public int getInvulnerableTime() { kiln.Missing.called("net.minecraft.world.entity.Entity.getInvulnerableTime"); return 0; }
    public static record Movement(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to, java.util.Optional<net.minecraft.world.phys.Vec3> axisDependentOriginalMovement) {
        public Movement(net.minecraft.world.phys.Vec3 p0, net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.Vec3 p2) { this(p0, p1, (java.util.Optional) null); }
        public Movement(net.minecraft.world.phys.Vec3 p0, net.minecraft.world.phys.Vec3 p1) { this(p0, p1, (java.util.Optional) null); }
    }
    public static enum MovementEmission {
        NONE, SOUNDS, EVENTS, ALL;
    }
    public static interface MoveFunction {
        void accept(net.minecraft.world.entity.Entity p0, double p1, double p2, double p3);
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
