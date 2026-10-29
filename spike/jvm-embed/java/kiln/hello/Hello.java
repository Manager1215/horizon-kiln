package kiln.hello;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import kiln.Native;

/** The spike's "mod": calls back into Rust, uses reflection and lambdas, and answers a HUD line. */
public final class Hello {
    private static int calls;

    private Hello() {}

    public static void init() throws Exception {
        // Reflection, the risk TeaVM had: find and call a method by name.
        Method m = Class.forName("kiln.hello.Hello").getMethod("hudText");
        String first = (String) m.invoke(null);
        // A lambda and a stream (invokedynamic).
        Supplier<String> greeting = () -> "Hello mod on Java " + System.getProperty("java.version");
        int total = List.of(1, 2, 3).stream().mapToInt(Integer::intValue).sum();
        Native.log(greeting.get() + "; reflection said: " + first + "; stream sum " + total);
        calls = 0;
    }

    public static String hudText() {
        calls++;
        return "Hello from Java! tick " + Native.gameTicks() + ", call " + calls;
    }

    /**
     * Null pointer exceptions in a hot loop: once the JIT compiles it, HotSpot catches them as SIGSEGV
     * (implicit null checks). Tests that its signal handlers live with the Rust program's own.
     */
    public static int npeStress() {
        int caught = 0;
        String[] values = {"a", null};
        for (int i = 0; i < 200_000; i++) {
            try {
                caught += values[i & 1].length() - 1;
            } catch (NullPointerException e) {
                caught++;
            }
        }
        return caught;
    }
}
