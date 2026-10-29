package io.github.manager1215.kilntimer.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.manager1215.kilntimer.core.Hud;
import io.github.manager1215.kilntimer.core.Records;
import io.github.manager1215.kilntimer.core.Settings;
import io.github.manager1215.kilntimer.core.Split;
import io.github.manager1215.kilntimer.core.Timer;
import io.github.manager1215.kilntimer.core.Times;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The timer in the game: it feeds the core ({@link Timer}) what happens each client tick (a world entered or
 * left, the pause, the dimension, advancements made, the keys) and draws its panel on the HUD. It uses only a
 * few classes of Minecraft and the Fabric API, the ones Kiln stands in for, so the same jar runs in Minecraft
 * with Fabric and in Horizon through Kiln.
 */
public final class KilnTimerClient implements ClientModInitializer {
    static final String ID = "kiln_timer";
    private static final int PAD = 3;
    private static final int GAP = 6;
    private static final int MARGIN = 2;
    private static final int LINE = 10;
    private static final int BACKGROUND = 0x78000000;
    private static final int OPAQUE = 0xFF000000;

    private final Timer timer = new Timer(Settings.load("").finish);
    private final Set<Identifier> made = new HashSet<>();
    private Settings settings = new Settings();
    private Records records = new Records();
    /** The records as they were when this run started: what its deltas compare to, even after it becomes the PB. */
    private Records comparison = new Records();
    private Path folder;
    private KeyMapping splitKey;
    private KeyMapping resetKey;
    private KeyMapping hideKey;
    private boolean inWorld;
    private boolean recorded;
    private boolean newPb;
    /** The splits of this run already said in chat. */
    private final Set<Split> announced = EnumSet.noneOf(Split.class);

    @Override
    public void onInitializeClient() {
        folder = FabricLoader.getInstance().getConfigDir().resolve(ID);
        settings = Settings.load(read("settings.properties"));
        records = Records.load(read("records.txt"));
        comparison = Records.load(records.save());
        timer.setFinishMode(settings.finish);
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "timer"));
        splitKey = key("split", InputConstants.KEY_J, category);
        resetKey = key("reset", InputConstants.KEY_K, category);
        hideKey = key("hide", InputConstants.KEY_U, category);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "panel"), this::draw);
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key." + ID + "." + name, InputConstants.Type.KEYBOARD, code, category));
    }

    private void tick(Minecraft client) {
        long now = System.nanoTime();
        if (client.getConnection() == null) {
            // Out of the world, on the title screen: an unfinished run ends.
            if (inWorld) {
                inWorld = false;
                keepRecords();
                timer.leftWorld();
            }
            return;
        }
        if (client.player == null || client.level == null) {
            // Still connected but no player: a dimension is loading. IGT leaves loading screens out.
            timer.setStopped(true, now);
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        Map<AdvancementHolder, AdvancementProgress> progress = client.getConnection().getAdvancements().progress();
        if (!inWorld) {
            // A world: what was made before doesn't split; a new world starts the run.
            inWorld = true;
            made.clear();
            progress.forEach((holder, p) -> {
                if (p.isDone()) {
                    made.add(holder.id());
                }
            });
            fresh();
            timer.enteredWorld(client.level.getGameTime(), dimension, now);
        }
        timer.setStopped(client.isPaused(), now);
        timer.dimension(dimension, now);
        progress.forEach((holder, p) -> {
            if (p.isDone() && made.add(holder.id())) {
                timer.advancement(holder.id().toString(), now);
            }
        });
        while (splitKey.consumeClick()) {
            if (timer.phase() == Timer.Phase.IDLE) {
                fresh();
                timer.start(now);
            } else {
                timer.splitNext(now);
            }
        }
        while (resetKey.consumeClick()) {
            keepRecords();
            timer.reset();
            fresh();
        }
        while (hideKey.consumeClick()) {
            settings.shown = !settings.shown;
            write("settings.properties", settings.save());
        }
        announceSplits(client);
        if (timer.phase() == Timer.Phase.FINISHED && !recorded) {
            recorded = true;
            newPb = records.record(timer.splits());
            write("records.txt", records.save());
            say(client, "Final IGT " + Times.format(timer.igt(now)) + ", RTA " + Times.format(timer.rta(now))
                    + (newPb ? ". New personal best!" : "."));
        }
    }

    /** A new run's state: nothing recorded or said yet, compared to the records as they are now. */
    private void fresh() {
        comparison = Records.load(records.save());
        recorded = false;
        newPb = false;
        announced.clear();
    }

    /** An unfinished run's splits still count for the best splits. */
    private void keepRecords() {
        if (timer.phase() == Timer.Phase.RUNNING && !timer.splits().isEmpty()) {
            records.record(timer.splits());
            write("records.txt", records.save());
        }
    }

    /** With chat splits on, each split reached is said in chat once (the finish has its own line). */
    private void announceSplits(Minecraft client) {
        timer.splits().forEach((split, t) -> {
            if (announced.add(split) && settings.chatSplits && split != Split.FINISH) {
                String delta = comparison.delta(split, t.igt()).map(d -> " (" + Times.delta(d) + ")").orElse("");
                say(client, split.label() + " " + Times.format(t.igt()) + delta);
            }
        });
    }

    private static void say(Minecraft client, String text) {
        client.player.sendSystemMessage(Component.literal("[Kiln Timer] " + text));
    }

    /** The panel: rows of label, time and delta, in the corner the settings name. */
    private void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.shown || client.player == null) {
            return;
        }
        Font font = client.font;
        List<Hud.Row> rows = Hud.rows(timer, comparison, newPb, System.nanoTime());
        int labels = 0;
        int times = 0;
        int deltas = 0;
        for (Hud.Row row : rows) {
            labels = Math.max(labels, font.width(row.label()));
            times = Math.max(times, font.width(row.time()));
            deltas = Math.max(deltas, font.width(row.delta()));
        }
        int width = PAD + labels + GAP + times + (deltas > 0 ? GAP + deltas : 0) + PAD;
        int height = PAD + rows.size() * LINE + PAD - 1;
        Settings.Corner corner = settings.corner;
        int x = corner.left() ? MARGIN : graphics.guiWidth() - MARGIN - width;
        int y = corner.top() ? MARGIN : graphics.guiHeight() - MARGIN - height;
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        int line = y + PAD;
        for (Hud.Row row : rows) {
            graphics.text(font, row.label(), x + PAD, line, OPAQUE | row.color());
            int timeRight = x + PAD + labels + GAP + times;
            graphics.text(font, row.time(), timeRight - font.width(row.time()), line, OPAQUE | row.color());
            if (!row.delta().isEmpty()) {
                int deltaRight = timeRight + GAP + deltas;
                graphics.text(font, row.delta(), deltaRight - font.width(row.delta()), line, OPAQUE | row.deltaColor());
            }
            line += LINE;
        }
    }

    private String read(String name) {
        try {
            Path file = folder.resolve(name);
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            System.err.println("[Kiln Timer] can't read " + name + ": " + e);
            return "";
        }
    }

    private void write(String name, String text) {
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(name), text);
        } catch (IOException e) {
            System.err.println("[Kiln Timer] can't save " + name + ": " + e);
        }
    }
}
