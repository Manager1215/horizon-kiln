package net.minecraft.gizmos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code Gizmos}: shapes mods draw in the world for a tick (a box around a block, a line, a
 * text over a block), given to the collector the game set for the tick ({@link #withCollector}). Kiln can't draw in
 * Horizon's world yet: the game's collector takes them and draws none (said once in Kiln's log). A box given as an
 * {@code AABB} isn't here yet.
 */
public class Gizmos {
    private static final ThreadLocal<GizmoCollector> COLLECTOR = ThreadLocal.withInitial(() -> GizmoCollector.NOOP);

    /** The gizmos added until the collection closes go to {@code collector}. */
    public static TemporaryCollection withCollector(GizmoCollector collector) {
        TemporaryCollection collection = new TemporaryCollection(COLLECTOR.get());
        COLLECTOR.set(collector);
        return collection;
    }

    public static GizmoProperties addGizmo(Gizmo gizmo) {
        GizmoCollector collector = COLLECTOR.get();
        if (collector == GizmoCollector.NOOP) {
            kiln.Missing.called("shapes drawn in the world (Gizmos)");
        }
        return collector.add(gizmo);
    }

    public static GizmoProperties cuboid(BlockPos pos, GizmoStyle style) {
        return cuboid(pos, 0.0F, style);
    }

    public static GizmoProperties cuboid(BlockPos pos, float margin, GizmoStyle style) {
        return addGizmo(new Gizmo() {});
    }

    public static GizmoProperties circle(Vec3 center, float radius, GizmoStyle style) {
        return addGizmo(new Gizmo() {});
    }

    public static GizmoProperties line(Vec3 from, Vec3 to, int argb) {
        return line(from, to, argb, 2.5F);
    }

    public static GizmoProperties line(Vec3 from, Vec3 to, int argb, float width) {
        return addGizmo(new Gizmo() {});
    }

    public static GizmoProperties arrow(Vec3 from, Vec3 to, int argb) {
        return arrow(from, to, argb, 2.5F);
    }

    public static GizmoProperties arrow(Vec3 from, Vec3 to, int argb, float width) {
        return addGizmo(new Gizmo() {});
    }

    public static GizmoProperties point(Vec3 at, int argb, float size) {
        return addGizmo(new Gizmo() {});
    }

    public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int line, int argb, float scale) {
        return addGizmo(new Gizmo() {});
    }

    /** What the collection set takes back when it closes: the collector before it. */
    public static class TemporaryCollection implements AutoCloseable {
        private final GizmoCollector before;

        TemporaryCollection(GizmoCollector before) {
            this.before = before;
        }

        @Override
        public void close() {
            COLLECTOR.set(before);
        }
    }
}
