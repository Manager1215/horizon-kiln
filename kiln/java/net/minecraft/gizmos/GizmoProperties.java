package net.minecraft.gizmos;

/** Kiln's stand-in for {@code GizmoProperties}: how a gizmo a mod added draws (on top, for how long). */
public interface GizmoProperties {
    GizmoProperties setAlwaysOnTop();

    GizmoProperties persistForMillis(int millis);

    GizmoProperties fadeOut();
}
