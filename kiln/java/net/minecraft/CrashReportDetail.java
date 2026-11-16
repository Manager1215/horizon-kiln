package net.minecraft;

import java.util.concurrent.Callable;

/** Kiln's stand-in for {@code CrashReportDetail}: a crash report's value, worked out when the report is written. */
@FunctionalInterface
public interface CrashReportDetail<V> extends Callable<V> {}
