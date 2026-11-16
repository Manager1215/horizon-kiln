package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code WorldCoordinate}: one coordinate, a number or {@code ~} (relative) with an offset. */
public record WorldCoordinate(boolean relative, double value) {
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE =
            new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT =
            new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));

    /** The coordinate, from {@code origin} when relative. */
    public double get(double origin) {
        return relative ? value + origin : value;
    }

    /** A decimal coordinate; an absolute whole one gets {@code .5} when {@code centerCorrect} (a block's middle). */
    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        }
        if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        }
        boolean relative = isRelative(reader);
        int start = reader.getCursor();
        double value = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        String typed = reader.getString().substring(start, reader.getCursor());
        if (relative && typed.isEmpty()) {
            return new WorldCoordinate(true, 0.0);
        }
        if (!typed.contains(".") && !relative && centerCorrect) {
            value += 0.5;
        }
        return new WorldCoordinate(relative, value);
    }

    /** A whole coordinate (a block's), absolute or relative. */
    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        }
        if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        }
        boolean relative = isRelative(reader);
        double value;
        if (reader.canRead() && reader.peek() != ' ') {
            value = relative ? reader.readDouble() : reader.readInt();
        } else {
            value = 0.0;
        }
        return new WorldCoordinate(relative, value);
    }

    /** Whether the coordinate is relative ({@code ~}), which it reads past. */
    public static boolean isRelative(StringReader reader) {
        boolean relative = reader.peek() == '~';
        if (relative) {
            reader.skip();
        }
        return relative;
    }

    public boolean isRelative() {
        return relative;
    }
}
