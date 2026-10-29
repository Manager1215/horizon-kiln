package io.github.manager1215.kilntimer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.manager1215.kilntimer.core.Timer.Phase;
import io.github.manager1215.kilntimer.core.Timer.SplitTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimerTest {
    private static final long S = 1_000_000_000L;

    private static Timer running(FinishMode mode) {
        Timer t = new Timer(mode);
        t.enteredWorld(0, Timer.OVERWORLD, 0);
        assertEquals(Phase.RUNNING, t.phase());
        return t;
    }

    @Test
    void timesReadAsRunnersWriteThem() {
        assertEquals("0:00.000", Times.format(0));
        assertEquals("1:02.345", Times.format(62_345_000_000L));
        assertEquals("1:00:00.001", Times.format(3_600_001_000_000L));
        assertEquals("0:00.000", Times.format(-5));
        assertEquals("-1.234", Times.delta(-1_234_000_000L));
        assertEquals("+0.000", Times.delta(0));
        assertEquals("+1:02.345", Times.delta(62_345_000_000L));
    }

    @Test
    void aNewWorldStartsTheRunAnOldOneDoesNot() {
        Timer fresh = new Timer(FinishMode.DRAGON);
        fresh.enteredWorld(Timer.NEW_WORLD_TICKS - 1, Timer.OVERWORLD, 0);
        assertEquals(Phase.RUNNING, fresh.phase());
        Timer old = new Timer(FinishMode.DRAGON);
        old.enteredWorld(Timer.NEW_WORLD_TICKS, Timer.OVERWORLD, 0);
        assertEquals(Phase.IDLE, old.phase());
        old.start(5 * S);
        assertEquals(2 * S, old.rta(7 * S));
    }

    @Test
    void leavingTheWorldResetsAnUnfinishedRun() {
        Timer t = running(FinishMode.DRAGON);
        t.dimension(Timer.NETHER, 10 * S);
        t.leftWorld();
        assertEquals(Phase.IDLE, t.phase());
        assertTrue(t.splits().isEmpty());
    }

    @Test
    void igtLeavesOutPausesAndLoadingRtaDoesNot() {
        Timer t = running(FinishMode.MANUAL);
        t.setStopped(true, 10 * S);
        t.setStopped(true, 11 * S);
        t.setStopped(false, 15 * S);
        assertEquals(20 * S, t.rta(20 * S));
        assertEquals(15 * S, t.igt(20 * S));
        // Stopped before the start (a loading screen): IGT waits for play.
        Timer loading = new Timer(FinishMode.MANUAL);
        loading.setStopped(true, 0);
        loading.start(0);
        loading.setStopped(false, 3 * S);
        assertEquals(2 * S, loading.igt(5 * S));
    }

    @Test
    void eachSplitHappensOnceAnyOrder() {
        Timer t = running(FinishMode.DRAGON);
        t.advancement("minecraft:nether/find_fortress", 30 * S);
        t.advancement("minecraft:story/enter_the_nether", 20 * S);
        t.dimension(Timer.NETHER, 25 * S);
        t.advancement("minecraft:nether/find_fortress", 40 * S);
        assertEquals(List.of(Split.NETHER, Split.FORTRESS), List.copyOf(t.splits().keySet()));
        assertEquals(new SplitTime(20 * S, 20 * S), t.splits().get(Split.NETHER));
        assertEquals(30 * S, t.splits().get(Split.FORTRESS).igt());
    }

    @Test
    void dimensionsMakeTheirSplits() {
        Timer t = running(FinishMode.DRAGON);
        t.dimension(Timer.OVERWORLD, S);
        assertTrue(t.splits().isEmpty());
        t.dimension(Timer.NETHER, 2 * S);
        t.dimension(Timer.OVERWORLD, 3 * S);
        t.dimension(Timer.NETHER, 4 * S);
        t.dimension(Timer.OVERWORLD, 5 * S);
        t.dimension(Timer.END, 6 * S);
        assertEquals(2 * S, t.splits().get(Split.NETHER).rta());
        assertEquals(3 * S, t.splits().get(Split.BLIND).rta());
        assertEquals(6 * S, t.splits().get(Split.END).rta());
        assertEquals(Phase.RUNNING, t.phase());
    }

    @Test
    void finishModes() {
        Timer dragon = running(FinishMode.DRAGON);
        dragon.dimension(Timer.END, S);
        dragon.advancement("minecraft:end/kill_dragon", 9 * S);
        assertEquals(Phase.FINISHED, dragon.phase());
        assertEquals(9 * S, dragon.igt(100 * S));

        Timer leave = running(FinishMode.LEAVE_END);
        leave.dimension(Timer.END, S);
        leave.advancement("minecraft:end/kill_dragon", 9 * S);
        assertEquals(Phase.RUNNING, leave.phase());
        leave.dimension(Timer.OVERWORLD, 12 * S);
        assertEquals(Phase.FINISHED, leave.phase());
        assertEquals(12 * S, leave.rta(100 * S));

        Timer manual = running(FinishMode.MANUAL);
        manual.dimension(Timer.END, S);
        manual.advancement("minecraft:end/kill_dragon", 9 * S);
        manual.dimension(Timer.OVERWORLD, 12 * S);
        assertEquals(Phase.RUNNING, manual.phase());
        manual.finish(15 * S);
        assertEquals(15 * S, manual.splits().get(Split.FINISH).igt());
        manual.split(Split.NETHER, 16 * S);
        assertFalse(manual.splits().containsKey(Split.NETHER));
    }

    @Test
    void theSplitKeyTakesTheNextPendingSplit() {
        Timer t = running(FinishMode.MANUAL);
        t.dimension(Timer.NETHER, S);
        assertEquals(Split.BASTION, t.splitNext(2 * S).orElseThrow());
        assertEquals(Split.FORTRESS, t.splitNext(3 * S).orElseThrow());
        for (int i = 0; i < 3; i++) {
            t.splitNext(4 * S);
        }
        assertEquals(Split.FINISH, t.splitNext(5 * S).orElseThrow());
        assertEquals(Phase.FINISHED, t.phase());
        assertTrue(t.splitNext(6 * S).isEmpty());
    }

    @Test
    void personalBestsAndBestSplits() {
        Records r = new Records();
        Timer first = running(FinishMode.MANUAL);
        first.split(Split.NETHER, 100 * S);
        first.finish(600 * S);
        assertTrue(r.record(first.splits()));
        assertEquals(600 * S, r.pbIgt().orElseThrow());

        Timer slower = running(FinishMode.MANUAL);
        slower.split(Split.NETHER, 90 * S);
        slower.finish(700 * S);
        assertFalse(r.record(slower.splits()));
        assertEquals(600 * S, r.pbIgt().orElseThrow());
        assertEquals(90 * S, r.best(Split.NETHER).orElseThrow());
        assertEquals(-10 * S, r.delta(Split.NETHER, 90 * S).orElseThrow());

        Timer unfinished = running(FinishMode.MANUAL);
        unfinished.split(Split.NETHER, 80 * S);
        assertFalse(r.record(unfinished.splits()));
        assertEquals(80 * S, r.best(Split.NETHER).orElseThrow());

        Records again = Records.load(r.save() + "pb.NOPE=1 2\nbest.NETHER=oops\n");
        assertEquals(r.pb(), again.pb());
        assertEquals(80 * S, again.best(Split.NETHER).orElseThrow());
    }

    @Test
    void theHudShowsTimesSplitsAndDeltas() {
        Records r = new Records();
        Timer pb = running(FinishMode.MANUAL);
        pb.split(Split.NETHER, 100 * S);
        pb.finish(600 * S);
        r.record(pb.splits());

        Timer t = running(FinishMode.MANUAL);
        t.split(Split.NETHER, 95 * S);
        List<Hud.Row> rows = Hud.rows(t, r, false, 120 * S);
        assertEquals(new Hud.Row("RTA", "2:00.000", "", Hud.WHITE, Hud.WHITE), rows.get(0));
        assertEquals(new Hud.Row("Nether", "1:35.000", "-5.000", Hud.WHITE, Hud.GREEN), rows.get(2));
        assertEquals(Hud.Row.of("Bastion", "-", Hud.GRAY), rows.get(3));
        assertEquals(Hud.Row.of("Finish", "10:00.000", Hud.GRAY), rows.get(8));

        t.finish(590 * S);
        boolean newPb = r.record(t.splits());
        rows = Hud.rows(t, r, newPb, 999 * S);
        assertEquals(Hud.Row.of("PB!", "9:50.000", Hud.GOLD), rows.getLast());
    }
}
