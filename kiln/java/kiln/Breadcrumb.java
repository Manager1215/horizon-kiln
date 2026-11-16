package kiln;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * D-112's breadcrumb: around each call into a mod, Kiln notes which mod and which call runs, so a crash nothing can
 * catch (a native fault, a hang) still names the mod. The note is a file mapped into memory ({@code -Dkiln.last_call},
 * the game's Java only: {@code <Kiln's folder>/last-call}), so writing it costs no system call and it outlives the
 * process: {@link CrashWatch} reads it after the game is gone, and the game's next start when no watcher did.
 *
 * <p>The file is {@value #SLOTS} records of {@value #SIZE} bytes, one per thread that calls into mods (Kiln's Java
 * thread, the server's thread...), each four lines padded with spaces: {@code busy: 1} (0 once the call returned),
 * {@code thread: <name>}, {@code call: <what>}, {@code mod: <id>} ("-" for none). A call into a mod costs a thread-local
 * read, two comparisons and one byte written; the text is written only when the mod or the call changes.
 *
 * <p>The same notes feed the watchdog ({@link #watch}): a thread that stays in one call for {@value #STUCK} seconds is
 * said in Kiln's log (with its stack) and in the chat, and noted in {@code <Kiln's folder>/crashed} until it comes
 * back, so a game closed or killed while a mod hung asks at its next start whether to start without it.
 */
public final class Breadcrumb {
    static final int SLOTS = 8;
    static final int SIZE = 256;
    /** Where each line starts in a record; {@code busy}'s digit is at {@link #BUSY}. */
    private static final int BUSY = 6;
    private static final int THREAD = 8;
    private static final int CALL = 56;
    private static final int MOD = 192;
    /** Seconds in one call before the watchdog says so. */
    static final int STUCK = 10;
    /** Calls in calls kept by name (a mod's code calling a guarded handler...); deeper ones count but aren't named. */
    private static final int DEPTH = 16;

    private static final Path PATH = path();
    private static final MappedByteBuffer FILE = open(PATH);
    private static final Slot[] ALL = new Slot[SLOTS];
    private static final ThreadLocal<Slot> SLOT = ThreadLocal.withInitial(Breadcrumb::claim);
    private static boolean watching;

    private Breadcrumb() {}

    /** One thread's calls into mods: its record in the file, and what the watchdog reads. */
    static final class Slot {
        /** The record's offset in the file, -1 when every record is taken (nothing written, still watched). */
        final int at;
        final Thread thread;
        final String[] mods = new String[DEPTH];
        final String[] calls = new String[DEPTH];
        /** Calls in progress; written by the thread only. */
        int depth;
        /** Calls begun, ever: the watchdog sees a thread move on by it. */
        int enters;
        /** What the record says now. */
        private String shownMod;
        private String shownCall;
        /** The watchdog's own: {@link #enters} as last seen, the seconds since, and whether it said so. */
        int seen = -1;
        int still;
        boolean said;

        Slot(int at, Thread thread) {
            this.at = at;
            this.thread = thread;
        }

        void show(String mod, String call) {
            if (at < 0) {
                return;
            }
            if (!call.equals(shownCall)) {
                line(at + CALL, "call: ", call, MOD - CALL);
                shownCall = call;
            }
            if (!mod.equals(shownMod)) {
                line(at + MOD, "mod: ", mod, SIZE - MOD);
                shownMod = mod;
            }
            FILE.put(at + BUSY, (byte) '1');
        }

        void idle() {
            if (at >= 0) {
                FILE.put(at + BUSY, (byte) '0');
            }
        }

        /** The innermost call's mod that is named, else "". */
        String mod() {
            for (int i = Math.min(depth, DEPTH) - 1; i >= 0; i--) {
                if (mods[i] != null && !mods[i].isEmpty()) {
                    return mods[i];
                }
            }
            return "";
        }

        /** The innermost call that is named, else "". */
        String call() {
            int i = Math.min(depth, DEPTH) - 1;
            return i >= 0 && calls[i] != null ? calls[i] : "";
        }
    }

    /**
     * A call into {@code listener}'s mod (the jar its class came from) begins: {@code call} names it. Returns what
     * {@link #leave} takes after it.
     */
    public static int enter(Object listener, String call) {
        return enter(listener == null ? "" : SoftSwitch.modOf(listener.getClass()), call);
    }

    /**
     * A call into mod {@code mod} ("" when no mod's or not known) begins: {@code call} names it, as Kiln's log and the
     * crash window say it. Returns the depth before it, for {@link #leave}.
     */
    public static int enter(String mod, String call) {
        Slot s = SLOT.get();
        int depth = s.depth;
        if (depth < DEPTH) {
            s.mods[depth] = mod;
            s.calls[depth] = call;
        }
        s.depth = depth + 1;
        s.enters++;
        if (FILE != null) {
            s.show(mod, call);
        }
        return depth;
    }

    /**
     * The call {@link #enter} began ended: {@code depth} is what it returned. A call left out by a throw nothing caught
     * is ended by the one around it.
     */
    public static void leave(int depth) {
        Slot s = SLOT.get();
        if (depth < 0 || depth >= s.depth) {
            return;
        }
        s.depth = depth;
        if (FILE == null) {
            return;
        }
        if (depth == 0) {
            s.idle();
        } else if (depth <= DEPTH) {
            s.show(s.mods[depth - 1] == null ? "" : s.mods[depth - 1], s.calls[depth - 1] == null ? "" : s.calls[depth - 1]);
        }
    }

    /** This thread's innermost mod in a call now, or "". */
    static String currentMod() {
        return SLOT.get().mod();
    }

    /**
     * The mod whose code {@code thrown} came from: the first frame of its stack whose class is a mod's (the jar it came
     * from, {@link SoftSwitch#modOf}), or "" when none is. For a throw that reached Kiln past every boundary that knows
     * its mod: a stack overflow's frames are the recursion's, an exhausted heap's where the last allocation failed.
     */
    static String modOf(StackTraceElement[] stack) {
        ClassLoader loader = Breadcrumb.class.getClassLoader();
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("kiln.")) {
                continue;
            }
            try {
                String mod = SoftSwitch.modOf(Class.forName(name, false, loader));
                if (!mod.isEmpty()) {
                    return mod;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // a hidden class or one of another loader: the next frame
            }
        }
        return "";
    }

    private static Path path() {
        String file = System.getProperty("kiln.last_call");
        return file == null || file.isEmpty() ? null : Path.of(file);
    }

    private static MappedByteBuffer open(Path path) {
        if (path == null) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.setLength(SLOTS * SIZE);
            MappedByteBuffer file = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, SLOTS * SIZE);
            // Nothing of the last game's notes stays: every record idle and empty.
            for (int i = 0; i < SLOTS; i++) {
                record(file, i * SIZE, "");
            }
            return file;
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: no breadcrumb at " + path + ": " + e);
            return null;
        }
    }

    /** A record idle, for {@code thread}. */
    private static void record(MappedByteBuffer file, int at, String thread) {
        put(file, at, "busy: ", "0", THREAD);
        put(file, at + THREAD, "thread: ", thread, CALL - THREAD);
        put(file, at + CALL, "call: ", "", MOD - CALL);
        put(file, at + MOD, "mod: ", "", SIZE - MOD);
    }

    private static void line(int at, String label, String value, int width) {
        put(FILE, at, label, value, width);
    }

    /** {@code label} and {@code value} ("-" when empty) at {@code at}, cut or padded with spaces to a line of {@code width}. */
    private static void put(MappedByteBuffer file, int at, String label, String value, int width) {
        byte[] line = new byte[width];
        java.util.Arrays.fill(line, (byte) ' ');
        byte[] text = (label + (value.isEmpty() ? "-" : value.replace('\n', ' '))).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(text, 0, line, 0, Math.min(text.length, width - 1));
        line[width - 1] = '\n';
        file.put(at, line, 0, width);
    }

    /** This thread's slot: a free record (one whose thread ended), or none when all are taken. */
    private static Slot claim() {
        Thread me = Thread.currentThread();
        synchronized (ALL) {
            if (!watching) {
                watching = true;
                Thread dog = new Thread(Breadcrumb::watch, "Kiln watchdog");
                dog.setDaemon(true);
                dog.start();
            }
            for (int i = 0; i < SLOTS; i++) {
                if (ALL[i] == null || !ALL[i].thread.isAlive()) {
                    Slot s = new Slot(FILE == null ? -1 : i * SIZE, me);
                    if (FILE != null) {
                        record(FILE, s.at, me.getName());
                    }
                    ALL[i] = s;
                    return s;
                }
            }
        }
        return new Slot(-1, me);
    }

    /**
     * The watchdog: once a second, each thread in a call that began no call since the last look has been in it a second
     * more. At {@value #STUCK} seconds it is said once (Kiln's log with the thread's stack, the chat, the {@code crashed}
     * note); when the thread moves on, the log says so and the note goes.
     */
    private static void watch() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            Slot[] slots;
            synchronized (ALL) {
                slots = ALL.clone();
            }
            for (Slot s : slots) {
                if (s == null) {
                    continue;
                }
                try {
                    look(s);
                } catch (RuntimeException | OutOfMemoryError e) {
                    // The watchdog goes on: the next look may say it.
                }
            }
        }
    }

    private static void look(Slot s) {
        int enters = s.enters;
        boolean busy = s.depth > 0 && s.thread.isAlive();
        if (!busy || enters != s.seen) {
            if (s.said) {
                s.said = false;
                // The note first: what the chat says is so by the time it says it.
                stuck(null, null);
                Host.send("log", "Kiln's watchdog: thread " + s.thread.getName() + " moved on after " + s.still
                        + " s in one call");
                Host.send("chat", "Kiln: the game goes on (the call that was stuck returned after " + s.still + " s)");
            }
            s.seen = enters;
            s.still = 0;
            return;
        }
        s.still++;
        if (s.still < STUCK || s.said) {
            return;
        }
        s.said = true;
        StackTraceElement[] stack = s.thread.getStackTrace();
        String mod = s.mod();
        if (mod.isEmpty()) {
            mod = modOf(stack);
        }
        String call = s.call();
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(stack.length, 40); i++) {
            trace.append("\n\tat ").append(stack[i]);
        }
        stuck(mod, call);
        Host.send("log", "Kiln's watchdog: thread " + s.thread.getName() + " has been in one call for " + STUCK + " s"
                + (call.isEmpty() ? "" : " (" + call + ")") + (mod.isEmpty() ? "" : ", in the mod " + mod) + trace);
        Host.send("chat", (mod.isEmpty() ? "Kiln: a Java call" : "Kiln: the mod " + mod) + " has been stuck for "
                + STUCK + " s" + (call.isEmpty() ? "" : " in " + call) + ", so the game may not respond. If it doesn't"
                + " come back, close the game: its next start asks whether to start without "
                + (mod.isEmpty() ? "it" : mod) + ".");
    }

    /**
     * The {@code crashed} note for a mod that was turned off because it ran Java out of memory ({@code memory}) or
     * overflowed its stack ({@code stack}): the next start's title screen asks whether to start without it, as it
     * does for a crash. It replaces a note already there; a hang's note that comes and goes after it leaves it.
     */
    static void tripped(String mod, String call, String why) {
        tripped(mod, call, why, "");
    }

    /** As {@link #tripped(String, String, String)}, with what Java threw ({@code detail}) for the Crashes tab (D-134). */
    static void tripped(String mod, String call, String why, String detail) {
        if (PATH == null || PATH.getParent() == null || mod == null || mod.isEmpty()) {
            return;
        }
        Crashes.record(PATH.getParent(), why, mod, call, detail);
        Path note = PATH.getParent().resolve("crashed");
        try {
            String text = CrashWatch.note(mod, call, why);
            synchronized (Breadcrumb.class) {
                tripNote = text;
                Files.writeString(note, text, StandardCharsets.UTF_8);
            }
        } catch (IOException | OutOfMemoryError e) {
            // The mod is off all the same; only the question at the next start is lost.
        }
    }

    /** The last trip's {@code crashed} note ({@link #tripped}), which a hang's note gives back when it goes. */
    private static String tripNote;

    /**
     * The {@code crashed} note beside the breadcrumb: a mod stuck in {@code call}, or none (null) once it moved on,
     * when the last trip's note comes back (the next start asks about that mod still).
     */
    private static synchronized void stuck(String mod, String call) {
        if (PATH == null || PATH.getParent() == null) {
            return;
        }
        Path note = PATH.getParent().resolve("crashed");
        if (mod != null) {
            Crashes.record(PATH.getParent(), "stuck", mod, call, "");
        }
        try {
            if (mod == null && tripNote != null) {
                Files.writeString(note, tripNote, StandardCharsets.UTF_8);
            } else if (mod == null) {
                Files.deleteIfExists(note);
            } else if (!mod.isEmpty()) {
                Files.writeString(note, CrashWatch.note(mod, call, "stuck"), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Host.send("log", "Kiln's watchdog can't write " + note + ": " + e);
        }
    }
}
