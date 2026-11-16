package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Kiln's stand-in for {@code EntityArgument}: an argument naming entities, a player's name or UUID or a selector
 * ({@link EntitySelector}). Horizon's own tree parses it in the chat box as the game's.
 */
public class EntityArgument implements ArgumentType<EntitySelector> {
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY =
            new SimpleCommandExceptionType(Component.translatable("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED =
            new SimpleCommandExceptionType(Component.translatable("argument.player.entities"));
    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND =
            new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND =
            new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.player"));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED =
            new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.not_allowed"));
    private static final Collection<String> EXAMPLES = List.of("Player", "0123", "@e", "@e[type=foo]",
            "dd12be42-52a9-4a91-a8a1-11c01849e498");

    private final boolean single;
    private final boolean playersOnly;

    protected EntityArgument(boolean single, boolean playersOnly) {
        this.single = single;
        this.playersOnly = playersOnly;
    }

    public static EntityArgument entity() {
        return new EntityArgument(true, false);
    }

    public static EntityArgument entities() {
        return new EntityArgument(false, false);
    }

    public static EntityArgument player() {
        return new EntityArgument(true, true);
    }

    public static EntityArgument players() {
        return new EntityArgument(false, true);
    }

    public static Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findSingleEntity(context.getSource());
    }

    public static Collection<? extends Entity> getEntities(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        Collection<? extends Entity> found = getOptionalEntities(context, name);
        if (found.isEmpty()) {
            throw NO_ENTITIES_FOUND.create();
        }
        return found;
    }

    public static Collection<? extends Entity> getOptionalEntities(CommandContext<CommandSourceStack> context,
            String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findEntities(context.getSource());
    }

    public static Collection<ServerPlayer> getOptionalPlayers(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findPlayers(context.getSource());
    }

    public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findSinglePlayer(context.getSource());
    }

    public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        List<ServerPlayer> found = context.getArgument(name, EntitySelector.class).findPlayers(context.getSource());
        if (found.isEmpty()) {
            throw NO_PLAYERS_FOUND.create();
        }
        return found;
    }

    /** A selector ({@code @p}, its filters in brackets read past) or a name or UUID. */
    @Override
    public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        EntitySelector selector;
        if (reader.canRead() && reader.peek() == '@') {
            reader.skip();
            if (!reader.canRead() || "parsen".indexOf(reader.peek()) < 0) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
            }
            char kind = reader.read();
            if (reader.canRead() && reader.peek() == '[') {
                int depth = 0;
                do {
                    char c = reader.read();
                    depth += c == '[' ? 1 : c == ']' ? -1 : 0;
                } while (depth > 0 && reader.canRead());
            }
            boolean one = kind == 'p' || kind == 'r' || kind == 's' || kind == 'n';
            selector = new EntitySelector(kind, "", one ? 1 : EntitySelector.INFINITE, kind == 'e' || kind == 'n');
            if (single && !one) {
                reader.setCursor(start);
                throw (playersOnly ? ERROR_NOT_SINGLE_PLAYER : ERROR_NOT_SINGLE_ENTITY).createWithContext(reader);
            }
        } else {
            // A name or a UUID: up to the next space, as the game reads one.
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            String name = reader.getString().substring(start, reader.getCursor());
            if (name.isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, "name");
            }
            selector = new EntitySelector((char) 0, name, 1, false);
        }
        return selector;
    }

    public <S> EntitySelector parse(StringReader reader, S source) throws CommandSyntaxException {
        return parse(reader);
    }

    /** The players' names, then the selectors. */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>();
        if (context.getSource() instanceof SharedSuggestionProvider provider) {
            names.addAll(provider.getOnlinePlayerNames());
        }
        names.addAll(List.of("@p", "@a", "@r", "@s"));
        if (!playersOnly) {
            names.addAll(List.of("@e", "@n"));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
