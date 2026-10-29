package io.github.manager1215.kilntimer.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * An Any% run: idle, running or finished; its real time (RTA) and in-game time (IGT); and the splits it has
 * reached. It knows nothing of Minecraft: the game feeds it what happened, with the time it happened
 * ({@code now}, nanoseconds from any fixed origin, as {@link System#nanoTime()} gives).
 *
 * <p>IGT is real time without pauses and loading screens, as SpeedRunIGT counts it: the game says when it
 * stops and starts ({@link #setStopped}).
 */
public final class Timer {
    /** A new world is one whose game time is under this many ticks when the player appears in it. */
    public static final long NEW_WORLD_TICKS = 100;

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    public enum Phase {
        IDLE,
        RUNNING,
        FINISHED
    }

    /** When a split was reached: real time and in-game time since the start. */
    public record SplitTime(long rta, long igt) {}

    private final EnumMap<Split, SplitTime> splits = new EnumMap<>(Split.class);
    private FinishMode finishMode;
    private Phase phase = Phase.IDLE;
    private long startedAt;
    private long finalRta;
    private long finalIgt;
    /** IGT up to {@link #igtSince}. */
    private long igtBanked;
    /** When IGT last started counting; -1 while it is stopped. */
    private long igtSince = -1;
    private boolean stopped;
    /** The dimension last seen: the splits that a change of dimension makes need where the player came from. */
    private String dimension;

    public Timer(FinishMode finishMode) {
        this.finishMode = finishMode;
    }

    public Phase phase() {
        return phase;
    }

    public FinishMode finishMode() {
        return finishMode;
    }

    public void setFinishMode(FinishMode finishMode) {
        this.finishMode = finishMode;
    }

    /** Starts a run now, whatever the timer was doing. */
    public void start(long now) {
        reset();
        phase = Phase.RUNNING;
        startedAt = now;
        igtSince = stopped ? -1 : now;
    }

    /** Back to idle: the run, if any, is gone. */
    public void reset() {
        phase = Phase.IDLE;
        splits.clear();
        igtBanked = 0;
        igtSince = -1;
        finalRta = 0;
        finalIgt = 0;
    }

    /** The game paused, or a loading screen came ({@code true}), or play went on ({@code false}). */
    public void setStopped(boolean stopped, long now) {
        if (stopped == this.stopped) {
            return;
        }
        this.stopped = stopped;
        if (phase != Phase.RUNNING) {
            return;
        }
        if (stopped) {
            igtBanked += now - igtSince;
            igtSince = -1;
        } else {
            igtSince = now;
        }
    }

    /**
     * The player appeared in a world: a new world ({@code gameTime} under {@link #NEW_WORLD_TICKS}) starts a
     * run by itself; an old one only by hand.
     */
    public void enteredWorld(long gameTime, String dimension, long now) {
        this.dimension = dimension;
        if (phase == Phase.IDLE && gameTime < NEW_WORLD_TICKS) {
            start(now);
        }
    }

    /** The player left the world: an unfinished run is reset. */
    public void leftWorld() {
        dimension = null;
        if (phase == Phase.RUNNING) {
            reset();
        }
    }

    /** The player's dimension now (called often; only a change does something). */
    public void dimension(String now, long at) {
        String from = dimension;
        dimension = now;
        if (from == null || from.equals(now)) {
            return;
        }
        if (now.equals(NETHER)) {
            split(Split.NETHER, at);
        } else if (now.equals(END)) {
            split(Split.END, at);
        } else if (now.equals(OVERWORLD) && from.equals(NETHER)) {
            split(Split.BLIND, at);
        } else if (now.equals(OVERWORLD) && from.equals(END) && finishMode == FinishMode.LEAVE_END) {
            finish(at);
        }
    }

    /** The player got the advancement {@code id} ({@code minecraft:story/enter_the_nether}). */
    public void advancement(String id, long now) {
        switch (id) {
            case "minecraft:story/enter_the_nether" -> split(Split.NETHER, now);
            case "minecraft:nether/find_bastion" -> split(Split.BASTION, now);
            case "minecraft:nether/find_fortress" -> split(Split.FORTRESS, now);
            case "minecraft:story/follow_ender_eye" -> split(Split.STRONGHOLD, now);
            case "minecraft:story/enter_the_end" -> split(Split.END, now);
            case "minecraft:end/kill_dragon" -> {
                if (finishMode == FinishMode.DRAGON) {
                    finish(now);
                }
            }
            default -> {}
        }
    }

    /** Reaches {@code split} now, unless the run isn't going or already reached it. True when it did. */
    public boolean split(Split split, long now) {
        if (phase != Phase.RUNNING || splits.containsKey(split)) {
            return false;
        }
        if (split == Split.FINISH) {
            finish(now);
        } else {
            splits.put(split, new SplitTime(rta(now), igt(now)));
        }
        return true;
    }

    /** The split key: the first split of the route not reached yet (the finish once all are). */
    public Optional<Split> splitNext(long now) {
        for (Split s : Split.values()) {
            if (!splits.containsKey(s) && split(s, now)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** Ends the run now: its times stop. */
    public void finish(long now) {
        if (phase != Phase.RUNNING) {
            return;
        }
        finalRta = rta(now);
        finalIgt = igt(now);
        splits.put(Split.FINISH, new SplitTime(finalRta, finalIgt));
        phase = Phase.FINISHED;
    }

    /** Real time since the start (0 while idle, the final time once finished). */
    public long rta(long now) {
        return switch (phase) {
            case IDLE -> 0;
            case RUNNING -> now - startedAt;
            case FINISHED -> finalRta;
        };
    }

    /** In-game time since the start: real time without pauses and loading screens. */
    public long igt(long now) {
        return switch (phase) {
            case IDLE -> 0;
            case RUNNING -> igtBanked + (igtSince < 0 ? 0 : now - igtSince);
            case FINISHED -> finalIgt;
        };
    }

    /** The splits reached, in route order. */
    public Map<Split, SplitTime> splits() {
        return Collections.unmodifiableMap(splits);
    }
}
