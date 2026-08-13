package dev.ifuto.fpsreplay.replay;

/**
 * A single first-person camera sample: eye position + orientation + fov.
 *
 * <p>This is the core unit of a first-person replay. Everything the player
 * sees is reproducible from a sequence of these, interpolated at render time.</p>
 */
public final class CameraFrame {
    public final long tick;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final float roll;
    public final float fov;
    public final float handSwingProgress;

    public CameraFrame(long tick, double x, double y, double z,
                       float yaw, float pitch, float roll,
                       float fov, float handSwingProgress) {
        this.tick = tick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.fov = fov;
        this.handSwingProgress = handSwingProgress;
    }
}
