package net.minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Kiln's stand-in for {@code CrashReport}: what went wrong, its exception and named sections of details, as text. A
 * mod makes one to throw (a {@link ReportedException}); Kiln's own crash reports are D-112's.
 */
public class CrashReport {
    private final String title;
    private final Throwable exception;
    private final List<CrashReportCategory> categories = new ArrayList<>();

    public CrashReport(String title, Throwable exception) {
        this.title = title;
        this.exception = exception;
    }

    public static CrashReport forThrowable(Throwable exception, String title) {
        return exception instanceof ReportedException reported ? reported.getReport() : new CrashReport(title, exception);
    }

    public String getTitle() {
        return title;
    }

    public Throwable getException() {
        return exception;
    }

    public CrashReportCategory addCategory(String name) {
        CrashReportCategory category = new CrashReportCategory(name);
        categories.add(category);
        return category;
    }

    public String getDetails() {
        StringBuilder out = new StringBuilder();
        for (CrashReportCategory category : categories) {
            category.getDetails(out);
            out.append("\n\n");
        }
        return out.toString();
    }

    public String getFriendlyReport() {
        java.io.StringWriter trace = new java.io.StringWriter();
        if (exception != null) {
            exception.printStackTrace(new java.io.PrintWriter(trace));
        }
        return "---- Crash report ----\nDescription: " + title + "\n\n" + trace + "\n" + getDetails();
    }
}
