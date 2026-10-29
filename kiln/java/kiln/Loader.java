package kiln;

import java.lang.reflect.InvocationTargetException;

/** Starts Java mods as Fabric does. Kiln's Rust side calls these with the names from fabric.mod.json. */
final class Loader {
    private Loader() {}

    /**
     * A mod's client entrypoint: a new instance of {@code className}, then its {@code onInitializeClient()}.
     * Null when it ran, else what went wrong.
     */
    static String load(String className) {
        try {
            Class<?> c = Class.forName(className, true, Loader.class.getClassLoader());
            Object entrypoint = c.getDeclaredConstructor().newInstance();
            c.getMethod("onInitializeClient").invoke(entrypoint);
            return null;
        } catch (InvocationTargetException e) {
            return describe(e.getCause());
        } catch (Throwable t) {
            return describe(t);
        }
    }

    /** The running Java, for Kiln's log: {@code 25.0.2 (Eclipse Adoptium)}. */
    static String javaVersion() {
        return System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
