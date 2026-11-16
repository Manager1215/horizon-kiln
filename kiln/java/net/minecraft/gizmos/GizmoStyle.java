package net.minecraft.gizmos;

/** Kiln's stand-in for {@code GizmoStyle}: a gizmo's outline colour and width, and its fill colour (ARGB, 0 for none). */
public record GizmoStyle(int stroke, float strokeWidth, int fill) {
    public static GizmoStyle stroke(int argb) {
        return new GizmoStyle(argb, 2.5F, 0);
    }

    public static GizmoStyle stroke(int argb, float width) {
        return new GizmoStyle(argb, width, 0);
    }

    public static GizmoStyle fill(int argb) {
        return new GizmoStyle(0, 0.0F, argb);
    }

    public static GizmoStyle strokeAndFill(int stroke, float width, int fill) {
        return new GizmoStyle(stroke, width, fill);
    }

    public boolean hasFill() {
        return fill != 0;
    }

    public boolean hasStroke() {
        return stroke != 0;
    }

    public int multipliedStroke(float alpha) {
        return multiplied(stroke, alpha);
    }

    public int multipliedFill(float alpha) {
        return multiplied(fill, alpha);
    }

    private static int multiplied(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return a << 24 | (argb & 0xFFFFFF);
    }
}
