package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code LocalCoordinates}: {@code ^left ^up ^forwards}, from where the source stands and looks, as
 * the game turns them.
 */
public record LocalCoordinates(double left, double up, double forwards) implements Coordinates {
    public static final char PREFIX_LOCAL_COORDINATE = '^';

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        return apply(source.getPosition(), source.getRotation());
    }

    /** {@code at} moved by these, as seen with {@code rotation} (x pitch, y yaw, in degrees). */
    public Vec3 apply(Vec3 at, Vec2 rotation) {
        float yawCos = (float) Math.cos(Math.toRadians(rotation.y + 90.0F));
        float yawSin = (float) Math.sin(Math.toRadians(rotation.y + 90.0F));
        float pitchCos = (float) Math.cos(Math.toRadians(-rotation.x));
        float pitchSin = (float) Math.sin(Math.toRadians(-rotation.x));
        float upCos = (float) Math.cos(Math.toRadians(-rotation.x + 90.0F));
        float upSin = (float) Math.sin(Math.toRadians(-rotation.x + 90.0F));
        double fx = yawCos * pitchCos, fy = pitchSin, fz = yawSin * pitchCos;
        double ux = yawCos * upCos, uy = upSin, uz = yawSin * upCos;
        // Left: forwards crossed with up, negated.
        double lx = -(fy * uz - fz * uy), ly = -(fz * ux - fx * uz), lz = -(fx * uy - fy * ux);
        return new Vec3(at.x + fx * forwards + ux * up + lx * left, at.y + fy * forwards + uy * up + ly * left,
                at.z + fz * forwards + uz * up + lz * left);
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        return Vec2.ZERO;
    }

    @Override
    public boolean isXRelative() {
        return true;
    }

    @Override
    public boolean isYRelative() {
        return true;
    }

    @Override
    public boolean isZRelative() {
        return true;
    }

    public static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        double left = read(reader, start);
        double up = separatedThen(reader, start);
        double forwards = separatedThen(reader, start);
        return new LocalCoordinates(left, up, forwards);
    }

    private static double separatedThen(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() != ' ') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
        reader.skip();
        return read(reader, start);
    }

    private static double read(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        }
        if (reader.peek() != PREFIX_LOCAL_COORDINATE) {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        }
        reader.skip();
        return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
    }
}
