package kiln.test.teleports;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server commands that reach the server's levels and move the player as home and warp mods do: {@code /tptest go
 * <dimension> <x> <y> <z>} moves the player to the level the server has by that name ({@code MinecraftServer.getLevel},
 * {@code ServerPlayer.teleportTo}) and says where they are then; {@code /tptest up} moves them 10 blocks up in their
 * level ({@code Entity.teleportTo}); {@code /tptest levels} lists the server's levels and its overworld.
 */
public class TestTeleports implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> dispatcher.register(
                Commands.literal("tptest")
                        .then(Commands.literal("go").then(Commands.argument("dimension", StringArgumentType.string())
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(StringArgumentType.getString(c, "dimension")));
            ServerLevel level = c.getSource().getServer().getLevel(key);
            if (level == null) {
                return say(c.getSource(), "No level " + key.identifier());
            }
            player.teleportTo(level, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"),
                    DoubleArgumentType.getDouble(c, "z"), Set.of(), 45.0F, 10.0F, false);
            return say(c.getSource(), where(player));
        }))))))
                        .then(Commands.literal("up").executes(c -> {
                            ServerPlayer player = c.getSource().getPlayerOrException();
                            player.teleportTo(player.getX(), player.getY() + 10, player.getZ());
                            return say(c.getSource(), where(player));
                        }))
                        .then(Commands.literal("levels").executes(c -> {
                            StringBuilder text = new StringBuilder();
                            for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
                                text.append(level.dimension().identifier()).append(' ');
                            }
                            text.append(c.getSource().getServer().overworld().dimension().identifier());
                            return say(c.getSource(), text.toString());
                        }))));
    }

    private static String where(ServerPlayer player) {
        return player.level().dimension().identifier() + " " + player.getX() + " " + player.getY() + " "
                + player.getZ() + " " + player.getYRot() + " " + player.getXRot();
    }

    private static int say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
