package dev.ifuto.fpsreplay.replay;

/**
 * A snapshot of a single non-player entity (position + rotation).
 *
 * <p>Written at keyframes only (not every tick) to keep recording cheap;
 * motion between keyframes is reconstructed by interpolation at render time.</p>
 */
public final class EntityFrame {
    public final int entityId;
    /** Raw registry id of the entity type (resolved against the local registry on read). */
    public final int typeId;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final float headYaw;

    public EntityFrame(int entityId, int typeId,
                       double x, double y, double z,
                       float yaw, float pitch, float headYaw) {
        this.entityId = entityId;
        this.typeId = typeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = headYaw;
    }
}
