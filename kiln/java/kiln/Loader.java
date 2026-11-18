package kiln;

import java.lang.reflect.InvocationTargetException;

/** Starts Java mods as Fabric does. Kiln's Rust side calls these with the names from fabric.mod.json. */
public final class Loader {
    /** The entrypoints made so far, by kind ({@code main}, {@code client}), in the order they ran. */
    private static final java.util.Map<String, java.util.List<Object>> MADE = new java.util.concurrent.ConcurrentHashMap<>();

    private Loader() {}

    /** Kiln's own: the entrypoint objects of {@code kind} that ran, for {@code FabricLoader.getEntrypoints}. */
    public static java.util.List<Object> made(String kind) {
        return java.util.List.copyOf(MADE.getOrDefault(kind, java.util.List.of()));
    }

    private static void made(String kind, Object entrypoint) {
        MADE.computeIfAbsent(kind, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entrypoint);
    }

    /**
     * A mod's entrypoint of {@code kind}: a new instance of {@code className}, then its {@code onInitialize()}
     * ({@code main}) or {@code onInitializeClient()} ({@code client}). Null when it ran, else what went wrong.
     */
    static String load(String className, String kind) {
        Mixins.started();
        // The game's creative tabs are in their registry before any mod's, as vanilla's registry has them: a mod's tab
        // comes after them, in the order a recipe viewer lists them (D-168).
        GameTabs.register();
        // D-112's breadcrumb: the entrypoint's mod, known from its class before the class runs anything (its static
        // initializer is the mod's code too). A failure, a run out of Java's heap or stack included, is the mod's
        // failure to start: it says so, and the game goes on.
        int member = className.indexOf("::");
        String owner = member >= 0 ? className.substring(0, member) : className;
        String mod = "";
        try {
            mod = SoftSwitch.modOf(Class.forName(owner, false, Loader.class.getClassLoader()));
        } catch (Throwable t) {
            // Said below, where the class is loaded for its entrypoint.
        }
        int depth = Breadcrumb.enter(mod, (kind.equals("main") ? "onInitialize " : "onInitializeClient ") + className);
        String failed;
        try {
            if (member >= 0) {
                failed = loadMember(owner, className.substring(member + 2), kind);
            } else {
                Class<?> c = Class.forName(className, true, Loader.class.getClassLoader());
                Object entrypoint = instance(c);
                made(kind, entrypoint);
                java.lang.reflect.Method init = c.getMethod(kind.equals("main") ? "onInitialize" : "onInitializeClient");
                init.setAccessible(true);
                init.invoke(entrypoint);
                failed = null;
            }
        } catch (InvocationTargetException e) {
            failed = describe(e.getCause());
        } catch (Throwable t) {
            failed = describe(t);
        } finally {
            Breadcrumb.leave(depth);
        }
        started(mod, failed);
        return failed;
    }

    /**
     * How each mod started, for the overlay's state (D-113): "" when every entrypoint of it ran, else the first
     * failure's text. A mod with no entrypoint (mixins or data only) isn't in it.
     */
    private static final java.util.Map<String, String> STARTED = new java.util.concurrent.ConcurrentHashMap<>();

    private static void started(String mod, String failed) {
        if (mod.isEmpty()) {
            return;
        }
        STARTED.merge(mod, failed == null ? "" : failed, (before, now) -> before.isEmpty() ? now : before);
    }

    /** Mod {@code id}'s start: null when none of its entrypoints ran yet, "" when they all ran, else why one failed. */
    static String started(String id) {
        return STARTED.get(id);
    }

