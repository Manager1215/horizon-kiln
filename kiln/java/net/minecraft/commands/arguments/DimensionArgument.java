package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/** Kiln's stand-in for {@code DimensionArgument}: a dimension by id ({@code minecraft:the_nether}), as its level. */
public class DimensionArgument implements ArgumentType<Identifier> {
    private static final Collection<String> EXAMPLES = Arrays.asList("world", "nether");
    private static final Set<String> DIMENSIONS = Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private static final DynamicCommandExceptionType ERROR_INVALID_VALUE =
            new DynamicCommandExceptionType(id -> Component.translatable("argument.dimension.invalid", id));

    public static DimensionArgument dimension() {
        return new DimensionArgument();
    }

    @Override
    public Identifier parse(StringReader reader) throws CommandSyntaxException {
        return IdentifierArgument.read(reader);
    }

    /** The level of the dimension typed: the game's three (Horizon's worlds have no others). */
    public static ServerLevel getDimension(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        Identifier id = context.getArgument(name, Identifier.class);
        if (!DIMENSIONS.contains(id.toString())) {
            throw ERROR_INVALID_VALUE.create(id);
        }
        ServerLevel here = context.getSource().getLevel();
        if (here != null && here.dimension().identifier().equals(id)) {
            return here;
        }
        long time = here != null ? here.getGameTime() : 0L;
        return ServerLevel.kiln$of(id.toString(), time, here != null ? here.getDataStorage() : null);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
