package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;

/**
 * Kiln's stand-in for {@code ObjectiveArgument}: a scoreboard objective by name. The scoreboard is Horizon's, which
 * Kiln's Java can't read yet: a command that looks one up hears the game's "unknown objective" (logged as such).
 */
public class ObjectiveArgument implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "*", "012");
    private static final DynamicCommandExceptionType ERROR_OBJECTIVE_NOT_FOUND =
            new DynamicCommandExceptionType(name -> Component.translatable("arguments.objective.notFound", name));

    public static ObjectiveArgument objective() {
        return new ObjectiveArgument();
    }

    public static Objective getObjective(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        String objective = context.getArgument(name, String.class);
        kiln.Native.log("a mod's command looked up the scoreboard objective " + objective
                + ": Horizon's scoreboard isn't Java's yet");
        throw ERROR_OBJECTIVE_NOT_FOUND.create(objective);
    }

    public static Objective getWritableObjective(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return getObjective(context, name);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readUnquotedString();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
