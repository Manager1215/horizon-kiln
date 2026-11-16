package kiln.test.commanderrors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * A mod for Kiln's test of a client command's errors (kiln/tests/command_errors.rs): {@code /kiln_test_count [n]} counts to
 * n, and fails as mods' commands do, with errors made of components (brigadier's {@code Message} in the game):
 * nothing to count, too many to count. Written against Minecraft 26.3 and Fabric's API as any mod is; built against
 * Kiln's stand-ins.
 */
public final class TestCommandErrors implements ClientModInitializer {
    private static final SimpleCommandExceptionType NOTHING =
            new SimpleCommandExceptionType(Component.literal("Nothing to count").withStyle(ChatFormatting.GOLD));
    private static final DynamicCommandExceptionType TOO_MANY =
            new DynamicCommandExceptionType(n -> Component.literal("Too many to count: " + n));

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("kiln_test_count")
                        .executes(command -> {
                            throw NOTHING.create();
                        })
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(0)).executes(command -> {
                            int n = IntegerArgumentType.getInteger(command, "n");
                            if (n > 64) {
                                throw TOO_MANY.create(n);
                            }
                            command.getSource().sendFeedback(Component.literal("Counted to " + n));
                            return n;
                        }))));
    }
}
