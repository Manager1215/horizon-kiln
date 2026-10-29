package kiln;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Kiln's Java side, a process of its own beside the game: the game (Kiln's Rust side) writes commands to its
 * stdin, one a line, and reads replies from its stdout. The mods' own {@code System.out} goes to stderr
 * (Kiln's java.log), so a mod can't break the protocol. It ends when the game closes its stdin.
 *
 * <p>A line is a word and tab-separated fields; backslash, tab, newline and carriage return in a field are
 * escaped as {@code \\ \t \n \r}. Commands: {@code load <class>}, {@code tick <n>}. Replies: {@code ready
 * <java version>}, {@code loaded <class>}, {@code failed <class> <why>}, {@code hud <text>}, {@code log
 * <text>}, {@code chat <text>}, and {@code done <command>} after each command.
 */
public final class Host {
    private static PrintStream out;

    private Host() {}

    public static void main(String[] args) throws IOException {
        out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(System.err);
        send("ready", Loader.javaVersion());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String hud = "";
        for (String line; (line = in.readLine()) != null; ) {
            int tab = line.indexOf('\t');
            String command = tab < 0 ? line : line.substring(0, tab);
            String field = tab < 0 ? "" : unescape(line.substring(tab + 1));
            switch (command) {
                case "load" -> {
                    String failed = Loader.load(field);
                    if (failed == null) {
                        send("loaded", field);
                    } else {
                        send("failed", field, failed);
                    }
                }
                case "tick" -> Events.tick(Long.parseLong(field));
                default -> send("log", "unknown command " + command);
            }
            String now = Hud.text();
            if (!now.equals(hud)) {
                hud = now;
                send("hud", now);
            }
            send("done", command);
        }
    }

    /** A reply line, sent at once. */
    static synchronized void send(String kind, String... fields) {
        StringBuilder b = new StringBuilder(kind);
        for (String f : fields) {
            b.append('\t').append(escape(f));
        }
        out.print(b.append('\n'));
        out.flush();
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(switch (n) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
