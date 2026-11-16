package kiln;

import java.io.IOException;
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
 */
final class Crashes {
    private Crashes() {}

    static final String FILE = "crashes";
    static final int KEPT = 50;

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
            lines.add(new Crash(System.currentTimeMillis(), why, mod, call, detail).line());
            List<String> kept = lines.subList(Math.max(0, lines.size() - KEPT), lines.size());
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
