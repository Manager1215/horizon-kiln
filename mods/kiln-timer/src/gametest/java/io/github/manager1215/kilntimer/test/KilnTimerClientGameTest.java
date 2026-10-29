package io.github.manager1215.kilntimer.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The timer in real Minecraft 26.3 with Fabric, as kiln/proof.py plays it in Horizon: a new world starts the run,
 * advancements granted by command and trips between dimensions make the six splits, a two-second pause stays out
 * of IGT, and "Free the End" finishes a first personal best. The check reads the records the timer saved.
 */
public final class KilnTimerClientGameTest implements FabricClientGameTest {
    private static final List<String> SPLITS = List.of("NETHER", "BASTION", "FORTRESS", "BLIND", "STRONGHOLD", "END", "FINISH");

    @Override
    public void runTest(ClientGameTestContext context) {
        Path records = FabricLoader.getInstance().getConfigDir().resolve("kiln_timer/records.txt");
        try {
            Files.deleteIfExists(records);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            context.waitTicks(40);
            step(context, server, "advancement grant @a only minecraft:story/enter_the_nether");
            step(context, server, "execute in minecraft:the_nether run tp @a 0 100 0");
            step(context, server, "advancement grant @a only minecraft:nether/find_bastion");
            step(context, server, "advancement grant @a only minecraft:nether/find_fortress");
            step(context, server, "execute in minecraft:overworld run tp @a 0 120 0");
            step(context, server, "advancement grant @a only minecraft:story/follow_ender_eye");
            // The pause menu for two seconds: client ticks go on, IGT doesn't.
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(40);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(10);
            step(context, server, "execute in minecraft:the_end run tp @a 0 100 0");
            step(context, server, "advancement grant @a only minecraft:end/kill_dragon");
            context.takeScreenshot("kiln-timer-finished");
        }
        String saved;
        try {
            saved = Files.readString(records);
        } catch (IOException e) {
            throw new AssertionError("the timer saved no records: " + e);
        }
        for (String split : SPLITS) {
            if (!saved.contains("pb." + split + "=")) {
                throw new AssertionError("no " + split + " in the personal best:\n" + saved);
            }
        }
        String[] finish = saved.lines().filter(l -> l.startsWith("pb.FINISH=")).findFirst().orElseThrow()
                .substring("pb.FINISH=".length()).split(" ");
        long rta = Long.parseLong(finish[0]);
        long igt = Long.parseLong(finish[1]);
        if (rta - igt < 1_500_000_000L) {
            throw new AssertionError("the pause should be out of IGT: RTA " + rta + ", IGT " + igt);
        }
        System.out.println("[Kiln Timer test] all splits and a personal best; RTA " + rta / 1_000_000 + " ms, IGT "
                + igt / 1_000_000 + " ms");
    }

    private static void step(ClientGameTestContext context, TestServerContext server, String command) {
        server.runCommand(command);
        context.waitTicks(30);
    }
}
