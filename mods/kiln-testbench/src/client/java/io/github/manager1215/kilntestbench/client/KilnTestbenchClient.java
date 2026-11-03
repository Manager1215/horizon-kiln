package io.github.manager1215.kilntestbench.client;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Bench.Phase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Runs the probes in their phases and writes the report to {@code <config>/kiln_testbench/report.txt}: at start,
 * once in a world, in the HUD, and later. It uses only what Kiln stands in for, so it runs (and reports) under Kiln
 * however many probes fail.
 */
public final class KilnTestbenchClient implements ClientModInitializer {
    static final String ID = "kiln_testbench";
    /** Client ticks in a world before the WORLD probes, and before the LATE ones and the report. */
    private static final int WORLD_TICK = 20;
    private static final int LATE_TICK = 60;

    private final Bench bench = new Bench();
    private int worldTicks;
    private boolean reported;

    @Override
    public void onInitializeClient() {
        LoaderProbes.add(bench);
        EventProbes.add(bench);
        DataProbes.add(bench);
        ClientProbes.add(bench);
        PlayerProbes.add(bench);
        LevelProbes.add(bench);
        HudProbes.add(bench);
        bench.run(Phase.INIT);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "probes"), this::draw);
    }

    private void tick(Minecraft client) {
        if (reported || client.player == null || client.level == null) {
            return;
        }
        worldTicks++;
        if (worldTicks == WORLD_TICK) {
            bench.run(Phase.WORLD);
        }
        if (worldTicks == LATE_TICK) {
            bench.never(Phase.HUD, "the HUD was never drawn");
            bench.run(Phase.LATE);
            report(client);
        }
    }

    private void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (bench.ran(Phase.WORLD) && !bench.ran(Phase.HUD)) {
            Game.graphics = graphics;
            Game.delta = delta;
            try {
                bench.run(Phase.HUD);
            } finally {
                Game.graphics = null;
                Game.delta = null;
            }
        }
        if (reported) {
            graphics.text(Minecraft.getInstance().font, "Kiln Testbench: " + bench.score(), 2,
                    graphics.guiHeight() - 10, 0xFFFFFFFF, true);
        }
    }

    private void report(Minecraft client) {
        reported = true;
        String where = onKiln() ? "Kiln" : "Fabric";
        String report = bench.report("kiln-testbench on " + where + ", Java " + System.getProperty("java.version"));
        Path file = FabricLoader.getInstance().getConfigDir().resolve(ID).resolve("report.txt");
        String saved;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report);
            saved = "report in config/" + ID + "/report.txt";
        } catch (IOException e) {
            saved = "no report written: " + e;
        }
        System.out.println(report);
        client.player.sendSystemMessage(
                Component.literal("[Kiln Testbench] " + bench.score() + " on " + where + "; " + saved));
    }

    private static boolean onKiln() {
        try {
            Class.forName("kiln.Host", false, KilnTestbenchClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
