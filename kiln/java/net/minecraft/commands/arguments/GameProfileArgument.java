package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * Kiln's stand-in for {@code GameProfileArgument}: players by name or selector ({@code Alex}, {@code @a}). A name is
 * the online player's; one who isn't online is unknown (the game would look the name up at Mojang's, Kiln doesn't).
 */
public class GameProfileArgument implements ArgumentType<GameProfileArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");
    public static final SimpleCommandExceptionType ERROR_UNKNOWN_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("argument.player.unknown"));

    public static Collection<NameAndId> getGameProfiles(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return context.getArgument(name, Result.class).getNames(context.getSource());
    }

    public static GameProfileArgument gameProfile() {
        return new GameProfileArgument();
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelector selector = EntityArgument.players().parse(reader);
            return source -> {
                List<NameAndId> found = new ArrayList<>();
                for (ServerPlayer player : selector.findPlayers(source)) {
                    found.add(player.nameAndId());
                }
                if (found.isEmpty()) {
                    throw EntityArgument.NO_PLAYERS_FOUND.create();
                }
                return found;
            };
        }
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        String typed = reader.getString().substring(start, reader.getCursor());
        return source -> {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                if (player.getScoreboardName().equalsIgnoreCase(typed) || player.getUUID().toString().equals(typed)) {
                    return List.of(player.nameAndId());
                }
            }
            throw ERROR_UNKNOWN_PLAYER.create();
        };
    }

    public <S> Result parse(StringReader reader, S source) throws CommandSyntaxException {
        return parse(reader);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /** The players the argument names, found when the command runs. */
    @FunctionalInterface
    public interface Result {
        Collection<NameAndId> getNames(CommandSourceStack source) throws CommandSyntaxException;
    }
}
