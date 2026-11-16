package net.minecraft;

import java.util.ArrayList;
import java.util.List;

/** Kiln's stand-in for {@code CrashReportCategory}: a named section of a crash report, its details by name. */
public class CrashReportCategory {
    private final String title;
    private final List<String[]> details = new ArrayList<>();

    public CrashReportCategory(String title) {
        this.title = title;
    }

    public CrashReportCategory setDetail(String name, CrashReportDetail<?> detail) {
        String value;
        try {
            value = String.valueOf(detail.call());
        } catch (Throwable e) {
            value = "~~ERROR~~ " + e;
        }
        return setDetail(name, value);
    }

    public CrashReportCategory setDetail(String name, Object value) {
        details.add(new String[] {name, String.valueOf(value)});
        return this;
    }

    public void setDetailError(String name, Throwable error) {
        setDetail(name, "~~ERROR~~ " + error);
    }

    /** Its section as the game writes it: "-- title --" and "Details:" with each detail on a line. */
    public void getDetails(StringBuilder out) {
        out.append("-- ").append(title).append(" --\nDetails:");
        for (String[] detail : details) {
            out.append("\n\t").append(detail[0]).append(": ").append(detail[1]);
        }
    }
}
