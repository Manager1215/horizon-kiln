package kiln;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * D-112's crash window: a small Java process the game starts with Kiln's Java, which outlives the game's crash to say
 * which mod stopped it and to ask whether to start again without it.
 *
 * <pre>java -cp kiln-runtime.jar kiln.CrashWatch &lt;the game's pid&gt; &lt;Kiln's folder&gt;</pre>
 *
 * <p>The game writes {@code <Kiln's folder>/running} as Kiln's Java starts (its folder, then its program and arguments,
 * one a line) and deletes it as it closes. When the game's process is gone and the file is still there, the game
 * stopped without closing: a native crash in its process (Java's own included) or a kill. The watcher then reads
 * D-112's breadcrumb ({@code last-call}: the mod and call running then, {@link Breadcrumb}) and Java's own crash report
 * ({@code hs_err_pid<pid>.log}, its Java frames matched to the mods' jars when the breadcrumb names no mod), notes the
 * mod in {@code crashed} for the game's next start and in the Crashes tab's list ({@link Crashes}), and shows a window
 * that asks: start without the mod (its switch in Options > Mods turned off), start with all mods, start in safe mode
 * (no Java mods for that game, D-134), or open the log. An answer clears the note; a window closed
 * without one leaves it, and the next start's title screen asks. With no display, it says it on its output and leaves
 * the note. A stop with no mod and no call of Kiln's named shows nothing: it wasn't a Java mod's.
 *
 * <p>Nothing here but the JDK: the watcher's class path is Kiln's runtime alone.
 */
public final class CrashWatch {
    private CrashWatch() {}

    /** What the watcher found: the mod (id, name), Kiln's call, and what Java's crash report says, each "" if none. */
    record Found(String mod, String name, String call, String cause, String frame) {
        boolean named() {
            return !mod.isEmpty() || !call.isEmpty() || !cause.isEmpty();
        }
    }

    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        Path kiln = Path.of(args[1]).toAbsolutePath();
        ProcessHandle.of(pid).ifPresent(game -> game.onExit().join());
        Path running = kiln.resolve("running");
        if (!Files.exists(running)) {
            return;
        }
        List<String> start = read(running);
        Files.deleteIfExists(running);
        Found found = find(kiln, pid);
        Path log = kiln.resolve("java.log");
        if (!found.named()) {
            System.out.println("Kiln: the game stopped without closing; no Java mod or call of Kiln's was running then.");
            return;
        }
        // The Crashes tab's line (D-134), and the note the next start's title screen asks about: with the mod, or,
        // with only Kiln's call or Java's cause named, whether to start in safe mode.
        String cause = (found.cause() + (found.frame().isEmpty() ? "" : " in " + found.frame())).trim();
        Crashes.record(kiln, "stopped", found.mod(), found.call(), cause);
        Files.writeString(kiln.resolve("crashed"), note(found.mod().isEmpty() ? "-" : found.mod(), found.call(), "stopped"),
                StandardCharsets.UTF_8);
        String report = report(found, kiln, pid);
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println(question(found) + "\n" + report);
            return;
        }
        SwingUtilities.invokeAndWait(() -> show(found, report, start, kiln, log));
    }

    /**
     * The {@code crashed} note: the mod, Kiln's call, and why ({@code stopped}: the game died; {@code stuck}: hung;
     * {@code memory}, {@code stack}: Java turned it off, {@link Breadcrumb#tripped}).
     */
    static String note(String mod, String call, String why) {
        return "mod: " + mod + "\ncall: " + (call == null || call.isEmpty() ? "-" : call.replace('\n', ' ')) + "\nwhy: "
                + why + "\n";
    }

    /** The breadcrumb, Java's crash report and the mods' jars, read for the game that was {@code pid}. */
    static Found find(Path kiln, long pid) {
        String[] last = lastCall(read(kiln.resolve("last-call")));
        String mod = last[0];
        String call = last[1];
        List<String> crash = read(kiln.resolve("hs_err_pid" + pid + ".log"));
        String[] fatal = nativeCrash(crash);
        if (mod.isEmpty()) {
            for (String type : javaFrames(crash)) {
                mod = modOfClass(kiln, type);
                if (!mod.isEmpty()) {
                    break;
                }
            }
        }
        return new Found(mod, mod.isEmpty() ? "" : modName(kiln, mod), call, fatal[0], fatal[1]);
    }

    /**
     * The breadcrumb's busy record ({@code busy: 1}) that names a mod, else one that names only Kiln's call: {mod,
     * call}, "" for none. The records are {@code busy:}, {@code thread:}, {@code call:} and {@code mod:} lines, "-" for
     * none.
     */
    static String[] lastCall(List<String> lines) {
        String[] best = {"", ""};
        boolean busy = false;
        String call = "";
        String mod = "";
        List<String> all = new ArrayList<>(lines);
        all.add("busy: 0");
        for (String line : all) {
            String value = line.substring(line.indexOf(':') + 1).trim();
            value = value.equals("-") ? "" : value;
            if (line.startsWith("busy:")) {
                if (busy && !mod.isEmpty() && best[0].isEmpty()) {
                    best = new String[] {mod, call};
                } else if (busy && best[0].isEmpty() && best[1].isEmpty()) {
                    best = new String[] {"", call};
                }
                busy = value.equals("1");
                call = "";
                mod = "";
            } else if (line.startsWith("call:")) {
                call = value;
            } else if (line.startsWith("mod:")) {
                mod = value;
            }
        }
        return best;
    }

    private static final Pattern SIGNAL = Pattern.compile("^#\\s+((SIG[A-Z]+|EXCEPTION_[A-Z_]+|Internal Error|"
            + "Out of Memory Error)[^,]*?)(?: at pc=.*)?$");

    /** Java's crash report: {what it was (SIGSEGV...), the frame it was in}, "" for none. */
    static String[] nativeCrash(List<String> lines) {
        String cause = "";
        String frame = "";
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = SIGNAL.matcher(lines.get(i));
            if (cause.isEmpty() && m.matches()) {
                cause = m.group(1).trim();
            }
            if (lines.get(i).startsWith("# Problematic frame:") && i + 1 < lines.size()) {
                frame = lines.get(i + 1).replaceFirst("^#\\s*", "").trim();
            }
        }
        return new String[] {cause, frame};
    }

    /** The classes of the Java frames in Java's crash report, innermost first. */
    static List<String> javaFrames(List<String> lines) {
        List<String> types = new ArrayList<>();
        boolean in = false;
        for (String line : lines) {
            if (line.startsWith("Java frames:")) {
                in = true;
                continue;
            }
            if (in && line.isBlank()) {
                break;
            }
            if (!in || !(line.startsWith("j ") || line.startsWith("J "))) {
                continue;
            }
            for (String word : line.split("\\s+")) {
                int paren = word.indexOf('(');
                if (paren > 0) {
                    // A JDK's frame may name its module first (java.base@25/java.lang.Thread.run).
                    String method = word.substring(0, paren);
                    method = method.substring(method.lastIndexOf('/') + 1);
                    int dot = method.lastIndexOf('.');
                    if (dot > 0) {
                        types.add(method.substring(0, dot));
                    }
                    break;
                }
            }
        }
        return types;
    }

    /** The mod whose jar (in {@code mods/}, or carried inside one, {@code cache/jars/}) has class {@code type}, or "". */
    static String modOfClass(Path kiln, String type) {
        String entry = type.replace('.', '/') + ".class";
        for (Path jar : jars(kiln)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (zip.getEntry(entry) != null) {
                    return manifest(zip, "id");
                }
            } catch (IOException e) {
                // not a jar Kiln can read: the next
            }
        }
        return "";
    }

    /** Mod {@code id}'s name, from its jar's {@code fabric.mod.json}; the id when no jar names it. */
    static String modName(Path kiln, String id) {
        for (Path jar : jars(kiln)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (manifest(zip, "id").equals(id)) {
                    String name = manifest(zip, "name");
                    return name.isEmpty() ? id : name;
                }
            } catch (IOException e) {
                // the next
            }
        }
        return id;
    }

    private static List<Path> jars(Path kiln) {
        List<Path> jars = new ArrayList<>();
        for (Path dir : List.of(kiln.resolve("mods"), kiln.resolve("cache").resolve("jars"))) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.toString().endsWith(".jar")).sorted().forEach(jars::add);
            } catch (IOException e) {
                // no such folder
            }
        }
        return jars;
    }

    /** A string field of the jar's {@code fabric.mod.json} (its first), or "": no JSON library on this class path. */
    private static String manifest(ZipFile zip, String field) throws IOException {
        var entry = zip.getEntry("fabric.mod.json");
        if (entry == null) {
            return "";
        }
        try (InputStream in = zip.getInputStream(entry)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            return m.find() ? m.group(1) : "";
        }
    }

    /**
     * The file that starts the next game in safe mode (D-134): no Java mods for that one game, every switch left as it
     * is. The game reads and removes it at its start (runtime.rs {@code SAFE_MODE_FILE}).
     */
    static final String SAFE_MODE = "safe-mode";

    /** What the window asks, as one text. */
    static String question(Found f) {
        String cause = f.cause().isEmpty() ? "" : " (" + f.cause() + ")";
        if (f.mod().isEmpty()) {
            return "The game stopped" + cause + (f.call().isEmpty() ? "" : " in Kiln's call " + f.call())
                    + ". No Java mod was running then, so Kiln can't say which one it was. Start the game again, or in"
                    + " safe mode (without Java mods, for one game)?";
        }
        String mod = f.name().equals(f.mod()) ? f.mod() : f.name() + " (" + f.mod() + ")";
        return "The game stopped" + cause + " while the Java mod " + mod + " was running"
                + (f.call().isEmpty() ? "" : " (" + f.call() + ")") + ". Kiln couldn't catch it, as it catches a mod's"
                + " errors in the game. Start the game again without " + f.name() + "? It stays off until you turn it"
                + " back on in Options > Mods.";
    }

    /** The details under the question: Java's crash report's lines, then the tail of Kiln's Java log. */
    private static String report(Found f, Path kiln, long pid) {
        StringBuilder text = new StringBuilder();
        if (!f.cause().isEmpty()) {
            text.append("Java's crash report: ").append(kiln.resolve("hs_err_pid" + pid + ".log")).append('\n')
                    .append("  ").append(f.cause()).append('\n');
            if (!f.frame().isEmpty()) {
                text.append("  in ").append(f.frame()).append('\n');
            }
            text.append('\n');
        }
        text.append("The end of ").append(kiln.resolve("java.log")).append(":\n");
        text.append(String.join("\n", tail(read(kiln.resolve("java.log")), 40)));
        return text.toString();
    }

    private static void show(Found found, String report, List<String> start, Path kiln, Path log) {
        JFrame frame = new JFrame("Kiln: the game stopped");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (!found.mod().isEmpty()) {
            JButton without = new JButton("Start without " + found.name());
            without.addActionListener(e -> {
                turnOff(kiln.getParent(), found.mod());
                answered(kiln);
                restart(start, kiln);
                frame.dispose();
            });
            buttons.add(without);
            frame.getRootPane().setDefaultButton(without);
        }
        JButton all = new JButton(found.mod().isEmpty() ? "Start the game again" : "Start with all mods");
        all.addActionListener(e -> {
            answered(kiln);
            restart(start, kiln);
            frame.dispose();
        });
        buttons.add(all);
        JButton safe = new JButton("Start in safe mode");
        safe.setToolTipText("Without Java mods, for one game; their switches stay as they are");
        safe.addActionListener(e -> {
            safeMode(kiln);
            answered(kiln);
            restart(start, kiln);
            frame.dispose();
        });
        buttons.add(safe);
        JButton open = new JButton("Open the log");
        open.addActionListener(e -> open(log));
        buttons.add(open);
        JLabel question = new JLabel("<html><body style='width: 560px'>" + html(question(found)) + "</body></html>");
        question.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        JTextArea text = new JTextArea(report, 16, 90);
        text.setEditable(false);
        text.setCaretPosition(0);
        frame.add(question, BorderLayout.NORTH);
        frame.add(new JScrollPane(text), BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        // macOS doesn't bring a program started in the background to the front by itself: the window would open behind
        // whatever the player switches to as the game goes. Where the system lets a program ask (macOS), it asks.
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                java.awt.Desktop.getDesktop().requestForeground(true);
            }
        } catch (RuntimeException e) {
            // Only the window's place: it is open all the same.
        }
        frame.toFront();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The next game starts in safe mode: no Java mods for it ({@link #SAFE_MODE}). */
    static void safeMode(Path kiln) {
        try {
            Files.writeString(kiln.resolve(SAFE_MODE), "on\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Kiln can't write " + kiln.resolve(SAFE_MODE) + ": " + e);
        }
    }

    /** The player answered here: the next start doesn't ask again. */
    static void answered(Path kiln) {
        try {
            Files.deleteIfExists(kiln.resolve("crashed"));
        } catch (IOException e) {
            System.err.println("Kiln can't clear " + kiln.resolve("crashed") + ": " + e);
        }
    }

    /**
     * The game started again as it was: its folder, then its program and arguments. Not while another game runs from
     * this folder (the player started one meanwhile): its {@code running} is there.
     */
    private static void restart(List<String> start, Path kiln) {
        if (start.size() < 2 || Files.exists(kiln.resolve("running"))) {
            return;
        }
        try {
            new ProcessBuilder(start.subList(1, start.size())).directory(Path.of(start.get(0)).toFile())
                    .redirectInput(Redirect.from(nullFile())).redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD).start();
        } catch (IOException e) {
            System.err.println("Kiln can't start the game again: " + e);
        }
    }

    private static java.io.File nullFile() {
        return new java.io.File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
    }

    /** The mod's switch in Options > Mods turned off: {@code mod.kiln_<id>:false} in {@code horizon-mods.txt}. */
    static void turnOff(Path game, String mod) {
        Path list = game.resolve("horizon-mods.txt");
        List<String> lines = new ArrayList<>(read(list));
        String key = "mod.kiln_" + mod;
        lines.removeIf(l -> l.startsWith(key + ":"));
        lines.add(key + ":false");
        try {
            Files.write(list, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Kiln can't turn " + mod + " off: " + e);
        }
    }

    private static void open(Path log) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(log.toFile());
            } else {
                new ProcessBuilder("xdg-open", log.toString()).start();
            }
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("Kiln can't open " + log + ": " + e);
        }
    }

    /** A file's lines, or none; a byte that isn't UTF-8 (a note cut inside a character) reads as a replacement. */
    private static List<String> read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<String> tail(List<String> lines, int n) {
        return lines.subList(Math.max(0, lines.size() - n), lines.size());
    }
}
