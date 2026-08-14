package dev.ifuto.fpsreplay.client;

import net.minecraft.client.render.Camera;

/**
 * Captures the <b>final rendered camera</b> (not the raw entity eye position).
 *
 * <p>We sample the actual {@link Camera} position/yaw/pitch on every frame so
 * the replay reproduces the viewpoint exactly (including view bobbing). Roll
 * is intentionally NOT captured: extracting it from the rotation quaternion is
 * unreliable and caused the camera to flip upside-down during replay, so we
 * keep roll at 0 and rely on position/yaw/pitch only.</p>
 */
public final class CameraCapture {
    public static double x;
    public static double y;
    public static double z;
    public static float yaw;
    public static float pitch;
    public static boolean valid;

    private CameraCapture() {
    }

    /** Called from the camera mixin on every rendered frame. */
    public static void capture(Camera camera) {
        var pos = camera.getCameraPos();
        x = pos.x;
        y = pos.y;
        z = pos.z;
        yaw = camera.getCameraYaw();
        pitch = camera.getPitch();
        valid = true;
    }

    /** Reset the capture (called when recording starts). */
    public static void reset() {
        valid = false;
    }
}
