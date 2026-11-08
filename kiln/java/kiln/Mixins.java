package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.transformers.MixinClassWriter;

/**
 * Kiln's Mixin: the mods' mixins applied by the Mixin library itself (FabricMC's Mixin and MixinExtras, the jars Fabric
 * Loader ships), with Kiln as the place it runs in (D-089; D-073 and D-081 were Kiln's own Mixin before). Kiln's
 * runtime jar is a Java agent ({@code -javaagent}, this class's {@link #premain}): before {@code kiln.Host} starts, it
 * starts Mixin with {@link MixinService}, gives it every mod's mixin configs (fabric.mod.json's {@code mixins}, the
 * client's, never a dedicated server's) as Fabric Loader does, and from then on hands Mixin each class the JVM loads.
 *
 * <p>Kiln's own rules over Mixin's: a mixin Mixin can't apply is left out, said once in Kiln's log, and its class
 * patched again without it ({@link MixinErrors}): the game never stops for a mod's mixin. Kiln's stand-ins get the
 * members the mixins name and they lack ({@link MixinStandIns}), and a mod's handler that throws doesn't stop the method
 * it is in ({@link MixinGuards}).
 *
 * <p>Nothing here may load a stand-in or {@code kiln.Host} before the agent is in place, or that class would never
 * be patched: the agent uses only the JDK, ASM, Gson and Mixin until {@link #started()}.
 */
