package net.minecraft;

/** Kiln's stand-in for {@code ReportedException}: an exception carrying its crash report. */
public class ReportedException extends RuntimeException {
    private final CrashReport report;

    public ReportedException(CrashReport report) {
        super(report.getTitle(), report.getException());
        this.report = report;
    }

    public CrashReport getReport() {
        return report;
    }

    @Override
    public String getMessage() {
        return report.getTitle();
    }
}
