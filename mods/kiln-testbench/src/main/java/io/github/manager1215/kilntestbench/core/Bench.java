package io.github.manager1215.kilntestbench.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The probes and what they did. Each probe is made by its own factory, inside the bench's guard: a probe is a class
 * of its own, and a class that names something Kiln has no stand-in for fails to load or verify, so it takes down
 * that probe and no other.
 */
public final class Bench {
    /** When probes run, in this order. */
    public enum Phase {
        /** In the mod's client entrypoint, before any world. */
        INIT,
        /** Once in a world, a second after entering it. */
        WORLD,
        /** In the first HUD drawing after WORLD. */
        HUD,
        /** Two seconds later: checks of what earlier phases set off (events fired, replies arrived). */
        LATE
    }

    public enum Status {
        PASS,
        /** The call threw. */
        FAIL,
        /** A class, method or field the probe calls isn't there (under Kiln: no stand-in for it). */
        MISSING,
        /** What the probe needs isn't there. */
        SKIP
    }

    public record Result(String id, Phase phase, Status status, String detail) {}

    private record Entry(String id, Phase phase, Supplier<? extends Probe> factory) {}

    private static final int DETAIL = 100;

    /** Events heard, by name: probes register listeners that add to it, and later probes check it. */
    public static final Set<String> HEARD = ConcurrentHashMap.newKeySet();

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Result> results = new LinkedHashMap<>();
    private final Set<Phase> ran = EnumSet.noneOf(Phase.class);

    public void add(String id, Phase phase, Supplier<? extends Probe> factory) {
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                throw new IllegalArgumentException("two probes named " + id);
            }
        }
        entries.add(new Entry(id, phase, factory));
    }

    /** Runs the phase's probes, once. */
    public void run(Phase phase) {
        if (!ran.add(phase)) {
            return;
        }
        for (Entry e : entries) {
            if (e.phase == phase) {
                results.put(e.id, outcome(e));
            }
        }
    }

    /** Marks a phase that never came, so its probes fail with {@code why}. */
    public void never(Phase phase, String why) {
        if (!ran.add(phase)) {
            return;
        }
        for (Entry e : entries) {
            if (e.phase == phase) {
                results.put(e.id, new Result(e.id, phase, Status.FAIL, why));
            }
        }
    }

    public boolean ran(Phase phase) {
        return ran.contains(phase);
    }

    public List<Result> results() {
        return List.copyOf(results.values());
    }

    public int size() {
        return entries.size();
    }

    public int count(Status status) {
        return (int) results.values().stream().filter(r -> r.status == status).count();
    }

    /** "40 of 80 pass" */
    public String score() {
        return count(Status.PASS) + " of " + entries.size() + " pass";
    }

    /** The report: a header, the counts, then a line a probe: status, phase, id, what it returned or threw. */
    public String report(String header) {
        StringBuilder b = new StringBuilder(header).append('\n');
        b.append(score());
        for (Status s : Status.values()) {
            if (s != Status.PASS) {
                b.append(", ").append(count(s)).append(' ').append(s.name().toLowerCase());
            }
        }
        b.append("\n\n");
        for (Result r : results.values()) {
            b.append(String.format("%-7s %-5s %-40s %s%n", r.status, r.phase.name().toLowerCase(), r.id, r.detail));
        }
        return b.toString();
    }

    /** A report's statuses by probe id, as {@link #report} writes them. */
    public static Map<String, Status> parse(String report) {
        Map<String, Status> statuses = new LinkedHashMap<>();
        for (String line : report.split("\n")) {
            String[] f = line.trim().split("\\s+", 4);
            if (f.length >= 3) {
                try {
                    statuses.put(f[2], Status.valueOf(f[0]));
                } catch (IllegalArgumentException notAProbeLine) {
                    // the header and the counts
                }
            }
        }
        return statuses;
    }

    private static Result outcome(Entry e) {
        try {
            return new Result(e.id, e.phase, Status.PASS, text(e.factory.get().run()));
        } catch (Skip s) {
            return new Result(e.id, e.phase, Status.SKIP, s.getMessage());
        } catch (ExceptionInInitializerError t) {
            return new Result(e.id, e.phase, Status.FAIL, thrown(t.getCause() == null ? t : t.getCause()));
        } catch (LinkageError t) {
            return new Result(e.id, e.phase, Status.MISSING, thrown(t));
        } catch (Throwable t) {
            return new Result(e.id, e.phase, Status.FAIL, thrown(t));
        }
    }

    static String text(Object value) {
        String s = String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
        return s.length() > DETAIL ? s.substring(0, DETAIL - 1) + "…" : s;
    }

    private static String thrown(Throwable t) {
        return text(t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
    }
}
