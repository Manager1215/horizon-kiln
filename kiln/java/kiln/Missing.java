package kiln;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a generated stand-in (kiln/java-gen, tools/kiln-stubs.py) was asked to do: it has the method's name only, so
 * it does nothing and gives back a default. Said once a method in Kiln's log: the next stand-ins to write by hand.
 */
public final class Missing {
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    private Missing() {}

    public static void called(String what) {
        // The method Kiln's Mixin bridge called (D-102): its body is this, and running is all it has to do.
        if (Bridge.missing(what)) {
            return;
        }
        if (SAID.add(what)) {
            Host.send("log", "Kiln has only the name of " + what + " so far");
        }
    }
}
