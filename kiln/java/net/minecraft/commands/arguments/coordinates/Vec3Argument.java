package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code Vec3Argument}: a position as the player types it, {@code 1 64.5 -3}, {@code ~ ~1 ~} or
 * {@code ^ ^ ^2}, read as the game reads it.
 */
public class Vec3Argument implements ArgumentType<Coordinates> {
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE =
            new SimpleCommandExceptionType(Component.translatable("argument.pos3d.incomplete"));
    public static final SimpleCommandExceptionType ERROR_MIXED_TYPE =
            new SimpleCommandExceptionType(Component.translatable("argument.pos.mixed"));
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "0.1 -0.5 .9", "~0.5 ~1 ~-5");
    private final boolean centerCorrect;

    public Vec3Argument(boolean centerCorrect) {
        this.centerCorrect = centerCorrect;
    }

    public static Vec3Argument vec3() {
        return new Vec3Argument(true);
    }

    public static Vec3Argument vec3(boolean centerCorrect) {
        return new Vec3Argument(centerCorrect);
    }

    public static Vec3 getVec3(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Coordinates.class).getPosition(context.getSource());
    }

    public static Coordinates getCoordinates(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Coordinates.class);
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        return reader.canRead() && reader.peek() == LocalCoordinates.PREFIX_LOCAL_COORDINATE
                ? LocalCoordinates.parse(reader)
                : WorldCoordinates.parseDouble(reader, centerCorrect);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
