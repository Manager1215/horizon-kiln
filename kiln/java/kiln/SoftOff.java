package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/**
 * For the overlay's Mods tab (D-113, {@code internal/soft-switch-spike.md} section 5), per mod jar: whether the soft
 * switch ({@link SoftSwitch}) can turn it off now, and whether horizon-kiln.cc lists it as tried in the game.
 *
 * <p>The class ({@code safe}: off now, fully; {@code partial}: off now, but its content stays until the next game;
 * {@code restart}: fully off only after a restart) comes from the catalogue's copy Kiln ships
 * ({@code /kiln/catalogue.json} in kiln-runtime.jar, written by {@code tools/catalogue-snapshot.py}) by the jar's
 * SHA-256. For a jar the copy hasn't, or when the copy was made by an older reading than Kiln's ({@code rules}), Kiln
 * reads the jar itself ({@link SoftOffScan}, {@code tools/soft-off.py}'s reading), once per jar.
 *
 * <p>A fresher copy of the catalogue in {@code <kiln>/cache/catalogue.json} (the same file, from a refresh online)
 * is read instead when its {@code generated} date is newer than the shipped one's; an older one is left alone.
 * Nothing here goes online: {@link CatalogueRefresh} fetches that file (D-128).
 */
public final class SoftOff {
    /** Where a class came from: the catalogue's copy (by the jar's SHA-256), or Kiln's own reading of the jar. */
    public enum Source { CATALOGUE, SCAN }

    /**
     * What the overlay shows for one jar.
     *
     * @param sha256 the jar's SHA-256, hex
     * @param cls {@code safe}, {@code partial} or {@code restart}; {@code unreadable} for a jar that isn't one
     * @param why why, in the reading's words ("registers blocks, items or entities (they stay, inert)")
     * @param source where the class came from
     * @param tried horizon-kiln.cc's word on the mod: {@code works}, {@code doesnt-work}, {@code in-development},
     *     {@code tried-not-working} (tried, not working yet), or {@code not-tried}
     * @param sameJar whether the catalogue tried this very jar (by SHA-256), not another version of the mod
     * @param version the version the catalogue tried ("" when not tried)
     * @param kiln the Kiln version it was checked with ("" when not tried)
     */
    public record Answer(String sha256, String cls, String why, Source source, String tried, boolean sameJar,
                         String version, String kiln) {
        /** The overlay's words for the class, on the "Off now" column. */
        public String offNow() {
            return switch (cls) {
                case "safe" -> "yes";
                case "partial" -> "partly";
                case "restart" -> "fully off after a restart";
                default -> "unknown";
            };
        }
    }

    /** The catalogue's copy in use: the shipped one, or a newer one from the cache folder. */
    record Catalogue(String generated, int rules, JsonObject jars, JsonObject mods) {
        static final Catalogue EMPTY = new Catalogue("", 0, new JsonObject(), new JsonObject());

