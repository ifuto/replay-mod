package dev.ifuto.fpsreplay.client;

import net.minecraft.client.render.Camera;

/**
 * Captures the <b>final rendered camera</b> (not the raw entity eye position).
 *
 * <p>The game applies view bobbing, damage tilt (roll), third-person offsets
 * and FOV effects at render time. By sampling the actual {@link Camera} and the
 * FOV from {@code GameRenderer}, the replay reproduces the viewpoint exactly —
 * including bobbing and roll — instead of reconstructing it from entity state.</p>
 */
public final class CameraCapture {
    public static double x;
    public static double y;
    public static double z;
    public static float yaw;
    public static float pitch;
    public static float roll;
    public static float fov;
    public static boolean valid;

    private CameraCapture() {
    }

    /** Called from the camera mixin on every rendered frame. */
    public static void capture(Camera camera) {
        x = camera.getPos().x;
        y = camera.getPos().y;
        z = camera.getPos().z;
        yaw = camera.getYaw();
        pitch = camera.getPitch();
        roll = extractRoll(camera);
        valid = true;
    }

    /** Called from the game renderer mixin on every rendered frame. */
    public static void captureFov(double fov) {
        CameraCapture.fov = (float) fov;
    }

    /**
     * Best-effort extraction of the roll component from the camera's rotation
     * quaternion (view bob + damage tilt). Roll is small in first person, so
     * any residual sign/order error here is cosmetic.
     */
    private static float extractRoll(Camera camera) {
        try {
            org.joml.Quaternionf q = camera.getRotation();
            org.joml.Vector3f euler = q.getEulerAnglesZYX(new org.joml.Vector3f());
            // roll = rotation around the view (forward) axis
            return (float) Math.toDegrees(euler.z);
        } catch (Throwable t) {
            return 0.0f;
        }
    }

    /** Reset the capture (called when recording starts). */
    public static void reset() {
        valid = false;
        fov = 70.0f;
        roll = 0.0f;
    }
}
