package kiln.test.crash;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

/**
 * A mod for Kiln's tests of D-112's crash policy (kiln/tests/crash_policy.rs): its client tick counts, and on its
 * command does what a broken mod does once, on the next tick: {@code /crashtest overflow} recurses until Java's stack
 * overflows, {@code /crashtest oom} allocates until Java's heap is gone (and lets go of it as the error unwinds),
 * {@code /crashtest hang <seconds>} sleeps that long in the tick. Written against Minecraft 26.3 and Fabric's API as any
 * mod is; built against Kiln's stand-ins.
 */
public final class TestCrash implements ClientModInitializer {
    public static int ticks;
    private static volatile String next = "";
    private static volatile int seconds;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            String what = next;
            next = "";
            switch (what) {
                case "overflow" -> deeper(0);
                case "oom" -> {
                    List<long[]> hog = new ArrayList<>();
                    while (true) {
                        hog.add(new long[1 << 20]);
                    }
                }
                case "hang" -> {
                    try {
                        Thread.sleep(seconds * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                default -> { }
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("crashtest")
                        .then(ClientCommands.literal("overflow").executes(c -> say(c.getSource(), "overflow")))
                        .then(ClientCommands.literal("oom").executes(c -> say(c.getSource(), "oom")))
                        .then(ClientCommands.literal("hang")
                                .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                        .executes(c -> {
                                            seconds = IntegerArgumentType.getInteger(c, "seconds");
                                            return say(c.getSource(), "hang");
                                        })))));
    }

    private static int say(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String what) {
        next = what;
        source.sendFeedback(Component.literal("Crash test: " + what + " on the next tick"));
        return 1;
    }

    private static int deeper(int n) {
        return deeper(n + 1) + 1;
    }
}