public final class Mixins implements ClassFileTransformer {
    /** Packages Mixin never patches: the JDK, ASM, Mixin and MixinExtras themselves (and Kiln's own, below). */
    private static final List<String> SKIPPED = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/",
            "org/objectweb/asm/", "org/spongepowered/", "com/llamalad7/mixinextras/");
    /** Fabric Loader's versions and the Mixin behaviour each set (its FabricMixinVersions), latest first. */
    private static final int[][] COMPATIBILITY = {
        {0, 20, 0, FabricUtil.COMPATIBILITY_0_17_5},
        {0, 19, 4, FabricUtil.COMPATIBILITY_0_17_4},
        {0, 19, 0, FabricUtil.COMPATIBILITY_0_17_1},
        {0, 18, 4, FabricUtil.COMPATIBILITY_0_17_0},
        {0, 17, 3, FabricUtil.COMPATIBILITY_0_16_5},
        {0, 16, 0, FabricUtil.COMPATIBILITY_0_14_0},
        {0, 12, 0, FabricUtil.COMPATIBILITY_0_10_0},
    };
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** {@code -Dkiln.mixin.debug=true}: what Kiln's Mixin does with each class, in java.log. */
    private static final boolean DEBUG = Boolean.getBoolean("kiln.mixin.debug");
    private static final List<String> LOG = new ArrayList<>();
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();
    /** Each config's mod, and each mixin package's (for Kiln's log). */
    private static final Map<String, String> MOD_OF_PACKAGE = new ConcurrentHashMap<>();
    /** Every config given to Mixin (its own list keeps only those it hasn't taken in yet). */
    private static final List<org.spongepowered.asm.mixin.extensibility.IMixinConfig> CONFIGS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean running;
    private static volatile boolean ready;
    private static Instrumentation instrumentation;
    private static String runtime;
    /** Mixin's synthetic classes made so far ({@code @ModifyArgs}' argument bundles): the next number to look for. */
    private static int nextArgs = 1;

    private Mixins() {}

    /** The agent: Java calls it before {@code kiln.Host.main}, with the mods' jars on the class path. */
    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        // Java's standard output is Kiln's channel to the game (kiln.Host): nothing of Mixin's may print there.
        System.setOut(System.err);
        for (Class<?> c : inst.getAllLoadedClasses()) {
            MixinService.LOADED.add(c.getName().replace('.', '/'));
        }
        CodeSource source = Mixins.class.getProtectionDomain().getCodeSource();
        runtime = source == null ? null : source.getLocation().toString();
        // Classes loaded while Mixin starts pass as they are, and are recorded as loaded.
        inst.addTransformer(new Mixins());
        try {
            start(ClassLoader.getSystemClassLoader());
            ready = true;
        } catch (Throwable t) {
            say("Kiln's Mixin couldn't start Mixin: " + t + "; the mods' mixins do nothing");
            t.printStackTrace();
        }
    }

    /** Mixin started with Kiln's service, every mod's client configs given to it, then its phases up to the game's. */
    private static void start(ClassLoader loader) throws Exception {
        System.setProperty("mixin.bootstrapService", MixinService.Boot.class.getName());
        System.setProperty("mixin.service", MixinService.class.getName());
        MixinBootstrap.init();
        Map<String, String[]> configs = read(loader);
        for (String config : configs.keySet()) {
            try {
                org.spongepowered.asm.mixin.Mixins.addConfiguration(config);
            } catch (Throwable t) {
                say("Kiln's Mixin: " + configs.get(config)[0] + "'s mixin config " + config + " can't be used: " + t);
            }
        }
        org.spongepowered.asm.mixin.Mixins.registerErrorHandlerClass(MixinErrors.class.getName());
        int mixins = 0;
        for (Config raw : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
            CONFIGS.add(raw.getConfig());
            String[] mod = configs.get(raw.getName());
            if (mod == null) {
                continue;
            }
            raw.getConfig().decorate(FabricUtil.KEY_MOD_ID, mod[0]);
            raw.getConfig().decorate(FabricUtil.KEY_COMPATIBILITY, Integer.parseInt(mod[1]));
            String pkg = raw.getConfig().getMixinPackage();
            if (pkg != null && !pkg.isEmpty()) {
                MOD_OF_PACKAGE.put(pkg.replace('.', '/'), mod[0]);
            }
        }
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        if (MixinService.transformer == null) {
            throw new IllegalStateException("Mixin offered no transformer");
        }
        Set<String> mods = new LinkedHashSet<>();
        for (String[] mod : configs.values()) {
            mods.add(mod[0]);
        }
        if (!configs.isEmpty()) {
            say("Kiln's Mixin: Mixin " + MixinBootstrap.VERSION + " with " + configs.size() + " configs of "
                    + String.join(", ", mods) + "; each class gets its mixins as it loads");
        }
    }

    /** Kiln's Java started (the first mod's entrypoint is next): what the agent had to say goes to Kiln's log. */
    static void started() {
        List<String> lines;
        synchronized (LOG) {
            if (running) {
                return;
            }
            running = true;
            lines = new ArrayList<>(LOG);
            LOG.clear();
        }
        if (instrumentation == null && !MOD_OF_PACKAGE.isEmpty()) {
            lines.add(0, "Kiln's Mixin isn't running (Java started without -javaagent): the mods' mixins do nothing");
        }
        for (String line : lines) {
            Host.send("log", line);
        }
    }

    /** A line for Kiln's log: kept until Kiln's Java has started, as the agent runs before it. */
    static void say(String line) {
        synchronized (LOG) {
            if (!running) {
                LOG.add(line);
                return;
            }
        }
        Host.send("log", line);
    }

    /** A line said only the first time. */
    static void sayOnce(String line) {
        if (SAID.add(line)) {
            say(line);
        }
    }

    /**
     * A mixin's handler threw, {@code where} names it (its guard calls this: MixinGuards): said once a handler, with
     * its trace in java.log; the method it was called in goes on.
     */
    public static void threw(Throwable thrown, String where) {
        if (SAID.add("threw " + where)) {
            say("Kiln's Mixin: " + where + " threw " + thrown + "; the method it is in goes on (said once)");
            thrown.printStackTrace();
        }
    }

    /** The mod of a mixin class (internal or binary name), by its config's package. */
    static String modOf(String mixinClass) {
        String name = mixinClass.replace('.', '/');
        String best = null;
        for (String pkg : MOD_OF_PACKAGE.keySet()) {
            if (name.startsWith(pkg + "/") && (best == null || pkg.length() > best.length())) {
                best = pkg;
            }
        }
        return best == null ? "?" : MOD_OF_PACKAGE.get(best);
    }

    // --- The mods' configs ---------------------------------------------------------------------------------------

    /**
     * Every mod's mixin configs Kiln applies, in class path order: config file to its mod's id and the Mixin behaviour
     * its Fabric Loader dependency asks for ({@code FabricUtil}'s compatibility, as Fabric Loader works it out).
     */
    private static Map<String, String[]> read(ClassLoader loader) throws IOException {
        Map<String, String[]> configs = new LinkedHashMap<>();
        Set<String> seen = new java.util.HashSet<>();
        Enumeration<URL> manifests = loader.getResources("fabric.mod.json");
        while (manifests.hasMoreElements()) {
            URL manifest = manifests.nextElement();
            JsonObject json;
            try (Reader r = new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            String mod = text(json, "id");
            if (mod.isEmpty() || !seen.add(mod)) {
                continue;
            }
            JsonElement entries = json.get("mixins");
            if (entries == null || !entries.isJsonArray()) {
                continue;
            }
            String compat = Integer.toString(compatibility(json));
            for (JsonElement entry : entries.getAsJsonArray()) {
                String file;
                String environment = "*";
                if (entry.isJsonPrimitive()) {
                    file = entry.getAsString();
                } else if (entry.isJsonObject()) {
                    file = text(entry.getAsJsonObject(), "config");
                    String env = text(entry.getAsJsonObject(), "environment");
                    environment = env.isEmpty() ? "*" : env;
                } else {
                    continue;
                }
                // Kiln is the client (with its integrated server), as Fabric's environment: dedicated-server
                // configs don't apply.
                if (file.isEmpty() || environment.equals("server")) {
                    continue;
                }
                String[] before = configs.putIfAbsent(file, new String[] {mod, compat});
                if (before != null) {
                    say("Kiln's Mixin: " + mod + " and " + before[0] + " both name the mixin config " + file
                            + ": " + before[0] + "'s is used");
                }
            }
        }
        return configs;
    }

    /** The Mixin behaviour a mod asks for, from the lowest Fabric Loader version its fabric.mod.json accepts. */
    static int compatibility(JsonObject json) {
        int[] lowest = null;
        JsonElement depends = json.get("depends");
        if (depends != null && depends.isJsonObject()) {
            for (String id : new String[] {"fabricloader", "fabric-loader"}) {
                JsonElement spec = depends.getAsJsonObject().get(id);
                if (spec == null) {
                    continue;
                }
                List<String> alternatives = new ArrayList<>();
                if (spec.isJsonArray()) {
                    spec.getAsJsonArray().forEach(e -> alternatives.add(e.getAsString()));
                } else {
                    alternatives.add(spec.getAsString());
                }
                for (String alternative : alternatives) {
                    int[] min = lowerBound(alternative);
                    if (min == null) {
                        return FabricUtil.COMPATIBILITY_0_9_2; // any version: no lower bound
                    }
                    if (lowest == null || compare(min, lowest) < 0) {
                        lowest = min;
                    }
                }
            }
        }
        if (lowest != null) {
            for (int[] row : COMPATIBILITY) {
                if (compare(lowest, row) >= 0) {
                    return row[3];
                }
            }
        }
        return FabricUtil.COMPATIBILITY_0_9_2;
    }

    /** A version range's lowest version ({@code >=0.16.10}, {@code ^0.16}, {@code 0.16.x}), or null for none. */
    private static int[] lowerBound(String range) {
        int[] best = null;
        for (String part : range.trim().split("\\s+")) {
            if (part.isEmpty() || part.equals("*") || part.startsWith("<")) {
                continue;
            }
            Matcher m = VERSION.matcher(part);
            if (m.find()) {
                int[] v = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
                if (best == null || compare(v, best) > 0) {
                    best = v;
                }
            }
        }
        return best;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    // --- Patching -------------------------------------------------------------------------------------------------

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain,
            byte[] bytes) {
        if (name == null || redefined != null) {
            return null;
        }
        MixinService.LOADED.add(name);
        if (!ready || loader != ClassLoader.getSystemClassLoader()) {
            return null;
        }
        for (String skipped : SKIPPED) {
            if (name.startsWith(skipped)) {
                return null;
            }
        }
        // Kiln's own package (its agent and kiln.Host), not the packages under it a mod may have.
        if (name.startsWith("kiln/") && name.indexOf('/', 5) < 0) {
            return null;
        }
        try {
            return patch(name, bytes, ownClass(domain));
        } catch (org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError e) {
            // Mixin refuses to load a mixin class by name; the JVM loads it anyway, as it is (a transformer can't
            // stop a load), so the mod's code that names it goes on.
            sayOnce("Kiln's Mixin: " + name.replace('/', '.') + " is a mixin class, which Mixin refuses to load by "
                    + "name (" + e.getMessage() + "); it loads as it is");
            return null;
        } catch (Throwable t) {
            say("Kiln's Mixin couldn't patch " + name.replace('/', '.') + ": " + t);
            t.printStackTrace();
            return null;
        }
    }

    /** Whether a class comes from Kiln's runtime jar: one of its stand-ins, which may get members mixins name. */
    private static boolean ownClass(ProtectionDomain domain) {
        CodeSource source = domain == null ? null : domain.getCodeSource();
        return runtime != null && source != null && runtime.equals(source.getLocation().toString());
    }

    /** The class with its mixins, or null when Mixin has none for it. */
    private static synchronized byte[] patch(String name, byte[] bytes, boolean standIn) throws Exception {
        String dotted = name.replace('/', '.');
        MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
        var transformer = MixinService.transformer;
        if (!transformer.couldTransformClass(env, dotted)) {
            return null;
        }
        List<IMixinInfo> mine = mixinsFor(dotted);
        List<String> added = List.of();
        // Up to as many tries as the class has mixins: each failed one is taken out, and the class patched again.
        for (int attempt = 0; attempt <= mine.size(); attempt++) {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
            if (standIn && !mine.isEmpty()) {
                added = MixinStandIns.fit(node, mixinsFor(dotted));
            }
            if (DEBUG) {
                System.err.println("[Kiln's Mixin] " + dotted + ": stand-in " + standIn + ", mixins " + mine.size()
                        + ", added " + added);
            }
            boolean changed;
            List<MixinErrors.Failure> failed;
            try {
                changed = transformer.transformClass(env, dotted, node);
                failed = MixinErrors.take();
            } catch (org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError e) {
                // An injector that found fewer points than it requires stops Mixin outright (InjectionError, no
                // mixin attached): its message names the mixin, as config:name.
                MixinErrors.take();
                Throwable why = e.getCause() != null ? e.getCause() : e;
                IMixinInfo culprit = named(mine, String.valueOf(why.getMessage()));
                if (culprit == null) {
                    throw e;
                }
                changed = false;
                failed = List.of(new MixinErrors.Failure(culprit, dotted, why));
            }
            if (failed.isEmpty()) {
                if (!changed && added.isEmpty()) {
                    return null;
                }
                MixinGuards.guard(node, Mixins::modOf);
                ClassWriter writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);
                byte[] patched = writer.toByteArray();
                if (!added.isEmpty()) {
                    say("Kiln's Mixin adds to Kiln's " + dotted + " what the mods' mixins name and it hasn't: "
                            + String.join(", ", added));
                }
                applied(dotted);
                generated(env);
                return patched;
            }
            for (MixinErrors.Failure f : failed) {
                sayOnce("Kiln's Mixin leaves out " + MixinErrors.who(f.mixin()) + " on " + dotted + ": "
                        + MixinErrors.reason(f.why()));
                drop(f.mixin(), dotted);
            }
            // What Mixin learned of the class while applying the failed mixins goes with them.
            forget(name);
        }
        say("Kiln's Mixin couldn't patch " + dotted + ": its mixins kept failing; it loads without them");
        return null;
    }

    /** The mixin a message of Mixin's names (as config:name, Mixin's own way), the longest name that fits. */
    private static IMixinInfo named(List<IMixinInfo> mixins, String message) {
        IMixinInfo best = null;
        for (IMixinInfo mixin : mixins) {
            String id = mixin.toString();
            int at = message.indexOf(id);
            boolean whole = at >= 0 && (at + id.length() == message.length()
                    || !Character.isJavaIdentifierPart(message.charAt(at + id.length())));
            if (whole && (best == null || id.length() > best.toString().length())) {
                best = mixin;
            }
        }
        return best;
    }

    /** The mixins Mixin has for a class (by binary name), from every config. */
    @SuppressWarnings("unchecked")
    private static List<IMixinInfo> mixinsFor(String dotted) throws Exception {
        List<IMixinInfo> all = new ArrayList<>();
        for (Object config : CONFIGS) {
            Method has = config.getClass().getMethod("hasMixinsFor", String.class);
            has.setAccessible(true);
            if ((Boolean) has.invoke(config, dotted)) {
                Method get = config.getClass().getMethod("getMixinsFor", String.class);
                get.setAccessible(true);
                all.addAll((List<IMixinInfo>) get.invoke(config, dotted));
            }
        }
        return all;
    }

    /** Takes a mixin out of what its config applies to {@code dotted}. */
    private static void drop(IMixinInfo mixin, String dotted) throws Exception {
        Object config = mixin.getConfig();
        Method get = config.getClass().getMethod("getMixinsFor", String.class);
        get.setAccessible(true);
        ((List<?>) get.invoke(config, dotted)).remove(mixin);
    }

    /** Mixin's cached view of a class, made again from its file on the next try. */
    private static void forget(String name) throws Exception {
        Field cache = ClassInfo.class.getDeclaredField("cache");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).remove(name);
    }

    /**
     * Kiln's log says which mixins a class got, by mod, in the order Mixin applied them (priority, then as listed):
     * those its configs have for the class, once Mixin applied them all.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applied(String dotted) throws Exception {
        java.util.TreeSet ordered = new java.util.TreeSet(mixinsFor(dotted));
        Map<String, List<String>> byMod = new LinkedHashMap<>();
        for (Object o : ordered) {
            IMixinInfo mixin = (IMixinInfo) o;
            String mod = mixin.getConfig() == null ? "?" : FabricUtil.getModId(mixin.getConfig(), "?");
            byMod.computeIfAbsent(mod, m -> new ArrayList<>()).add(mixin.getName());
        }
        List<String> parts = new ArrayList<>();
        byMod.forEach((mod, mixins) -> parts.add(mod + "'s " + String.join(", ", mixins)));
        if (!parts.isEmpty()) {
            say("Kiln's Mixin applies " + String.join("; ", parts) + " to " + dotted);
        }
    }

    /**
     * Mixin's synthetic classes (the argument bundles of {@code @ModifyArgs}) have no file: Mixin makes them when the
     * class loader asks. The JVM's class loader doesn't ask, so Kiln puts each, as it is made, in a jar of its own on
     * the class path, before the code that uses it runs.
     */
    private static void generated(MixinEnvironment env) throws IOException {
        Map<String, byte[]> made = new TreeMap<>();
        for (; ; nextArgs++) {
            String synthetic = "org.spongepowered.asm.synthetic.args.Args$" + nextArgs;
            byte[] bytes = MixinService.transformer.generateClass(env, synthetic);
            if (bytes == null) {
                break;
            }
            made.put(synthetic.replace('.', '/') + ".class", bytes);
        }
        if (made.isEmpty()) {
            return;
        }
        File jar = File.createTempFile("kiln-mixin-", ".jar");
        jar.deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : made.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        instrumentation.appendToSystemClassLoaderSearch(new JarFile(jar));
    }
}
