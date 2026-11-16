package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code IdentifierArgument}: an id as the player types it ({@code minecraft:stone}, {@code stone}). */
public class IdentifierArgument implements ArgumentType<Identifier> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));

    public static IdentifierArgument id() {
        return new IdentifierArgument();
    }

    public static Identifier getId(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Identifier.class);
    }

    @Override
    public Identifier parse(StringReader reader) throws CommandSyntaxException {
        return read(reader);
    }

    /** The id at the reader, up to the first character an id can't hold, as the game reads one. */
    static Identifier read(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && allowed(reader.peek())) {
            reader.skip();
        }
        Identifier id = Identifier.tryParse(reader.getString().substring(start, reader.getCursor()));
        if (id == null) {
            reader.setCursor(start);
            throw ERROR_INVALID.createWithContext(reader);
        }
        return id;
    }

    private static boolean allowed(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