        static Catalogue parse(Reader in) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            return new Catalogue(str(o, "generated"), o.has("rules") ? o.get("rules").getAsInt() : 0,
                    o.has("jars") ? o.getAsJsonObject("jars") : new JsonObject(),
                    o.has("mods") ? o.getAsJsonObject("mods") : new JsonObject());
        }
    }

    private static volatile Catalogue catalogue;
    private static volatile Path cacheDir;
    private static final Map<String, SoftOffScan.Reading> READ = new ConcurrentHashMap<>();

    private SoftOff() {}

    /** Where a refreshed copy may be: {@code <kiln dir>/cache}. Read again on the next answer. */
    public static synchronized void use(Path kilnDir) {
        cacheDir = kilnDir == null ? null : kilnDir.resolve("cache");
        catalogue = null;
    }

    /** The copy in use, read once: the newer of the shipped one and {@code cache/catalogue.json}. */
    static synchronized Catalogue catalogue() {
        if (catalogue != null) {
            return catalogue;
        }
        Catalogue shipped = Catalogue.EMPTY;
        try (InputStream in = SoftOff.class.getResourceAsStream("/kiln/catalogue.json")) {
            if (in != null) {
                shipped = Catalogue.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Kiln] the catalogue's copy in kiln-runtime.jar is unreadable: " + e);
        }
        Catalogue chosen = shipped;
        Path fresher = cacheDir == null ? null : cacheDir.resolve("catalogue.json");
        if (fresher != null && Files.isRegularFile(fresher)) {
            try (Reader in = Files.newBufferedReader(fresher, StandardCharsets.UTF_8)) {
                Catalogue cached = Catalogue.parse(in);
                if (cached.generated().compareTo(shipped.generated()) > 0) {
                    chosen = cached;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[Kiln] " + fresher + " is unreadable, the shipped copy stands: " + e);
            }
        }
        catalogue = chosen;
        return chosen;
    }

    /** The date of the catalogue's copy in use ("2026-11-10"), for the overlay to say how fresh it is. */
    public static String generated() {
        return catalogue().generated();
    }

    /** Reads every jar now, so the overlay's answers are at hand: Kiln calls it at start, off the game's thread. */
    public static void prepare(Collection<Path> jars) {
        for (Path jar : jars) {
            try {
                of(jar);
            } catch (RuntimeException e) {
                System.err.println("[Kiln] soft-off reading of " + jar.getFileName() + " failed: " + e);
            }
        }
    }

    /** The overlay's answer for the jar. */
    public static Answer of(Path jar) {
        String sha;
        try {
            sha = sha256(jar);
        } catch (IOException e) {
            return new Answer("", "unreadable", e.toString(), Source.SCAN, "not-tried", false, "", "");
        }
        Catalogue c = catalogue();
        JsonObject hit = c.jars().has(sha) ? c.jars().getAsJsonObject(sha) : null;
        JsonObject mod = hit != null ? object(c.mods(), str(hit, "mod")) : object(c.mods(), normalized(modId(jar)));
        String cls;
        String why;
        Source source;
        if (hit != null && c.rules() >= SoftOffScan.RULES) {
            cls = str(hit, "class");
            why = str(hit, "why");
            source = Source.CATALOGUE;
        } else {
            SoftOffScan.Reading r = READ.computeIfAbsent(sha, k -> {
                try {
                    return SoftOffScan.read(jar);
                } catch (IOException | RuntimeException e) {
                    return new SoftOffScan.Reading("unreadable", List.of(e.toString()));
                }
            });
            cls = r.cls();
            why = r.whyText();
            source = Source.SCAN;
        }
        if (mod == null) {
            return new Answer(sha, cls, why, source, "not-tried", false, "", "");
        }
        return new Answer(sha, cls, why, source, str(mod, "tried"), hit != null, str(mod, "version"),
                str(mod, "kiln"));
    }

    // ---- Helpers ----

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] block = new byte[1 << 16];
            for (int n; (n = in.read(block)) > 0; ) {
                digest.update(block, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** The jar's mod id ({@code fabric.mod.json}'s {@code id}), or "". */
    static String modId(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return "";
            }
            try (Reader in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonElement e = JsonParser.parseReader(in);
                return e.isJsonObject() ? str(e.getAsJsonObject(), "id") : "";
            }
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** A name as the copy keys its mods: letters and digits only, lower case ({@code tools/soft-off.py}'s norm). */
    static String normalized(String name) {
        StringBuilder out = new StringBuilder();
        name.toLowerCase(java.util.Locale.ROOT).codePoints().filter(Character::isLetterOrDigit)
                .forEach(out::appendCodePoint);
        return out.toString();
    }

    private static JsonObject object(JsonObject o, String key) {
        return key != null && !key.isEmpty() && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key)
                : null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    /**
     * The answers as JSON lines, one a jar, for Kiln's tests and a look by hand:
     * {@code java -cp kiln-runtime.jar:lib/* kiln.SoftOff [--kiln DIR] JAR...}.
     */
    public static void main(String[] args) {
        List<Path> jars = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--kiln") && i + 1 < args.length) {
                use(Path.of(args[++i]));
            } else {
                jars.add(Path.of(args[i]));
            }
        }
        System.out.println("{\"generated\":\"" + generated() + "\",\"rules\":" + catalogue().rules() + "}");
        for (Path jar : jars) {
            Answer a = of(jar);
            JsonObject o = new JsonObject();
            o.addProperty("jar", jar.getFileName().toString());
            o.addProperty("sha256", a.sha256());
            o.addProperty("class", a.cls());
            o.addProperty("why", a.why());
            o.addProperty("source", a.source().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("offNow", a.offNow());
            o.addProperty("tried", a.tried());
            o.addProperty("sameJar", a.sameJar());
            o.addProperty("version", a.version());
            o.addProperty("kiln", a.kiln());
            System.out.println(o);
        }
    }
}
