package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The catalogue's copy, refreshed online (D-128): at Kiln's start, off the game's thread, at most once a day, Kiln
 * fetches the same file it ships ({@code kiln/java/kiln/catalogue.json}, which the catalogue publishes on its branch)
 * and keeps it as {@code <kiln>/cache/catalogue.json}, which {@link SoftOff} reads when it is newer than the shipped
 * one.
 *
 * <p>The request is a plain GET of a fixed address: no query, no cookies, the User-Agent {@code Kiln/<version>}, and
 * nothing about the player's mods (the matching is {@link SoftOff}'s, here). The file is taken only when it is the
 * catalogue's copy ({@code generated} a date, {@code rules} a reading, every jar's class one Kiln knows) and newer than
 * the copy in use; it is written to a file beside and moved into place, so a reader sees the old file or the new one.
 * Anything that goes wrong leaves the old copy and says so in Kiln's log; nothing waits for it.
 *
 * <p>Off with a file {@code catalogue_refresh} in Kiln's folder ({@code kiln.dir}, beside {@code kiln-runtime.jar})
 * that says {@code off}, or with {@code KILN_CATALOGUE_REFRESH=off} (Kiln's game proofs set it, so no test goes
 * online).
 */
public final class CatalogueRefresh {
    /** Where the catalogue publishes its copy: the public repository's catalogue branch (catalogue.yml). */
    static final String URL = "https://raw.githubusercontent.com/Manager1215/horizon-kiln/catalogue/catalogue.json";
    /** The setting's file in Kiln's folder, and the environment variable that overrides it. */
    static final String SETTING = "catalogue_refresh";
    static final String ENV = "KILN_CATALOGUE_REFRESH";
    /** When the last request went out, in milliseconds since 1970, in the cache folder. */
    static final String STAMP = "catalogue.checked";
    static final Duration EVERY = Duration.ofDays(1);
    /** The shipped copy is about 0.3 MB; anything past this isn't the catalogue's copy. */
    static final int MAX_BYTES = 16 << 20;
    private static final Set<String> CLASSES = Set.of("safe", "partial", "restart");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** What one refresh did, for Kiln's log and its tests. */
    public enum Outcome {
        /** The setting is off: nothing went out. */
        OFF,
        /** The last request was less than a day ago: nothing went out. */
        NOT_DUE,
        /** A newer copy came and is in place. */
        TAKEN,
        /** The copy that came is no newer than the one in use: left alone. */
        STALE,
        /** What came isn't the catalogue's copy (not JSON, or not its shape): left alone. */
        REFUSED,
        /** No answer, or not 200: left alone. */
        FAILED
    }

    private static volatile Thread running;

    private CatalogueRefresh() {}

