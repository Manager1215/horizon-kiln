package kiln;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.BuiltInExceptions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * The Java mods' server commands on Horizon's server (D-107). Horizon's native mod API takes a command as usages, words
 * then typed arguments ({@code pay <player:player> <amount:integer>}), puts them in every player's command tree (the chat
 * box colours and completes them as the game's own) and hands a mod the line a player types for one of them, with the
 * player's operator level.
 * <ul>
 *   <li>{@code server_commands}: the mods register their commands ({@link Commands}, Fabric's {@code
 *       CommandRegistrationCallback}), and each Brigadier tree is told as usages, {@code usage <level> <usage>}
 *       effects: a usage for every node that runs, the level the lowest its requirements allow;
 *   <li>{@code server_command}: a player's line for one of them runs here, with Brigadier, as that player at their level;
 *       what it says back is {@code feedback} effects, in their chat after the command.
 * </ul>
 * Where a mod's tree can't be told as it is (an argument type Horizon has no kind for, a word Horizon's usages don't
 * take), the usage ends there with the rest of the line as text: Horizon hands the whole line all the same, and
 * Brigadier here parses it.
 */
public final class ServerCommands {
    /** A word Horizon takes in a usage. */
    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]+");
    /** The usage's ending for what can't be told: the rest of the line, optional. */
    private static final String REST = "[kiln_rest:text]";
    private static Commands commands;

    static {
        // The game's own wording of Brigadier's errors (vanilla's BrigadierExceptions), in the player's language.
        CommandSyntaxException.BUILT_IN_EXCEPTIONS = new BuiltInExceptions() {
            private final SimpleCommandExceptionType unknownCommand =
                    new SimpleCommandExceptionType(Component.translatable("command.unknown.command"));
            private final SimpleCommandExceptionType unknownArgument =
                    new SimpleCommandExceptionType(Component.translatable("command.unknown.argument"));
            private final SimpleCommandExceptionType expectedSeparator =
                    new SimpleCommandExceptionType(Component.translatable("command.expected.separator"));

            @Override
            public SimpleCommandExceptionType dispatcherUnknownCommand() {
                return unknownCommand;
            }

            @Override
            public SimpleCommandExceptionType dispatcherUnknownArgument() {
                return unknownArgument;
            }

            @Override
            public SimpleCommandExceptionType dispatcherExpectedArgumentSeparator() {
                return expectedSeparator;
            }
        };
    }

    private ServerCommands() {}

    /** The server's commands, made as the game makes them when none were yet. */
    public static Commands commands() {
        if (commands == null) {
            commands = new Commands(Commands.CommandSelection.INTEGRATED, CommandBuildContext.KILN);
        }
        return commands;
    }

    /**
     * The server's commands made anew (the mods register theirs), told as usages; {@code probe} is a player for the
     * requirements to be tried with, at each level.
     */
    static String register(ServerPlayer probe) {
        commands = new Commands(Commands.CommandSelection.INTEGRATED, CommandBuildContext.KILN);
        for (String usage : usages(commands.getDispatcher(), probe)) {
            Server.effect("usage " + usage);
        }
        return "pass";
    }

    /** Each runnable node of the tree as {@code <level> <usage>}, in order, once. */
    static List<String> usages(CommandDispatcher<CommandSourceStack> dispatcher, ServerPlayer probe) {
        Set<String> out = new LinkedHashSet<>();
        for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
            if (!(root instanceof LiteralCommandNode) || !WORD.matcher(root.getName()).matches()) {
                Native.log("the server command /" + root.getName() + " can't be told to Horizon: its name isn't a word");
                continue;
            }
            walk(root, root.getName(), level(root, probe), probe, out, 0);
        }
        return new ArrayList<>(out);
    }

    private static void walk(CommandNode<CommandSourceStack> node, String path, int level, ServerPlayer probe,
            Set<String> out, int depth) {
        if (node.getCommand() != null) {
            out.add(level + " " + path);
        }
        // A redirect (an alias, a fork) takes whatever follows to where it goes.
        if (node.getRedirect() != null || depth > 32) {
            out.add(level + " " + path + " " + REST);
            return;
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            int childLevel = Math.max(level, level(child, probe));
            String token = token(child);
            if (token == null) {
                out.add(childLevel + " " + path + " " + REST);
            } else if (token.endsWith(":text>")) {
                // The rest of the line: nothing can follow it in a usage.
                out.add(childLevel + " " + path + " " + token);
            } else {
                walk(child, path + " " + token, childLevel, probe, out, depth + 1);
            }
        }
    }

    /** A node as a usage's word or {@code <name:kind>}; null for a word Horizon doesn't take. */
    private static String token(CommandNode<CommandSourceStack> node) {
        if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
            return WORD.matcher(literal.getLiteral()).matches() ? literal.getLiteral() : null;
        }
        if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
            String name = argument.getName().replaceAll("[^A-Za-z0-9_]", "_");
            return "<" + (name.isEmpty() ? "arg" : name) + ":" + kind(argument.getType(), node.getChildren().isEmpty()) + ">";
        }
        return null;
    }

    /**
     * Horizon's kind for an argument type: the one the chat box parses it as. A type Horizon has none for is a word, or
     * the rest of the line where nothing follows it.
     */
    static String kind(ArgumentType<?> type, boolean last) {
        if (type instanceof StringArgumentType s) {
            return switch (s.getType()) {
                case SINGLE_WORD -> "word";
                case QUOTABLE_PHRASE -> last ? "text" : "word";
                case GREEDY_PHRASE -> "text";
            };
        }
        if (type instanceof IntegerArgumentType || type instanceof LongArgumentType) {
            return "integer";
        }
        if (type instanceof DoubleArgumentType || type instanceof FloatArgumentType) {
            return "number";
        }
        if (type instanceof BoolArgumentType) {
            return "boolean";
        }
        if (type instanceof EntityArgument || type instanceof net.minecraft.commands.arguments.GameProfileArgument
                || type instanceof net.minecraft.commands.arguments.ScoreHolderArgument) {
            return "player";
        }
        // A position, three coordinates (decimals the chat box may mark; the line still runs).
        if (type instanceof net.minecraft.commands.arguments.coordinates.Vec3Argument) {
            return "block_pos";
        }
        return switch (type.getClass().getName()) {
            case "net.minecraft.commands.arguments.coordinates.BlockPosArgument" -> "block_pos";
            case "net.minecraft.commands.arguments.item.ItemArgument" -> "item";
            default -> last ? "text" : "word";
        };
    }

    /**
     * The lowest operator level whose source the node's requirement lets through; 0 when none does (it asks what a
     * level alone doesn't say): Brigadier asks it again, of the real player, when the command runs.
     */
    private static int level(CommandNode<CommandSourceStack> node, ServerPlayer probe) {
        for (PermissionLevel level : PermissionLevel.values()) {
            try {
                if (node.canUse(CommandSourceStack.kiln$player(probe, LevelBasedPermissionSet.forLevel(level)))) {
                    return level.id();
                }
            } catch (RuntimeException | LinkageError e) {
                return 0;
            }
        }
        return 0;
    }

    /** Who is running a command now, and at what operator level ({@link #isOperator}). */
    private static java.util.UUID runner;
    private static int runnerLevel;

    /** A player's command ({@code line}, without its slash) at operator level {@code permission}. */
    static String run(int permission, String line, ServerPlayer player) {
        CommandSourceStack source =
                CommandSourceStack.kiln$player(player, LevelBasedPermissionSet.forLevel(PermissionLevel.byId(permission)));
        runner = player.getUUID();
        runnerLevel = permission;
        try {
            return perform(commands().getDispatcher(), source, line) ? "success" : "fail";
        } finally {
            runner = null;
        }
    }

    /** Whether the player {@code id} is an operator, as far as Kiln knows: the one running a command at level 2 or more. */
    public static boolean isOperator(java.util.UUID id) {
        return id != null && id.equals(runner) && runnerLevel >= 2;
    }

    /** The operator level of the player {@code id}: the running command's player's (D-107); 0 for anyone else. */
    public static int levelOf(java.util.UUID id) {
        return id != null && id.equals(runner) ? runnerLevel : 0;
    }

    /** Runs {@code line} as {@code source}; what went wrong, as the game says it, to the source. Whether it ran. */
    public static boolean perform(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            String line) {
        int depth = -1;
        try {
            String mod = SoftSwitch.commandMod(dispatcher, line, source);
            if (!mod.isEmpty() && SoftSwitch.off(mod)) {
                source.sendFailure(Component.literal(SoftSwitch.offMessage(mod)));
                return false;
            }
            depth = Breadcrumb.enter(mod, "the server command /" + line);
            try {
                dispatcher.execute(line, source);
            } catch (OutOfMemoryError | StackOverflowError e) {
                SoftSwitch.trip(mod, e, "the server command /" + line);
                source.sendFailure(Component.translatable("command.failed"));
                return false;
            }
            return true;
        } catch (CommandSyntaxException e) {
            source.sendFailure(e.getRawMessage() instanceof Component c ? c : Component.literal(e.getRawMessage().getString()));
            if (e.getInput() != null && e.getCursor() >= 0) {
                source.sendFailure(context(e.getInput(), Math.min(e.getInput().length(), e.getCursor())));
            }
            return false;
        } catch (RuntimeException | LinkageError e) {
            Native.log("the server command /" + line + " threw " + e);
            source.sendFailure(Component.translatable("command.failed"));
            return false;
        } finally {
            Breadcrumb.leave(depth);
        }
    }

    /** Where the line went wrong, as the game shows it: the ten characters before, the rest underlined, "<--[HERE]". */
    private static Component context(String input, int cursor) {
        MutableComponent context = Component.empty().withStyle(ChatFormatting.GRAY);
        if (cursor > 10) {
            context.append("...");
        }
        context.append(input.substring(Math.max(0, cursor - 10), cursor));
        if (cursor < input.length()) {
            context.append(Component.literal(input.substring(cursor)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE));
        }
        context.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        return context;
    }
}
