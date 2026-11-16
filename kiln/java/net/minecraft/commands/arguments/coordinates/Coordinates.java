package net.minecraft.commands.arguments.coordinates;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Kiln's stand-in for {@code Coordinates}: a position a player typed, absolute or relative to the command's source. */
public interface Coordinates {
    Vec3 getPosition(CommandSourceStack source);

    Vec2 getRotation(CommandSourceStack source);

    default BlockPos getBlockPos(CommandSourceStack source) {
        Vec3 at = getPosition(source);
        return BlockPos.containing(at.x, at.y, at.z);
    }

    boolean isXRelative();

    boolean isYRelative();

    boolean isZRelative();
}
