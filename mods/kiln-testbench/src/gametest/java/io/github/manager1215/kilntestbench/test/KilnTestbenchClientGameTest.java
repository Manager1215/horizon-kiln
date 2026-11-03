package io.github.manager1215.kilntestbench.test;

import io.github.manager1215.kilntestbench.core.Bench;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The reference: in real Minecraft 26.3 with Fabric every probe must pass. A probe that can't pass here is wrong, not
 * Kiln; so whatever fails under Kiln is something Kiln doesn't do yet.
 */
public final class KilnTestbenchClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Path report = FabricLoader.getInstance().getConfigDir().resolve("kiln_testbench/report.txt");
        try {
            Files.deleteIfExists(report);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        String text;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            for (int waited = 0; !Files.exists(report); waited += 10) {
                if (waited > 600) {
                    throw new AssertionError("the testbench wrote no report");
                }
                context.waitTicks(10);
            }
            context.takeScreenshot("kiln-testbench");
            text = Files.readString(report);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Map<String, Bench.Status> statuses = Bench.parse(text);
        if (statuses.isEmpty() || statuses.values().stream().anyMatch(st -> st != Bench.Status.PASS)) {
            throw new AssertionError("every probe must pass in Minecraft with Fabric:\n" + text);
        }
        System.out.println("[Kiln Testbench test] all " + statuses.size() + " probes pass in Minecraft with Fabric");
    }
}
