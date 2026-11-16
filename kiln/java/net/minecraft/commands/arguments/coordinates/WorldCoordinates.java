package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Kiln's stand-in for {@code WorldCoordinates}: x, y and z, each absolute or relative ({@code ~}) to the source. */
public record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) implements Coordinates {
    public static final WorldCoordinates ZERO_ROTATION =
            absolute(new Vec2(0.0F, 0.0F));

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec3 at = source.getPosition();
        return new Vec3(x.get(at.x), y.get(at.y), z.get(at.z));
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        return new Vec2((float) x.get(rotation.x), (float) y.get(rotation.y));
    }

    @Override
    public boolean isXRelative() {
        return x.isRelative();
    }

    @Override
    public boolean isYRelative() {
        return y.isRelative();
    }

    @Override
    public boolean isZRelative() {
        return z.isRelative();
    }

    /** Three whole coordinates, a block's position. */
    public static WorldCoordinates parseInt(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        WorldCoordinate x = WorldCoordinate.parseInt(reader);
        WorldCoordinate y = separatedThen(reader, start, () -> WorldCoordinate.parseInt(reader));
        WorldCoordinate z = separatedThen(reader, start, () -> WorldCoordinate.parseInt(reader));
        return new WorldCoordinates(x, y, z);
    }

    /** Three decimal coordinates; whole absolute x and z are a block's middle when {@code centerCorrect}. */
    public static WorldCoordinates parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        int start = reader.getCursor();
        WorldCoordinate x = WorldCoordinate.parseDouble(reader, centerCorrect);
        WorldCoordinate y = separatedThen(reader, start, () -> WorldCoordinate.parseDouble(reader, false));
        WorldCoordinate z = separatedThen(reader, start, () -> WorldCoordinate.parseDouble(reader, centerCorrect));
        return new WorldCoordinates(x, y, z);
    }

    private interface Next {
        WorldCoordinate read() throws CommandSyntaxException;
    }

    /** A space, then the next coordinate; vanilla's "incomplete" error from where the coordinates began otherwise. */
    private static WorldCoordinate separatedThen(StringReader reader, int start, Next next) throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() != ' ') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
        reader.skip();
        return next.read();
    }

    public static WorldCoordinates absolute(double x, double y, double z) {
        return new WorldCoordinates(new WorldCoordinate(false, x), new WorldCoordinate(false, y),
                new WorldCoordinate(false, z));
    }

    public static WorldCoordinates absolute(Vec2 rotation) {
        return new WorldCoordinates(new WorldCoordinate(false, rotation.x), new WorldCoordinate(false, rotation.y),
                new WorldCoordinate(true, 0.0));
    }

    /** All relative, at the source ({@code ~ ~ ~}). */
    public static WorldCoordinates current() {
        return new WorldCoordinates(new WorldCoordinate(true, 0.0), new WorldCoordinate(true, 0.0),
                new WorldCoordinate(true, 0.0));
    }
}
