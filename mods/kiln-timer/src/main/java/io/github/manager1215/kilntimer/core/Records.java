package io.github.manager1215.kilntimer.core;

import io.github.manager1215.kilntimer.core.Timer.SplitTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The runner's records: the personal best run (the fastest finished run by IGT, with its splits, which the
 * deltas compare to) and the best time each split was ever reached in (by IGT). Kept as a small text file.
 */
public final class Records {
    private final EnumMap<Split, SplitTime> pb = new EnumMap<>(Split.class);
    private final EnumMap<Split, Long> best = new EnumMap<>(Split.class);

    /** The personal best's splits; FINISH is its final time. Empty before a first finished run. */
    public Map<Split, SplitTime> pb() {
        return pb;
    }

    /** The personal best's final IGT, if there is one. */
    public Optional<Long> pbIgt() {
        return Optional.ofNullable(pb.get(Split.FINISH)).map(SplitTime::igt);
    }

    /** The best IGT a split was ever reached in. */
    public Optional<Long> best(Split split) {
        return Optional.ofNullable(best.get(split));
    }

    /** {@code igt} against the personal best's time for {@code split}: negative is ahead. */
    public Optional<Long> delta(Split split, long igt) {
        return Optional.ofNullable(pb.get(split)).map(t -> igt - t.igt());
    }

    /**
     * A run's splits go into the records; a finished run faster than the personal best (or the first one)
     * becomes it. True when it did: a new personal best.
     */
    public boolean record(Map<Split, SplitTime> splits) {
        splits.forEach((split, time) -> best.merge(split, time.igt(), Math::min));
        SplitTime finish = splits.get(Split.FINISH);
        if (finish == null || pbIgt().filter(old -> old <= finish.igt()).isPresent()) {
            return false;
        }
        pb.clear();
        pb.putAll(splits);
        return true;
    }

    /** One line a time: {@code pb.NETHER=<rta> <igt>} and {@code best.NETHER=<igt>}, in nanoseconds. */
    public String save() {
        StringBuilder out = new StringBuilder("# Kiln Speedrun Timer records: times in nanoseconds\n");
        pb.forEach((s, t) -> out.append("pb.").append(s.name()).append('=').append(t.rta()).append(' ')
                .append(t.igt()).append('\n'));
        best.forEach((s, igt) -> out.append("best.").append(s.name()).append('=').append(igt).append('\n'));
        return out.toString();
    }

    /** What {@link #save()} wrote; lines it can't read are skipped. */
    public static Records load(String text) {
        Records r = new Records();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (line.startsWith("#") || eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String[] value = line.substring(eq + 1).trim().split(" ");
            try {
                if (key.startsWith("pb.") && value.length == 2) {
                    Split s = Split.valueOf(key.substring(3));
                    r.pb.put(s, new SplitTime(Long.parseLong(value[0]), Long.parseLong(value[1])));
                } else if (key.startsWith("best.") && value.length == 1) {
                    r.best.put(Split.valueOf(key.substring(5)), Long.parseLong(value[0]));
                }
            } catch (IllegalArgumentException skipped) {
                // An unknown split or a damaged number: the rest still loads.
            }
        }
        return r;
    }
}
