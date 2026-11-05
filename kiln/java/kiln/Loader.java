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
        try {
            int member = className.indexOf("::");
            if (member >= 0) {
                return loadMember(className.substring(0, member), className.substring(member + 2), kind);
            }
            Class<?> c = Class.forName(className, true, Loader.class.getClassLoader());
            Object entrypoint = c.getDeclaredConstructor().newInstance();
            made(kind, entrypoint);
            c.getMethod(kind.equals("main") ? "onInitialize" : "onInitializeClient").invoke(entrypoint);
            return null;
        } catch (InvocationTargetException e) {
            return describe(e.getCause());
        } catch (Throwable t) {
            return describe(t);
        }
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
                : c.getDeclaredConstructor().newInstance());
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
        return t.getClass().getName() + (message == null ? "" : ": " + message) + where + mod;
    }
}
