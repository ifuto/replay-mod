package dev.ifuto.fpsreplay.replay;

/**
 * A quantized per-tick entity motion delta (position + rotation).
 *
 * <p>Encoding motion as deltas instead of full {@code double}/{@code float}
 * turns "entity moved 0.001 blocks" into a single-byte varint, and lets the
 * recorder skip entities that did not move at all (all-zero delta) — the
 * biggest recording cost is static mobs, and this makes them free.</p>
 */
public final class EntityDelta {
    public final int entityId;
    /** Position delta, stored = world * 4096. */
    public final int dx;
    public final int dy;
    public final int dz;
    /** Rotation delta, stored = degrees * 100. */
    public final int dyaw;
    public final int dpitch;
    public final int dheadYaw;

    public EntityDelta(int entityId, int dx, int dy, int dz, int dyaw, int dpitch, int dheadYaw) {
        this.entityId = entityId;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.dyaw = dyaw;
        this.dpitch = dpitch;
        this.dheadYaw = dheadYaw;
    }

    public boolean isZero() {
        return dx == 0 && dy == 0 && dz == 0 && dyaw == 0 && dpitch == 0 && dheadYaw == 0;
    }
}
