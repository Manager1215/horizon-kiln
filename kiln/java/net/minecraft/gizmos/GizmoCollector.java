package net.minecraft.gizmos;

/** Kiln's stand-in for {@code GizmoCollector}: where the gizmos mods add go. */
public interface GizmoCollector {
    /** What an ignored gizmo's properties are: setting them does nothing. */
    GizmoProperties IGNORED = new GizmoProperties() {
        @Override
        public GizmoProperties setAlwaysOnTop() {
            return this;
        }

        @Override
        public GizmoProperties persistForMillis(int millis) {
            return this;
        }

        @Override
        public GizmoProperties fadeOut() {
            return this;
        }
    };
    /** A collector that keeps nothing. */
    GizmoCollector NOOP = gizmo -> IGNORED;

    GizmoProperties add(Gizmo gizmo);
}
