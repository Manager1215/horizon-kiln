package kiln;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Kiln's Java in the game's process (D-112): the game's command lines and Java's replies as calls, in place of the
 * separate process's stdin and stdout ({@link Host}). The game binds the two methods before {@code Host.main}
 * (kiln/mixins/kiln/jni), with {@code -Dkiln.channel=jni}; {@code -Dkiln.log} names the file the mods' own output goes
 * to, as the separate process's stderr does.
 */
final class Jni {
    private Jni() {}

    /** Whether Java runs in the game's process. */
    static boolean inProcess() {
        return "jni".equals(System.getProperty("kiln.channel"));
    }

    /** The next command line as UTF-8, waiting for it; null when the game let Java go. */
    static native byte[] next();

    /** A reply line as UTF-8. */
    static native void reply(byte[] line);

    /**
     * During a server call the game made on its own thread ({@link Host#direct}): a question to the game, its fields
     * tab-separated and escaped as a line's, answered at once by the waiting game; null outside one.
     */
    static native byte[] ask(byte[] what);

    private static boolean logging;

    /**
     * In the game's process: Java's standard output and error go to Kiln's log file, never the game's own output.
     * Once; the agent calls it first, before Mixin says anything.
     */
    static synchronized void logToFile() {
        String file = System.getProperty("kiln.log");
        if (logging || !inProcess() || file == null || file.isEmpty()) {
            return;
        }
        try {
            PrintStream log = new PrintStream(new FileOutputStream(file), true, StandardCharsets.UTF_8);
            System.setOut(log);
            System.setErr(log);
            logging = true;
        } catch (IOException e) {
            System.err.println("Kiln: can't write " + file + ": " + e);
        }
    }
}
