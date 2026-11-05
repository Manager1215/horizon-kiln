package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for Fabric's {@code ClientCommands}: the mods' client commands, a brigadier tree of their own. The
 * game hands Kiln each command the player types or clicks before the server sees it; one of these runs here, with the
 * next tick, and goes no further.
 */
public final class ClientCommands {
    private static CommandDispatcher<FabricClientCommandSource> active;
    private static final FabricClientCommandSource SOURCE = new Source();

    private ClientCommands() {}

    /** The connection's client commands; null before a world's are made. */
    public static CommandDispatcher<FabricClientCommandSource> getActiveDispatcher() {
        return active;
    }

    /** Tells the game the commands' names again (a mod added one later). */
    public static void refreshCommandCompletions() {
        kiln$announce();
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    /** Kiln's own: the connection's client commands made anew, as the mods register them; the game is told their names. */
    public static void kiln$register() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher, CommandBuildContext.KILN);
        active = dispatcher;
        kiln$announce();
    }

    private static void kiln$announce() {
        List<String> names = new ArrayList<>();
        if (active != null) {
            for (CommandNode<FabricClientCommandSource> node : active.getRoot().getChildren()) {
                names.add(node.getName());
            }
        }
        kiln.Native.commands(names);
    }

    /** Kiln's own: the player's command (without its slash) for one of these; what goes wrong, in their chat. */
    public static void kiln$run(String command) {
        if (active == null) {
            return;
        }
        try {
            active.execute(command, SOURCE);
        } catch (CommandSyntaxException e) {
            SOURCE.sendError(Component.literal(e.getMessage()));
        } catch (RuntimeException | LinkageError e) {
            kiln.Native.log("the client command /" + command + " failed: " + e);
            SOURCE.sendError(Component.literal(String.valueOf(e.getMessage())));
        }
    }

    /** The window's player, running a client command. */
    private static final class Source implements FabricClientCommandSource {
        @Override
        public void sendFeedback(Component message) {
            Minecraft.getInstance().gui.hud.getChat().addMessage(message);
        }

        @Override
        public void sendError(Component message) {
            Minecraft.getInstance().gui.hud.getChat().addMessage(
                    Component.empty().append(message).withStyle(ChatFormatting.RED));
        }

        @Override
        public Minecraft getClient() {
            return Minecraft.getInstance();
        }

        @Override
        public LocalPlayer getPlayer() {
            return Minecraft.getInstance().player;
        }

        @Override
        public ClientLevel getLevel() {
            return Minecraft.getInstance().level;
        }

        @Override
        public boolean attended() {
            return true;
        }

        @Override
        public Collection<String> getOnlinePlayerNames() {
            List<String> names = new ArrayList<>();
            if (Minecraft.getInstance().getConnection() != null) {
                for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                    names.add(info.kiln$name());
                }
            }
            return names;
        }
    }
}
