package kiln;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A mod turned off now, while its classes stay loaded (D-113's soft switch): Kiln stops calling it. Its Fabric event
 * listeners are left out of their events' invokers ({@link #listeners}), its HUD elements aren't drawn, its commands
 * don't run, and its mixins' merged handlers that Kiln guards ({@link MixinGuards}) return at once, as if they did
 * nothing. Off for good, nothing of the mod loaded, is from the next world (D-078).
 *
 * <p>Which mod a listener, an element or a command is: the jar its class came from (a lambda's class has its
 * declaring class's), as the jar's {@code fabric.mod.json} names it. One class loader holds every mod, so the class
 * loader can't tell them apart. The flags are an array indexed by a mod's number, so the guard in each handler is an
 * array load, the same in the separate Java process and in the game's (D-112).
 */
public final class SoftSwitch {
    /** Off by mod number; a mod's number is given once and kept. */
    private static volatile boolean[] off = new boolean[16];
    private static final Map<String, Integer> NUMBERS = new ConcurrentHashMap<>();
    private static final List<String> IDS = new CopyOnWriteArrayList<>();
    /** The jar (its code source's location) to the mod it holds. */
    private static volatile Map<String, String> modOfJar;
    private static final ClassValue<String> MOD_OF = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            CodeSource source = type.getProtectionDomain() == null ? null : type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return "";
            }
            return jars().getOrDefault(source.getLocation().toString(), "");
        }
    };
    /** The events whose listeners a switch filters, each made with {@code Event.kiln$of}. */
    private static final List<Runnable> EVENTS = new CopyOnWriteArrayList<>();
    /** Moves on with every switch: an invoker made over the listeners as they were is made again. */
    private static volatile int generation;

    private SoftSwitch() {}

    /** The number of mod {@code id}, for the guard in its mixins' handlers. */
    public static int number(String id) {
        return NUMBERS.computeIfAbsent(id, k -> {
            synchronized (IDS) {
                IDS.add(k);
                int n = IDS.size() - 1;
                if (n >= off.length) {
                    off = java.util.Arrays.copyOf(off, off.length * 2);
                }
                return n;
            }
        });
    }

    /** The guard in a mod's merged mixin handler: whether mod number {@code n} is turned off now. */
    public static boolean off(int n) {
        boolean[] now = off;
        return n < now.length && now[n];
    }

    /** Whether mod {@code id} is turned off now. */
    public static boolean off(String id) {
        Integer n = NUMBERS.get(id);
        return n != null && off(n);
    }

    /** Whether {@code o}'s mod (the jar its class came from) is turned off now; Kiln's own and the JDK's never are. */
    public static boolean offFor(Object o) {
        if (o == null || IDS.isEmpty()) {
            return false;
        }
        String mod = MOD_OF.get(o.getClass());
        return !mod.isEmpty() && off(mod);
    }

    /** The mod {@code type} came from, or "" for Kiln's, a library's or the JDK's class. */
    public static String modOf(Class<?> type) {
        return MOD_OF.get(type);
    }

    public static int generation() {
        return generation;
    }

    /**
     * The game's {@code soft_off} command: {@code ids} (comma-separated) are the mods turned off now, every other
     * one on. Says in Kiln's log each mod that changed.
     */
    static void set(String ids) {
        Set<String> wanted = new java.util.HashSet<>();
        for (String id : ids.split(",")) {
            if (!id.isBlank()) {
                wanted.add(id.trim());
                number(id.trim());
            }
        }
        boolean[] next = java.util.Arrays.copyOf(off, Math.max(off.length, IDS.size()));
        StringBuilder changed = new StringBuilder();
        for (int i = 0; i < IDS.size(); i++) {
            boolean now = wanted.contains(IDS.get(i));
            if (next[i] != now) {
                next[i] = now;
                changed.append(changed.length() == 0 ? "" : ", ").append(IDS.get(i)).append(now ? " off" : " on");
            }
        }
        if (changed.length() == 0) {
            return;
        }
        off = next;
        generation++;
        for (Runnable event : EVENTS) {
            event.run();
        }
        Host.send("log", "Kiln: turned " + changed + " now (fully from the next world)");
    }

    /**
     * A mod's call ({@code call}) ran Java out of what it can give (the heap, the stack): {@code listener}'s mod (the
     * object whose call failed) is turned off now, so the game goes on (D-112).
     */
    public static void trip(Object listener, Throwable why, String call) {
        trip(listener == null ? "" : modOf(listener.getClass()), why, call);
    }

    /**
     * Mod {@code mod}'s call ({@code call}) ran Java out of what it can give: the mod is turned off now, and so in
     * Options > Mods ({@code trip <mod>} to the game, which flips its switch: off from the next start too, until the
     * player turns it back on), and the player is told which mod, where, and how to turn it back on. A mod off already,
     * or none ("": the throw came from no mod's code Kiln knows), is only said in Kiln's log. Says nothing more when
     * saying it runs out of memory too.
     */
    public static void trip(String mod, Throwable why, String call) {
        try {
            String reason = why instanceof OutOfMemoryError ? "ran Java out of memory" : "overflowed Java's stack";
            String where = call == null || call.isEmpty() ? "" : " in " + call;
            Host.send("log", "Kiln: " + (mod.isEmpty() ? "a Java call" : "the mod " + mod) + " " + reason + where
                    + ": " + why);
            if (mod.isEmpty() || off(mod)) {
                return;
            }
            Set<String> now = new java.util.TreeSet<>();
            boolean[] flags = off;
            for (int i = 0; i < IDS.size() && i < flags.length; i++) {
                if (flags[i]) {
                    now.add(IDS.get(i));
                }
            }
            now.add(mod);
            set(String.join(",", now));
            Host.send("trip", mod);
            Breadcrumb.tripped(mod, call, why instanceof OutOfMemoryError ? "memory" : "stack", why.toString());
            Host.send("chat", "Kiln: the mod " + mod + " " + reason + where + ", so Kiln turned it off and the game"
                    + " goes on. It stays off, from the next start too, until you turn it back on in Options > Mods"
                    + " (or now: /kiln on " + mod + ").");
        } catch (OutOfMemoryError e) {
            // Nothing left to say it with.
        }
    }

    /**
     * An event's listeners as its invoker sees them: {@code all}, in the order they registered, without those of the
     * mods turned off. {@code live} is the list the invoker reads; it changes one atomic step at a time, so a call
     * running meanwhile sees the listeners before or after a step, never a list with one missing that is on.
     */
    public static <T> void listeners(List<T> all, List<T> live) {
        EVENTS.add(() -> sync(all, live));
    }

    /** {@code live} made {@code all} without the listeners of mods turned off. */
    static <T> void sync(List<T> all, List<T> live) {
        synchronized (live) {
            live.removeIf(SoftSwitch::offFor);
            int at = 0;
            for (T listener : all) {
                if (offFor(listener)) {
                    continue;
                }
                if (at >= live.size() || live.get(at) != listener) {
                    live.add(at, listener);
                }
                at++;
            }
        }
    }

    /**
     * The mod turned off now whose command {@code line} would run, or null: the command of each node the line goes
     * through (a redirect's too) is the mod's whose jar its class came from.
     */
    public static <S> String offCommand(com.mojang.brigadier.CommandDispatcher<S> dispatcher, String line, S source) {
        if (IDS.isEmpty()) {
            return null;
        }
        String mod = commandMod(dispatcher, line, source);
        return !mod.isEmpty() && off(mod) ? mod : null;
    }

    /**
     * The mod command {@code line} runs, for D-112's breadcrumb and the switch: the first one turned off now among the
     * mods whose commands the line goes through (a redirect's too), else the first of them, else "" (the game's own,
     * or Kiln's).
     */
    public static <S> String commandMod(com.mojang.brigadier.CommandDispatcher<S> dispatcher, String line, S source) {
        String first = "";
        for (var ctx = dispatcher.parse(line, source).getContext(); ctx != null; ctx = ctx.getChild()) {
            String mod = ctx.getCommand() == null ? "" : modOf(ctx.getCommand().getClass());
            if (!mod.isEmpty() && off(mod)) {
                return mod;
            }
            first = first.isEmpty() ? mod : first;
            for (var node : ctx.getNodes()) {
                var command = node.getNode().getCommand();
                mod = command == null ? "" : modOf(command.getClass());
                if (!mod.isEmpty() && off(mod)) {
                    return mod;
                }
                first = first.isEmpty() ? mod : first;
            }
        }
        return first;
    }

    /** What the player is told of a command whose mod is turned off. */
    public static String offMessage(String mod) {
        return "That command is " + mod + "'s, which is turned off";
    }

    /** Every jar on the class path with a {@code fabric.mod.json}, to its mod's id. */
    private static Map<String, String> jars() {
        Map<String, String> jars = modOfJar;
        if (jars != null) {
            return jars;
        }
        jars = new HashMap<>();
        try {
            Enumeration<URL> manifests = SoftSwitch.class.getClassLoader().getResources("fabric.mod.json");
            while (manifests.hasMoreElements()) {
                URL manifest = manifests.nextElement();
                String url = manifest.toString();
                // jar:file:/x/mod.jar!/fabric.mod.json is the jar file:/x/mod.jar; a folder's is file:/x/.
                String jar = url.startsWith("jar:") ? url.substring(4, url.lastIndexOf("!/"))
                        : url.substring(0, url.length() - "fabric.mod.json".length());
                try (Reader r = new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)) {
                    var json = JsonParser.parseReader(r).getAsJsonObject();
                    if (json.has("id")) {
                        jars.putIfAbsent(jar, json.get("id").getAsString());
                    }
                } catch (RuntimeException e) {
                    // Mods.all says what it can't read.
                }
            }
        } catch (java.io.IOException e) {
            Host.send("log", "Kiln can't list the mods' jars for their switches: " + e);
        }
        modOfJar = jars;
        return jars;
    }
}
