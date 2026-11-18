package kiln;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kiln's list of the Java mods' crashes (D-134), which the overlay's Crashes tab shows: {@code <Kiln's folder>/crashes},
 * one line a crash, oldest first, the last {@value #KEPT} kept. A line is its time (milliseconds since 1970), why
 * ({@code stopped}: the game died; {@code stuck}: a call hung; {@code memory}, {@code stack}: Java turned the mod off),
 * the mod's id, Kiln's call, and what Java said (the error, or the crash report's cause and frame), tab-separated,
 * {@code -} for none.
 *
 * <p>Each is written as it happens, by what sees it: the crash window ({@link CrashWatch}) for a game that died, the
 * watchdog and the soft switch ({@link Breadcrumb}) for a hang and a mod turned off; and the game itself
 * (runtime.rs {@code record_crash}, the same format) for a game that died with no crash window watching. Nothing here
 * but the JDK: the crash window's class path is Kiln's runtime alone.
 *
 * <p>With each crash, the last {@value #TAIL} lines of the Java log as it was then ({@code crash-logs/<time>.log}, the
 * time as the crash's line has it), so the report the Crashes tab copies is whole after the log has grown or a later
 * game started it again (D-191). They go with their crash's line: only the last {@value #KEPT} are kept.
 */
final class Crashes {
    private Crashes() {}

    static final String FILE = "crashes";
    static final int KEPT = 50;
    /** The folder of the logs' tails, one file a crash. */
    static final String LOGS = "crash-logs";
    /** How many of the log's last lines a crash keeps. */
    static final int TAIL = 100;

    /** One crash: when, why, the mod ("" for none named), Kiln's call and what Java said ("" for none). */
    record Crash(long when, String why, String mod, String call, String detail) {
        static Crash parse(String line) {
            String[] f = line.split("\t", -1);
            if (f.length < 5) {
                return null;
            }
            try {
                return new Crash(Long.parseLong(f[0]), f[1], none(f[2]), none(f[3]), none(f[4]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String line() {
            return when + "\t" + field(why) + "\t" + field(mod) + "\t" + field(call) + "\t" + field(detail);
        }
    }

    private static String none(String field) {
        return field.equals("-") ? "" : field;
    }

    private static String field(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** A crash, now, in {@code kiln}'s list; a list that can't be written only loses this line. */
    static synchronized void record(Path kiln, String why, String mod, String call, String detail) {
        if (kiln == null) {
            return;
        }
        Path file = kiln.resolve(FILE);
        try {
            List<String> lines = new ArrayList<>(lines(file));
            long when = System.currentTimeMillis();
            // Two crashes in one millisecond would share a tail's file: the second is a millisecond later.
            for (String line : lines) {
                Crash c = Crash.parse(line);
                if (c != null && c.when() >= when) {
                    when = c.when() + 1;
                }
            }
            keepTail(kiln, when);
            lines.add(new Crash(when, why, mod, call, detail).line());
            List<String> kept = lines.subList(Math.max(0, lines.size() - KEPT), lines.size());
            forget(kiln, lines.subList(0, lines.size() - kept.size()));
            Path part = kiln.resolve(FILE + ".part");
            Files.write(part, kept, StandardCharsets.UTF_8);
            try {
                Files.move(part, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            // The crash is handled all the same; only its line in the Crashes tab is lost.
        }
    }

    /**
     * The Java log this Java writes (the in-process one's {@code kiln.log}, the separate one's from the same property),
     * else {@code java.log}: the crash window's case, which reads the log of the Java in the game that died.
     */
    static Path log(Path kiln) {
        String file = System.getProperty("kiln.log");
        return file == null || file.isEmpty() ? kiln.resolve("java.log") : Path.of(file);
    }

    /** The log's last lines now, as a crash at {@code when} keeps them; none kept when there is no log. */
    private static void keepTail(Path kiln, long when) {
        List<String> tail = tail(log(kiln), TAIL);
        if (tail.isEmpty()) {
            return;
        }
        try {
            Path logs = Files.createDirectories(kiln.resolve(LOGS));
            Files.write(logs.resolve(when + ".log"), tail, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // The crash's line goes in all the same, its report without the log's tail.
        }
    }

    /** The tails of the crashes no longer kept ({@code gone}, their lines) go too. */
    private static void forget(Path kiln, List<String> gone) {
        for (String line : gone) {
            Crash c = Crash.parse(line);
            if (c != null) {
                try {
                    Files.deleteIfExists(kiln.resolve(LOGS).resolve(c.when() + ".log"));
                } catch (IOException | RuntimeException e) {
                    // An old file left behind is only a few kilobytes.
                }
            }
        }
    }

    /** The log's lines kept with {@code crash}; none when it kept none (a crash recorded before Kiln kept them). */
    static List<String> keptTail(Path kiln, Crash crash) {
        try {
            return Files.readAllLines(kiln.resolve(LOGS).resolve(crash.when() + ".log"), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** The last {@code n} lines of {@code file}, read from its end (a log may be large); none if it can't be read. */
    static List<String> tail(Path file, int n) {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long length = f.length();
            // 100 lines of a Java log, stack traces' among them, fit in 64 KiB; a longer line is cut at its start.
            long start = Math.max(0, length - (64 << 10));
            byte[] bytes = new byte[(int) (length - start)];
            f.seek(start);
            f.readFully(bytes);
            List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
            if (start > 0 && !lines.isEmpty()) {
                // The first line read is most likely a part of one.
                lines = lines.subList(1, lines.size());
            }
            return List.copyOf(lines.subList(Math.max(0, lines.size() - n), lines.size()));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** {@code kiln}'s list, newest first. */
    static List<Crash> read(Path kiln) {
        List<Crash> all = new ArrayList<>();
        for (String line : lines(kiln.resolve(FILE))) {
            Crash c = Crash.parse(line);
            if (c != null) {
                all.add(c);
            }
        }
        Collections.reverse(all);
        return all;
    }

    private static List<String> lines(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