    /** The entrypoints of the other kinds made so far ({@link #containers}), by kind: Fabric makes each once. */
    private static final java.util.Map<String, java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object>>> CONTAINERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Kiln's own: the entrypoints of {@code kind} with their mods, for {@code FabricLoader.getEntrypointContainers}:
     * {@code main} and {@code client} as they ran; any other kind a mod names for its own plugins (JEI's {@code
     * jei_mod_plugin}, D-152) made the first time it is asked for, in the mods' order. One that can't be made is
     * logged and left out; Fabric would throw, and the mod asking would lose all of them.
     */
    public static synchronized java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object>> containers(String kind) {
        if (kind.equals("main") || kind.equals("client")) {
            java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object>> ran = new java.util.ArrayList<>();
            for (Object entrypoint : made(kind)) {
                ran.add(container(SoftSwitch.modOf(entrypoint.getClass()), entrypoint, entrypoint.getClass().getName()));
            }
            return ran;
        }
        return CONTAINERS.computeIfAbsent(kind, k -> {
            java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object>> found = new java.util.ArrayList<>();
            for (String[] named : Mods.entrypoints(k)) {
                try {
                    found.add(container(named[0], entrypointOf(named[1]), named[1]));
                } catch (Throwable t) {
                    Host.send("log", "the " + k + " entrypoint " + named[1] + " of " + named[0] + " can't be made: " + describe(t));
                }
            }
            return java.util.List.copyOf(found);
        });
    }

    /** An entrypoint as written in fabric.mod.json, made as Fabric's default language adapter makes one. */
    private static Object entrypointOf(String value) throws ReflectiveOperationException {
        int member = value.indexOf("::");
        if (member < 0) {
            return instance(Class.forName(value, true, Loader.class.getClassLoader()));
        }
        Class<?> c = Class.forName(value.substring(0, member), true, Loader.class.getClassLoader());
        java.lang.reflect.Field field = c.getDeclaredField(value.substring(member + 2));
        field.setAccessible(true);
        return field.get(null);
    }

    private static net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object> container(String mod, Object entrypoint,
            String definition) {
        net.fabricmc.loader.api.ModContainer provider = Mods.all().stream()
                .filter(m -> m.getMetadata().getId().equals(mod)).findFirst()
                .orElseGet(() -> Mods.all().get(0));
        return new net.fabricmc.loader.api.entrypoint.EntrypointContainer<>() {
            @Override
            public Object getEntrypoint() {
                return entrypoint;
            }

            @Override
            public net.fabricmc.loader.api.ModContainer getProvider() {
                return provider;
            }

            @Override
            public String getDefinition() {
                return definition;
            }
        };
    }

    /**
     * An entrypoint class's instance, as Fabric's language adapters make one: a Kotlin {@code object}'s own (its static
     * {@code INSTANCE}, Fabric Language Kotlin's adapter), else a new one, its constructor reached even when it isn't
     * public (D-098).
     */
    private static Object instance(Class<?> c) throws ReflectiveOperationException {
        try {
            java.lang.reflect.Field field = c.getDeclaredField("INSTANCE");
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == c) {
                field.setAccessible(true);
                Object object = field.get(null);
                if (object != null) {
                    return object;
                }
            }
        } catch (NoSuchFieldException e) {
            // not a Kotlin object
        }
        java.lang.reflect.Constructor<?> constructor = c.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * An entrypoint written {@code Class::member}, as Fabric's language adapter takes it: a field holding the
     * entrypoint, or a method run in its place (static, else on a new instance).
     */
    private static String loadMember(String className, String member, String kind) throws ReflectiveOperationException {
        Class<?> c = Class.forName(className, true, Loader.class.getClassLoader());
        for (java.lang.reflect.Field field : c.getDeclaredFields()) {
            if (field.getName().equals(member) && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                Object entrypoint = field.get(null);
                made(kind, entrypoint);
                entrypoint.getClass().getMethod(kind.equals("main") ? "onInitialize" : "onInitializeClient")
                        .invoke(entrypoint);
                return null;
            }
        }
        java.lang.reflect.Method method = c.getDeclaredMethod(member);
        method.setAccessible(true);
        method.invoke(java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null
                : instance(c));
        return null;
    }

    /** The running Java, for Kiln's log: {@code 25.0.2 (Eclipse Adoptium)}. */
    static String javaVersion() {
        return System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }

    /** What went wrong, and where in the mod's code (the first frame outside Java's own classes). */
    private static String describe(Throwable t) {
        // A static initializer's failure, or a reflective call's: what went wrong inside.
        while ((t instanceof ExceptionInInitializerError || t instanceof InvocationTargetException) && t.getCause() != null) {
            t = t.getCause();
        }
        String message = t.getMessage();
        String where = "";
        String mod = "";
        for (StackTraceElement frame : t.getStackTrace()) {
            String c = frame.getClassName();
            String at = c + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            if (where.isEmpty() && !c.startsWith("java.") && !c.startsWith("jdk.")) {
                where = " at " + at;
            }
            // The mod's own line that led there, when the failure is inside a stand-in.
            if (!c.startsWith("java.") && !c.startsWith("jdk.") && !c.startsWith("net.minecraft.")
                    && !c.startsWith("net.fabricmc.") && !c.startsWith("com.mojang.") && !c.startsWith("kiln.")) {
                mod = (" at " + at).equals(where) ? "" : " (from " + at + ")";
                break;
            }
        }
        String said = t.getClass().getName() + (message == null ? "" : ": " + message) + where + mod;
        // A wrapper (a mod's "Failed to initialize ...") hides what its cause was, and that is what is to be fixed (D-206).
        Throwable root = t;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 16; depth++) {
            root = root.getCause();
        }
        if (root != t) {
            String at = "";
            for (StackTraceElement frame : root.getStackTrace()) {
                if (!frame.getClassName().startsWith("java.") && !frame.getClassName().startsWith("jdk.")) {
                    at = " at " + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                    break;
                }
            }
            said += " [cause: " + root.getClass().getName() + (root.getMessage() == null ? "" : ": " + root.getMessage()) + at + "]";
        }
        return said;
    }
}
