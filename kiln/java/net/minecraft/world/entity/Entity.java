package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code Entity}: an entity as the game said it was when it called the mod. */
public abstract class Entity {
    protected UUID uuid = new UUID(0, 0);
    protected String name = "";
    protected Level level;
    protected double x;
    protected double y;
    protected double z;

    public Level level() {
        return level;
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

    /** Kiln's own: something the mod's code changed, for the game to do (on the server; nothing on a client). */
    protected void kiln$effect(String effect) {}
}
