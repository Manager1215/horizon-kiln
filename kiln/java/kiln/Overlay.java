package kiln;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's overlay (D-113, {@code internal/soft-switch-spike.md} section 5): a screen of Kiln's own, shown as a mod's
 * screen is (D-094), over the game on its key (F7 by default, bound on Kiln's page in Options > Mods) or from Kiln's
 * page there. It lists every Java mod in {@code kiln/mods} with its version, its state, whether the soft switch can turn
 * it off now ({@link SoftOff}), whether horizon-kiln.cc lists it as tried, and its switch.
 *
 * <p>The game opens it with {@code overlay <key> <row>...}, each row the fields of {@link Jar} joined by U+001F; the
 * same command again closes it. A switch says {@code switch <mod id> <1 on|0 off>} to the game, which flips
 * the mod's switch in Options > Mods (and so in {@code horizon-mods.txt}); its {@code soft_off} then stops or starts
 * the mod at once ({@link SoftSwitch}). "Open the mods' folder" says {@code open mods}.
 *
 * <p>Tabs: Mods (all of them), Can turn off now (the mods running now that a switch stops this minute: safe or
 * partial) and Crashes (what the crash window, the watchdog and the soft switch recorded, newest first, {@link
 * Crashes}; D-134). The bottom row: the mods' folder, and Restart Java now (D-134), which says {@code restart}: the game
 * starts Java again with the switches as they are now. Java in the game's process can't be made again (a process has
 * one JVM), so the new Java runs in a process of its own and this one stays loaded, idle, its memory taken until the
 * game closes: the button asks a second click, and says so.
 */
public final class Overlay extends Screen {
    /** A mod jar as the game lists it: its id, name and version, the jar, what it holds, its last stop, its switch. */
    record Jar(String id, String name, String version, Path jar, String kind, String lastStop, boolean off) {
        /** {@code kind}: {@code code} (entrypoints), {@code mixins} (mixins only) or {@code data} (data only). */
        static Jar parse(String field) {
            String[] f = field.split("\u001f", -1);
            if (f.length < 7) {
                return null;
            }
            return new Jar(f[0], f[1], f[2], Path.of(f[3]), f[4], f[5], f[6].equals("1"));
        }
    }

    /** A row's piece of text and its colour. */
    record Label(String text, int color) {}

    /** Off-now answers by jar, made off Kiln's thread ({@link #prepare}): a jar's SHA-256 takes a moment. */
    private static final Map<Path, SoftOff.Answer> ANSWERS = new ConcurrentHashMap<>();
    private static volatile Thread preparing;

    static final int GRAY = 0xFFA0A0A0;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF55FF55;
    static final int YELLOW = 0xFFFFFF55;
    static final int RED = 0xFFFF5555;
    static final int LINE = 10;

    /** The tabs. */
    enum Tab { MODS, OFF_NOW, CRASHES }

    /** This Java was started again by Restart Java now after Java in the game's process (the game's {@code restarted}). */
    private static volatile boolean restarted;
    /** The first Java's memory as it measured itself as it was wound down, in bytes (0: not known). */
    private static volatile long oldBytes;

    /**
     * The game's {@code restarted <bytes> <banner 1|0>}: the Java in the game's process stays loaded, idle, until the
     * game closes, holding {@code bytes}; the banner says so once a game ({@link RestartBanner}), the overlay after it.
     */
    static void restarted(String[] f) {
        restarted = true;
        try {
            oldBytes = f.length > 1 ? Long.parseLong(f[1]) : 0;
        } catch (NumberFormatException e) {
            oldBytes = 0;
        }
        if (f.length > 2 && f[2].equals("1")) {
            RestartBanner.open(oldBytes);
        }
    }

    /**
     * This Java's memory, as the banner after a restart says it: the heap it uses (not what it has set aside: ZGC
     * commits the whole heap, most of it never touched) and its non-heap (class metadata, compiled code). What the JVM
     * holds besides (threads' stacks, the collector's own tables, its library) isn't counted: the banner says "at least".
     */
    static long memory() {
        try {
            var bean = java.lang.management.ManagementFactory.getMemoryMXBean();
            return bean.getHeapMemoryUsage().getUsed() + bean.getNonHeapMemoryUsage().getCommitted();
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** How long Restart Java now waits for its second click, in ticks. */
    static final int ARMED_TICKS = 80;

    private final String key;
    private final List<Jar> jars;
    /** The switches as the player left them here, by mod id: the game's word until the soft switch catches up. */
    private final Map<String, Boolean> on = new ConcurrentHashMap<>();
    /** The ticks left before a switch flipped here follows the soft switch again (the game's answer comes a tick on). */
    private final Map<String, Integer> flipped = new ConcurrentHashMap<>();
    /** How many answers the rows were laid out with: a row's note may need a line once its answer comes. */
    private int answered = -1;
    /** How many the Can turn off now tab had when the rows were laid out: a switch or a mod's start changes it. */
    private int offNowCount = -1;
    private Tab tab = Tab.MODS;
    private String search = "";
    private ModList list;
    private EditBox searchBox;
    private Button modsTab;
    private Button offNowTabButton;
    private Button crashesTab;
    private Button restart;
    /** The crashes as last read, newest first, and the ticks before they are read again. */
    private List<Crashes.Crash> crashes = List.of();
    private int crashesIn;
    /** Ticks left for Restart Java now's second click; 0 when it isn't waiting for one; -1 once it was clicked. */
    private int armed;

    Overlay(String key, List<Jar> jars) {
        super(Component.literal("Kiln"));
        this.key = key;
        this.jars = jars;
        for (Jar jar : jars) {
            on.put(jar.id(), !jar.off());
        }
    }

    /** Kiln's folder, as the game started Java in it. */
    static Path kilnDir() {
        return Path.of(System.getProperty("kiln.dir", System.getProperty("user.dir", ".")));
    }

    /**
     * At Java's start, off Kiln's thread: the catalogue's copy in use (a refreshed one in {@code <kiln>/cache}), then
     * every jar's answer, so the overlay has them when it opens (D-126).
     */
    static void prepare() {
        Path dir = kilnDir();
        List<Path> jars;
        try (Stream<Path> found = Files.list(dir.resolve("mods"))) {
            jars = found.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted()
                    .toList();
        } catch (java.io.IOException e) {
            jars = List.of();
        }
        SoftOff.use(dir);
        prepare(jars);
    }

    /** The answers for {@code jars} that aren't at hand yet, made on a thread of their own. */
    private static synchronized void prepare(List<Path> jars) {
        List<Path> missing = jars.stream().filter(j -> !ANSWERS.containsKey(j)).toList();
        Thread before = preparing;
        if (missing.isEmpty() || before != null && before.isAlive()) {
            return;
        }
        Thread t = new Thread(() -> {
            for (Path jar : missing) {
                try {
                    ANSWERS.put(jar, SoftOff.of(jar));
                } catch (RuntimeException e) {
                    // Not Host.send: this isn't Kiln's thread. java.log has it.
                    System.err.println("[Kiln] the overlay's reading of " + jar.getFileName() + " failed: " + e);
                }
            }
        }, "Kiln overlay: off-now answers");
        t.setDaemon(true);
        preparing = t;
        t.start();
    }

    /** The game's {@code overlay} command: opens the overlay over the game, or closes it when it is open. */
    static void toggle(String[] f) {
        Minecraft mc = Minecraft.getInstance();
        if (Screens.mod() instanceof Overlay open) {
            open.onClose();
            return;
        }
        List<Jar> jars = new ArrayList<>();
        for (int i = 2; i < f.length; i++) {
            Jar jar = Jar.parse(f[i]);
            if (jar != null) {
                jars.add(jar);
            }
        }
        prepare(jars.stream().map(Jar::jar).toList());
        mc.gui.setScreen(new Overlay(f.length > 1 ? f[1] : "", jars));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int bar = 4;
        // The GUI can be as narrow as 320 pixels (a high GUI scale, a small window, a Mac's Retina screen at Auto):
        // the tabs and the search box share what there is, the tabs as wide as 70, 110 and 80 where they fit.
        int searchWidth = Math.max(50, Math.min(120, width - 324));
        double room = Math.min(1.0, (width - 40 - 4 - searchWidth - 8) / 268.0);
        int modsWidth = (int) (70 * room);
        int offNowWidth = (int) (110 * room);
        int crashesWidth = (int) (80 * room);
        modsTab = addRenderableWidget(Button.builder(Component.literal("Mods"), b -> tab(Tab.MODS))
                .bounds(40, bar, modsWidth, 20).build());
        offNowTabButton = addRenderableWidget(Button.builder(Component.literal("Can turn off now"), b -> tab(Tab.OFF_NOW))
                .bounds(40 + modsWidth + 4, bar, offNowWidth, 20).build());
        crashesTab = addRenderableWidget(Button.builder(Component.literal("Crashes"), b -> tab(Tab.CRASHES))
                .bounds(40 + modsWidth + offNowWidth + 8, bar, crashesWidth, 20).build());
        crashes = Crashes.read(kilnDir());
        searchBox = addRenderableWidget(new EditBox(font, width - searchWidth - 8, bar, searchWidth, 20,
                Component.literal("Search")));
        searchBox.setHint(Component.literal("Search"));
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text;
            fill();
            list.setScrollAmount(0);
        });
        // Shorter by a line with the badge under it (two Javas, D-134).
        list = addRenderableWidget(new ModList(minecraft, width, height - (restarted ? 72 : 60), 28, 34));
        // The bottom row: the mods' folder, Restart Java now (D-134), Done.
        // Each 130 wide where there is room, else what is left beside Done.
        int bottom = Math.min(130, (width - 16 - 60 - 8) / 2);
        addRenderableWidget(Button.builder(Component.literal("Open the mods' folder"),
                b -> Host.send("open", "mods")).bounds(8, height - 26, bottom, 20).build());
        restart = addRenderableWidget(Button.builder(Component.literal(restartLabel()), b -> restart())
                .bounds(8 + bottom + 4, height - 26, bottom, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width - 68, height - 26, 60, 20).build());
        fill();
    }

    private void tab(Tab which) {
        tab = which;
        fill();
        list.setScrollAmount(0);
    }

    /** Restart Java now's label: what a click does now. */
    private String restartLabel() {
        if (armed < 0) {
            return "Restarting Java...";
        }
        return armed > 0 ? "Click again to restart" : "Restart Java now";
    }

    /**
     * Restart Java now: the first click asks for a second (in Java's process, this Java's memory stays taken; the line
     * over the buttons says so), the second says {@code restart} to the game, which starts Java again with the next
     * tick, the switches as they are now.
     */
    void restart() {
        if (armed < 0) {
            return;
        }
        if (armed == 0) {
            armed = ARMED_TICKS;
        } else {
            armed = -1;
            Host.send("restart", Long.toString(Jni.inProcess() ? memory() : 0));
        }
        restart.setMessage(Component.literal(restartLabel()));
    }

    /** What Restart Java now leaves behind, said over the buttons while it waits for its second click. */
    static String restartNote() {
        return Jni.inProcess()
                ? "Java starts again in a process of its own. This Java stays loaded, idle: its memory stays taken until"
                        + " the game closes."
                : "Java starts again, with the mods' switches as they are now.";
    }

    /** The Crashes tab's line about the Javas in this game, if there is one to say. */
    static String javaNote() {
        if (restarted) {
            String size = RestartBanner.size(oldBytes);
            return "Java was started again in this game. The first Java stays loaded in the game's process, idle, until"
                    + " the game closes: its memory stays taken" + (size.isEmpty() ? "" : " (at least " + size + ")")
                    + ". Nothing is broken; restart the game when it suits you.";
        }
        if (Jni.inProcess()) {
            return "Restart Java now starts Java again in a process of its own; this Java's memory stays taken until the"
                    + " game closes.";
        }
        return "";
    }

    /**
     * A client tick: a switch the soft switch turned off by itself (a mod that ran Java out of memory, {@code /kiln
     * off}) shows off here too, and the rows are laid out again when answers came.
     */
    @Override
    public void tick() {
        if (armed > 0 && --armed == 0) {
            restart.setMessage(Component.literal(restartLabel()));
        }
        // A crash recorded while the overlay shows (a mod turned off, a call that hangs) comes into the Crashes tab.
        if (--crashesIn <= 0) {
            crashesIn = 40;
            List<Crashes.Crash> now = Crashes.read(kilnDir());
            if (!now.equals(crashes)) {
                crashes = now;
                fill();
            }
        }
        for (Jar jar : jars) {
            int left = flipped.getOrDefault(jar.id(), 0);
            if (left > 0) {
                flipped.put(jar.id(), left - 1);
            } else if (onClassPath(jar)) {
                on.put(jar.id(), !SoftSwitch.off(jar.id()));
            }
        }
        if (ANSWERS.size() != answered || shown(true).size() != offNowCount) {
            fill();
        }
    }

    /** The rows of the tab shown, as the search leaves them. */
    private void fill() {
        if (list == null) {
            return;
        }
        answered = ANSWERS.size();
        offNowCount = shown(true).size();
        List<Row> rows = new ArrayList<>();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        if (tab == Tab.CRASHES) {
            String note = javaNote();
            if (!note.isEmpty()) {
                rows.add(new NoteRow(note));
            }
            for (Crashes.Crash crash : crashes) {
                String name = crashName(crash);
                if (needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle)
                        || crash.mod().toLowerCase(Locale.ROOT).contains(needle)) {
                    rows.add(new CrashRow(crash));
                }
            }
        } else {
            for (Jar jar : shown(tab == Tab.OFF_NOW)) {
                if (needle.isEmpty() || jar.name().toLowerCase(Locale.ROOT).contains(needle)
                        || jar.id().toLowerCase(Locale.ROOT).contains(needle)) {
                    rows.add(new ModRow(jar));
                }
            }
        }
        list.set(rows);
        modsTab.setMessage(Component.literal((tab == Tab.MODS ? "> " : "") + "Mods (" + shown(false).size() + ")"));
        String offNowLabel = (tab == Tab.OFF_NOW ? "> " : "") + "Can turn off now (" + shown(true).size() + ")";
        if (font.width(offNowLabel) > offNowTabButton.getWidth() - 8) {
            // A narrow GUI: the tab's short name.
            offNowLabel = (tab == Tab.OFF_NOW ? "> " : "") + "Off now (" + shown(true).size() + ")";
        }
        offNowTabButton.setMessage(Component.literal(offNowLabel));
        crashesTab.setMessage(Component.literal((tab == Tab.CRASHES ? "> " : "") + "Crashes (" + crashes.size() + ")"));
    }

    /** The jar of mod {@code id} in the list, or null. */
    private Jar jarOf(String id) {
        for (Jar jar : jars) {
            if (jar.id().equals(id)) {
                return jar;
            }
        }
        return null;
    }

    /** The crash's mod as its row names it: its name, else its id, else "The game" for a crash that named no mod. */
    String crashName(Crashes.Crash crash) {
        if (crash.mod().isEmpty()) {
            return "The game";
        }
        Jar jar = jarOf(crash.mod());
        return jar == null || jar.name().isEmpty() ? crash.mod() : jar.name();
    }

    /** What happened, as the row's first line says it after the mod's name. */
    static String crashWhat(Crashes.Crash crash) {
        if (crash.mod().isEmpty()) {
            return "stopped, in no Java mod's call";
        }
        return switch (crash.why()) {
            case "stuck" -> "hung the game";
            case "memory" -> "ran Java out of memory";
            case "stack" -> "overflowed Java's stack";
            default -> "stopped the game";
        };
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** The crash's time, in this computer's time zone. */
    static String when(Crashes.Crash crash) {
        return WHEN.format(Instant.ofEpochMilli(crash.when()).atZone(ZoneId.systemDefault()));
    }

    /** All the mods, or (the second tab) those running now whose switch stops them this minute. */
    List<Jar> shown(boolean offNow) {
        if (!offNow) {
            return jars;
        }
        return jars.stream().filter(j -> running(j) && j.kind().equals("code")
                && (cls(j).equals("safe") || cls(j).equals("partial"))).toList();
    }

    private static String cls(Jar jar) {
        SoftOff.Answer a = ANSWERS.get(jar.jar());
        return a == null ? "" : a.cls();
    }

    /** Whether Java runs it now: started with this game, every entrypoint ran, and not turned off since. */
    boolean running(Jar jar) {
        String started = Loader.started(jar.id());
        boolean loaded = jar.kind().equals("code") ? started != null && started.isEmpty() : onClassPath(jar);
        return loaded && on.getOrDefault(jar.id(), true) && !SoftSwitch.off(jar.id());
    }

    /** Whether its jar was put on Java's class path as Java started (a mod off then was left out). */
    private static boolean onClassPath(Jar jar) {
        return Mods.all().stream().anyMatch(m -> m.getMetadata().getId().equals(jar.id()));
    }

    /** The state the row shows, and its colour. */
    Label state(Jar jar) {
        boolean wanted = on.getOrDefault(jar.id(), true);
        boolean loaded = onClassPath(jar);
        String started = Loader.started(jar.id());
        String stop = switch (jar.lastStop()) {
            case "stopped" -> ", stopped the last game";
            case "stuck" -> ", hung the last game";
            case "memory" -> ", ran Java out of memory last game";
            case "stack" -> ", overflowed Java's stack last game";
            default -> "";
        };
        if (!loaded) {
            return wanted ? new Label("on from the next start" + stop, YELLOW)
                    : new Label("off" + stop, GRAY);
        }
        if (!wanted) {
            String when = cls(jar).equals("restart") ? "off, fully after a restart" : "off now";
            return new Label(when + stop, GRAY);
        }
        if (started != null && !started.isEmpty()) {
            return new Label("didn't start" + stop, RED);
        }
        String running = switch (jar.kind()) {
            case "mixins" -> "running (mixins only)";
            case "data" -> "content only";
            default -> started == null ? "not started yet" : "running";
        };
        return new Label(running + stop, stop.isEmpty() ? GREEN : YELLOW);
    }

    /** The "off now" column: what the switch does this minute. */
    static Label offNow(Jar jar) {
        if (jar.kind().equals("data")) {
            return new Label("nothing to stop", GRAY);
        }
        SoftOff.Answer a = ANSWERS.get(jar.jar());
        if (a == null) {
            return new Label("off now: ...", GRAY);
        }
        return switch (a.cls()) {
            case "safe" -> new Label("off now: yes", GREEN);
            case "partial" -> new Label("off now: partly", YELLOW);
            case "restart" -> new Label("fully off after a restart", RED);
            default -> new Label("off now: unknown", GRAY);
        };
    }

    /** The catalogue's word: tried on horizon-kiln.cc or not. */
    static Label tried(Jar jar) {
        SoftOff.Answer a = ANSWERS.get(jar.jar());
        if (a == null) {
            return new Label("", GRAY);
        }
        String other = a.sameJar() || a.version().isEmpty() ? "" : " (" + a.version() + ")";
        return switch (a.tried()) {
            case "works" -> new Label("tried: works" + other, GREEN);
            case "doesnt-work", "tried-not-working" -> new Label("tried: doesn't work" + other, RED);
            case "in-development" -> new Label("tried: in development", YELLOW);
            default -> new Label("not tried", GRAY);
        };
    }

    /** The row's last line: why it didn't start (what it lacks), or what a partial switch leaves. */
    String note(Jar jar) {
        String started = Loader.started(jar.id());
        if (started != null && !started.isEmpty() && onClassPath(jar)) {
            return lacks(started);
        }
        SoftOff.Answer a = ANSWERS.get(jar.jar());
        if (a != null && a.cls().equals("partial") && jar.kind().equals("code")) {
            return "its blocks and items stay in the world until the next game";
        }
        return "";
    }

    /** Known libraries by the package of a class a mod didn't find. */
    private static final String[][] LIBRARIES = {
        {"me/shedaniel/clothconfig", "Cloth Config"}, {"me.shedaniel.clothconfig", "Cloth Config"},
        {"me/shedaniel/autoconfig", "Cloth Config"}, {"me.shedaniel.autoconfig", "Cloth Config"},
        {"dev/isxander/yacl", "YetAnotherConfigLib"}, {"dev.isxander.yacl", "YetAnotherConfigLib"},
        {"com/terraformersmc/modmenu", "Mod Menu"}, {"com.terraformersmc.modmenu", "Mod Menu"},
        {"fuzs/forgeconfigapiport", "Forge Config API Port"}, {"fuzs.forgeconfigapiport", "Forge Config API Port"},
        {"net/fabricmc/fabric/api", "a part of Fabric API Kiln lacks"},
        {"net.fabricmc.fabric.api", "a part of Fabric API Kiln lacks"},
        {"software/bernie/geckolib", "GeckoLib"}, {"software.bernie.geckolib", "GeckoLib"},
        {"dev/architectury", "Architectury API"}, {"dev.architectury", "Architectury API"},
        {"io/wispforest/owo", "owo-lib"}, {"io.wispforest.owo", "owo-lib"},
    };

    /** A start's failure as the row says it: "needs Cloth Config" for a library missing, else the failure itself. */
    static String lacks(String failure) {
        if (failure.startsWith("java.lang.NoClassDefFoundError") || failure.startsWith("java.lang.ClassNotFoundException")) {
            for (String[] library : LIBRARIES) {
                if (failure.contains(library[0])) {
                    return "needs " + library[1];
                }
            }
        }
        return failure;
    }

    /** The player flipped mod {@code id}'s switch here: the game flips it in Options > Mods. */
    void flip(Jar jar) {
        boolean now = !on.getOrDefault(jar.id(), true);
        on.put(jar.id(), now);
        flipped.put(jar.id(), 20);
        Host.send("switch", jar.id(), now ? "1" : "0");
        fill();
    }

    boolean isOn(Jar jar) {
        return on.getOrDefault(jar.id(), true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The overlay's own key closes it (a function key types nothing into the search box).
        if (!key.isEmpty()
                && event.key() == com.mojang.blaze3d.platform.InputConstants.getKey(key).getValue()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world behind dimmed, as the game's own screens have it: the chat and the mods' HUD lines under the
        // overlay ran through its rows (seen in the game, #180).
        graphics.fill(0, 0, width, height, 0xC8000000);
        graphics.text(font, "Kiln", 8, 10, WHITE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // The badge after Restart Java now moved Java out of the game's process: two Javas until the game restarts.
        if (restarted) {
            String badge = "2 Javas: restart the game when it suits you";
            graphics.text(font, fit(badge, width - 16), 8, height - 41, YELLOW);
        }
        if (list.children().isEmpty()) {
            String none = tab == Tab.CRASHES ? "No crashes recorded"
                    : jars.isEmpty() ? "No Java mods in kiln/mods" : "No mod here";
            graphics.centeredText(font, none, width / 2, 28 + (height - 60) / 2 - 4, GRAY);
        }
        if (armed > 0) {
            // Two lines at most, over the list's foot: one line cut what the second click leaves behind.
            List<String> lines = RestartBanner.wrap(font, restartNote(), width - 16);
            if (lines.size() > 2) {
                lines = List.of(lines.get(0), fit(String.join(" ", lines.subList(1, lines.size())), width - 16));
            }
            int top = height - 30 - lines.size() * LINE;
            graphics.fill(4, top - 2, width - 4, height - 28, 0xE0000000);
            for (int i = 0; i < lines.size(); i++) {
                graphics.text(font, lines.get(i), 8, top + i * LINE, YELLOW);
            }
        }
        // Between Restart Java now and Done, where there is room for it.
        String fresh = SoftOff.generated();
        if (!fresh.isEmpty()) {
            String text = "catalogue of " + fresh;
            int x = width - 76 - font.width(text);
            if (x >= restart.getRight() + 6) {
                graphics.text(font, text, x, height - 20, GRAY);
            }
        }
    }

    // ---- The list ----

    final class ModList extends ContainerObjectSelectionList<Row> {
        ModList(Minecraft minecraft, int width, int height, int y, int rowHeight) {
            super(minecraft, width, height, y, rowHeight);
            // From the top, as a mod manager's list: a few rows don't float in the middle.
            centerListVertically = false;
        }

        /** The rows, in place of the ones before; the scroll stays where it can. */
        void set(List<Row> rows) {
            double scroll = scrollAmount();
            clearEntries();
            for (Row row : rows) {
                addEntry(row, row.lines() * LINE + 6);
            }
            setScrollAmount(scroll);
        }

        @Override
        public int getRowWidth() {
            return Math.min(width - 24, 460);
        }
    }

    /** A row of the list: a mod, a crash, or a note. */
    abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
        /** Its height, in lines of text. */
        abstract int lines();
    }

    /** A line of text across the list: what the Crashes tab says about the Javas in this game. */
    final class NoteRow extends Row {
        final String text;

        NoteRow(String text) {
            this.text = text;
        }

        @Override
        int lines() {
            return 3;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            int x = getContentX();
            int w = getContentRight() - x;
            List<String> lines = RestartBanner.wrap(font, text, w);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                String line = i == 2 && lines.size() > 3 ? fit(String.join(" ", lines.subList(2, lines.size())), w)
                        : lines.get(i);
                graphics.text(font, line, x, getContentY() + i * LINE, YELLOW);
            }
        }
    }

    /**
     * A crash's row: the mod's name and what happened, when; Kiln's call; what Java said; the mod's switch at its right
     * (its jar still in kiln/mods), to turn it back on or off.
     */
    final class CrashRow extends Row {
        final Crashes.Crash crash;
        final Jar jar;
        final Button toggle;

        CrashRow(Crashes.Crash crash) {
            this.crash = crash;
            this.jar = crash.mod().isEmpty() ? null : jarOf(crash.mod());
            this.toggle = jar == null ? null : Button.builder(Component.literal(""), b -> flip(jar)).size(36, 20).build();
        }

        @Override
        int lines() {
            return crash.detail().isEmpty() ? 2 : 3;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return toggle == null ? List.of() : List.of(toggle);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int right = getContentRight() - (toggle == null ? 0 : 40);
            graphics.text(font, fit(crashName(crash) + " " + crashWhat(crash), right - x - 8), x, y,
                    crash.why().equals("stuck") ? YELLOW : RED);
            String call = when(crash) + ", " + (crash.call().isEmpty() ? "Kiln's call isn't known"
                    : "in Kiln's call " + crash.call());
            if (jar != null) {
                call += isOn(jar) ? "; it is on" : "; it is off";
            }
            graphics.text(font, fit(call, right - x - 8), x + 8, y + LINE, GRAY);
            if (!crash.detail().isEmpty()) {
                graphics.text(font, fit(crash.detail(), right - x - 8), x + 8, y + 2 * LINE, GRAY);
            }
            if (toggle != null) {
                toggle.setMessage(Component.literal(isOn(jar) ? "ON" : "OFF"));
                toggle.setSize(36, Math.min(20, getContentHeight()));
                toggle.setPosition(getContentRight() - 36, getContentY());
                toggle.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** A mod's row: name and version, its state; off now, tried; a note; the switch at its right. */
    final class ModRow extends Row {
        final Jar jar;
        final Button toggle;

        ModRow(Jar jar) {
            this.jar = jar;
            this.toggle = Button.builder(Component.literal(""), b -> flip(jar)).size(36, 20).build();
        }

        @Override
        int lines() {
            return note(jar).isEmpty() ? 2 : 3;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int right = getContentRight() - 40;
            String title = jar.version().isEmpty() ? jar.name() : jar.name() + " " + jar.version();
            // A narrow list (a high GUI scale) cuts the texts rather than draw them over each other and the switch.
            int stateX = x + (right - x) / 2;
            graphics.text(font, fit(title, stateX - x - 6), x, y, WHITE);
            Label state = state(jar);
            graphics.text(font, fit(state.text(), right - stateX - 4), stateX, y, state.color());
            Label offNow = offNow(jar);
            graphics.text(font, fit(offNow.text(), stateX - x - 14), x + 8, y + LINE, offNow.color());
            Label tried = tried(jar);
            graphics.text(font, fit(tried.text(), right - stateX - 4), stateX, y + LINE, tried.color());
            String note = note(jar);
            if (!note.isEmpty()) {
                graphics.text(font, fit(note, right - x - 8), x + 8, y + 2 * LINE, GRAY);
            }
            toggle.setMessage(Component.literal(isOn(jar) ? "ON" : "OFF"));
            toggle.setSize(36, Math.min(20, getContentHeight()));
            toggle.setPosition(getContentRight() - 36, getContentY());
            toggle.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** {@code text} cut to {@code width} pixels, with "..." where it was cut. */
    String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + "...") > width) {
            end--;
        }
        return text.substring(0, end) + "...";
    }

    @Override
    public String toString() {
        return "Kiln's overlay";
    }
}