    /**
     * The game's start (the {@code catalogue} command, from the window's game only): one refresh on a daemon thread of
     * its own, so the game never waits for it.
     */
    public static synchronized void start(Path kilnDir, String version) {
        if (running != null && running.isAlive()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Outcome o = refresh(kilnDir, URI.create(URL), version, System.currentTimeMillis());
                if (o != Outcome.NOT_DUE && o != Outcome.OFF) {
                    System.err.println("[Kiln] the catalogue's copy online: " + o.name().toLowerCase(Locale.ROOT));
                }
            } catch (RuntimeException | LinkageError e) {
                System.err.println("[Kiln] the catalogue's copy online: failed: " + e);
            }
        }, "Kiln catalogue refresh");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        running = t;
        t.start();
    }

    /** Whether the refresh is on: on, unless the environment variable or the setting's file says off. */
    public static boolean enabled(Path kilnDir) {
        if (off(System.getenv(ENV))) {
            return false;
        }
        Path file = kilnDir.resolve(SETTING);
        try {
            return !Files.isRegularFile(file) || !off(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    /** Turns the refresh on or off (for the overlay's switch): writes the setting's file. */
    public static void setEnabled(Path kilnDir, boolean on) throws IOException {
        Files.createDirectories(kilnDir);
        Files.writeString(kilnDir.resolve(SETTING), on ? "on\n" : "off\n", StandardCharsets.UTF_8);
    }

    private static boolean off(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("off") || v.equals("false") || v.equals("0") || v.equals("no");
    }

    /**
     * One refresh, on the caller's thread: {@code now} is the time in milliseconds since 1970. Never throws for what
     * the network or the file system does.
     */
    static Outcome refresh(Path kilnDir, URI uri, String version, long now) {
        if (!enabled(kilnDir)) {
            return Outcome.OFF;
        }
        Path cache = kilnDir.resolve("cache");
        Path stamp = cache.resolve(STAMP);
        if (!due(stamp, now)) {
            return Outcome.NOT_DUE;
        }
        if (!allowed(uri)) {
            System.err.println("[Kiln] the catalogue's address isn't HTTPS: " + uri);
            return Outcome.FAILED;
        }
        try {
            Files.createDirectories(cache);
            // Before the request: a server that never answers is asked again tomorrow, not at every start.
            Files.writeString(stamp, Long.toString(now) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Kiln] " + cache + " isn't writable, no refresh of the catalogue's copy: " + e);
            return Outcome.FAILED;
        }
        byte[] body;
        try {
            body = fetch(uri, version);
        } catch (IOException e) {
            System.err.println("[Kiln] the catalogue's copy didn't come: " + e);
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
        if (body == null) {
            return Outcome.FAILED;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        String why = invalid(text);
        if (why != null) {
            System.err.println("[Kiln] the catalogue's copy online isn't one: " + why);
            return Outcome.REFUSED;
        }
        String generated = JsonParser.parseString(text).getAsJsonObject().get("generated").getAsString();
        SoftOff.use(kilnDir);
        if (generated.compareTo(SoftOff.generated()) <= 0) {
            return Outcome.STALE;
        }
        Path target = cache.resolve("catalogue.json");
        Path part = cache.resolve("catalogue.json.part");
        try {
            Files.write(part, body);
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[Kiln] the catalogue's copy wasn't written, the old one stands: " + e);
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // Overwritten next time.
            }
            return Outcome.FAILED;
        }
        // The overlay's next answer reads the new copy.
        SoftOff.use(kilnDir);
        return Outcome.TAKEN;
    }

    /** Whether a day has passed since the last request (or the stamp is unreadable, or from the future). */
    static boolean due(Path stamp, long now) {
        try {
            long last = Long.parseLong(Files.readString(stamp, StandardCharsets.UTF_8).trim());
            return now - last >= EVERY.toMillis() || now < last;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    /** HTTPS; plain HTTP only to this machine (Kiln's tests' local server). */
    static boolean allowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) {
            return true;
        }
        if (!scheme.equals("http") || uri.getHost() == null) {
            return false;
        }
        try {
            return InetAddress.getByName(uri.getHost()).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }

    /** The body of a plain GET, or null when the answer isn't 200; past {@link #MAX_BYTES}, an IOException. */
    private static byte[] fetch(URI uri, String version) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Kiln/" + (version == null || version.isBlank() ? "unknown" : version.strip()))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                System.err.println("[Kiln] the catalogue's copy online: HTTP " + response.statusCode());
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[1 << 16];
            for (int n; (n = in.read(block)) > 0; ) {
                if (out.size() + n > MAX_BYTES) {
                    throw new IOException("larger than " + MAX_BYTES + " bytes");
                }
                out.write(block, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Why {@code text} isn't the catalogue's copy, or null when it is. */
    static String invalid(String text) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseReader(new StringReader(text));
            if (!e.isJsonObject()) {
                return "not a JSON object";
            }
            o = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return "not JSON: " + e.getMessage();
        }
        JsonElement generated = o.get("generated");
        if (generated == null || !generated.isJsonPrimitive() || !DATE.matcher(generated.getAsString()).matches()) {
            return "no date in \"generated\"";
        }
        JsonElement rules = o.get("rules");
        if (rules == null || !rules.isJsonPrimitive() || !rules.getAsJsonPrimitive().isNumber()
                || rules.getAsDouble() != Math.rint(rules.getAsDouble()) || rules.getAsInt() < 1) {
            return "no reading in \"rules\"";
        }
        if (!(o.get("jars") instanceof JsonObject jars) || !(o.get("mods") instanceof JsonObject mods)) {
            return "no \"jars\" or \"mods\"";
        }
        if (jars.size() == 0 && mods.size() == 0) {
            return "empty";
        }
        for (var entry : jars.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject jar)) {
                return "jar " + entry.getKey() + " isn't an object";
            }
            JsonElement cls = jar.get("class");
            if (cls == null || !cls.isJsonPrimitive() || !CLASSES.contains(cls.getAsString())) {
                return "jar " + entry.getKey() + " has a class Kiln doesn't know";
            }
        }
        for (var entry : mods.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                return "mod " + entry.getKey() + " isn't an object";
            }
        }
        return null;
    }

    /**
     * One refresh now, for Kiln's tests and a look by hand: {@code java -cp kiln-runtime.jar:lib/*
     * kiln.CatalogueRefresh --kiln DIR [--url URL] [--version V] [--now MILLIS]}; prints the outcome.
     */
    public static void main(String[] args) {
        Path kiln = Path.of(".");
        String url = URL;
        String version = "";
        long now = System.currentTimeMillis();
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--kiln" -> kiln = Path.of(args[i + 1]);
                case "--url" -> url = args[i + 1];
                case "--version" -> version = args[i + 1];
                case "--now" -> now = Long.parseLong(args[i + 1]);
                default -> throw new IllegalArgumentException(args[i]);
            }
        }
        System.out.println(refresh(kiln, URI.create(url), version, now).name().toLowerCase(Locale.ROOT));
    }
}
