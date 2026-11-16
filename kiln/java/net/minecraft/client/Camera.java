package net.minecraft.client;

/**
 * Kiln's stand-in for {@code Camera}: Horizon's camera is its own. {@link #calculateFov}, which zoom mods inject into,
 * runs once a client tick for what their mixins make it give, and the view takes it (Kiln's Mixin bridge, D-105).
 */
public class Camera implements net.minecraft.world.waypoints.TrackedWaypoint.Camera {
    public Camera() {  }

    /**
     * The field of view the view takes, in degrees: the game's option here (Horizon adds its own effects: sprinting,
     * a fluid, the spyglass). What a mod's mixin makes it give over the option scales Horizon's view.
     */
    public float calculateFov(float partialTicks) {
        return kiln.Bridge.body((float) Minecraft.getInstance().options.fov().get(), partialTicks);
    }

    public net.minecraft.world.phys.Vec3 position() { kiln.Missing.called("net.minecraft.client.Camera.position"); return null; }
    public float yaw() { kiln.Missing.called("net.minecraft.client.Camera.yaw"); return 0.0F; }
    public net.minecraft.world.entity.Entity entity() { kiln.Missing.called("net.minecraft.client.Camera.entity"); return null; }
}
