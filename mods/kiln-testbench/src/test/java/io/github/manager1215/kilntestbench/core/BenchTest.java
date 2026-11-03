package io.github.manager1215.kilntestbench.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.manager1215.kilntestbench.core.Bench.Phase;
import io.github.manager1215.kilntestbench.core.Bench.Status;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchTest {
    @Test
    void eachProbeGetsItsStatus() {
        Bench b = new Bench();
        b.add("passes", Phase.INIT, () -> () -> 42);
        b.add("fails", Phase.INIT, () -> () -> {
            throw new IllegalStateException("broken");
        });
        b.add("missing", Phase.INIT, () -> () -> {
            throw new NoSuchMethodError("net.minecraft.client.Minecraft.getFps()");
        });
        b.add("skips", Phase.INIT, () -> () -> {
            throw new Skip("no player");
        });
        b.add("cannotLoad", Phase.INIT, () -> {
            throw new NoClassDefFoundError("net/minecraft/world/item/ItemStack");
        });
        b.add("later", Phase.WORLD, () -> () -> "not yet");
        b.run(Phase.INIT);

        Map<String, Status> statuses = Bench.parse(b.report("test"));
        assertEquals(Map.of("passes", Status.PASS, "fails", Status.FAIL, "missing", Status.MISSING, "skips",
                Status.SKIP, "cannotLoad", Status.MISSING), statuses);
        assertEquals("1 of 6 pass", b.score());
        assertTrue(b.report("test").contains("PASS    init  passes"));
        assertTrue(b.report("test").contains("java.lang.IllegalStateException: broken"));
    }

    @Test
    void aPhaseRunsOnce() {
        Bench b = new Bench();
        int[] runs = {0};
        b.add("counts", Phase.WORLD, () -> () -> ++runs[0]);
        assertFalse(b.ran(Phase.WORLD));
        b.run(Phase.WORLD);
        b.run(Phase.WORLD);
        assertEquals(1, runs[0]);
        assertTrue(b.ran(Phase.WORLD));
    }

    @Test
    void aPhaseThatNeverCameFailsItsProbes() {
        Bench b = new Bench();
        b.add("draws", Phase.HUD, () -> () -> "drawn");
        b.never(Phase.HUD, "the HUD was never drawn");
        b.run(Phase.HUD);
        assertEquals(Status.FAIL, b.results().getFirst().status());
        assertEquals("the HUD was never drawn", b.results().getFirst().detail());
    }

    @Test
    void longOrManyLineValuesAreShortened() {
        assertEquals("a b", Bench.text("a\nb"));
        assertEquals(100, Bench.text("x".repeat(500)).length());
    }
}
