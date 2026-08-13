package dev.ifuto.fpsreplay.replay;

/**
 * A snapshot of a single non-player entity (position + rotation + vitals).
 *
 * <p>Written at keyframes (not every tick) to keep recording cheap; motion
 * between keyframes is reconstructed by interpolation at render time.</p>
 *
 * <p>Health / max-health / custom-name / flags are captured so that other
 * mobs (including modded ones) are reproduced faithfully — a wounded iron
 * golem shows the same cracks, a named pet shows the same name tag.</p>
 */
public final class EntityFrame {
    public static final int FLAG_GLOWING = 1;
    public static final int FLAG_SNEAKING = 2;
    public static final int FLAG_SPRINTING = 4;

    public final int entityId;
    /** World tick at which this snapshot was taken (the owning keyframe's tick). */
    public final long tick;
    /** Raw registry id of the entity type (resolved against the local registry on read). */
    public final int typeId;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final float headYaw;
    /** Current health, or -1 if the entity is not a living entity. */
    public final float health;
    /** Max health, or -1 if not living. */
    public final float maxHealth;
    /** Custom name as a JSON text string, or null. */
    public final String customName;
    public final int flags;

    public EntityFrame(int entityId, long tick, int typeId,
                       double x, double y, double z,
                       float yaw, float pitch, float headYaw,
                       float health, float maxHealth, String customName, int flags) {
        this.entityId = entityId;
        this.tick = tick;
        this.typeId = typeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = headYaw;
        this.health = health;
        this.maxHealth = maxHealth;
        this.customName = customName;
        this.flags = flags;
    }
}
