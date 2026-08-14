package dev.ifuto.fpsreplay.replay;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes records into a replay stream.
 *
 * <p><b>Extreme-lightweight design.</b> The per-tick record is the hot path,
 * so it is kept allocation-free and byte-minimal:</p>
 * <ul>
 *   <li>No tick stored per record — the tick is encoded as a zigzag <i>delta</i>
 *       from the previous record (normally {@code 1} → a single byte).</li>
 *   <li>Position encoded as a zigzag varint delta from the last keyframe at
 *       1/4096-block resolution (~0.24&nbsp;mm) — enough to reproduce the
 *       camera exactly while staying 1–3 bytes per axis.</li>
 *   <li>Rotation (yaw/pitch/roll) and fov as zigzag varint deltas at
 *       0.01&deg; resolution.</li>
 *   <li>Hand-swing as a zigzag varint at 0.001 resolution.</li>
 * </ul>
 *
 * <p>Keyframes (full {@code double}/{@code float} precision + entity snapshot +
 * HUD snapshot) are written only every {@code keyframeInterval} ticks and
 * serve as the anchor for the delta encoding.</p>
 */
public final class ReplayWriter implements AutoCloseable {
    /** Position delta scale: stored varint = world * 4096. */
    static final double POS_SCALE = 4096.0;
    /** Rotation/fov delta scale: stored varint = degrees * 100. */
    static final double ROT_SCALE = 100.0;
    /** Hand-swing scale: stored varint = swing * 1000. */
    static final double HAND_SCALE = 1000.0;

    private final DataOutputStream out;
    private long lastTick = Long.MIN_VALUE;
    private boolean closed;

    ReplayWriter(DataOutputStream body) {
        this.out = body;
    }

    /**
     * Write a full-precision keyframe with entity + HUD snapshots.
     */
    public void writeKeyframe(long tick, CameraFrame cam, HudState hud, List<EntityFrame> entities) throws IOException {
        out.writeByte(RecordType.KEYFRAME.id());
        IoUtil.writeVarInt(out, (int) tick);
        out.writeDouble(cam.x);
        out.writeDouble(cam.y);
        out.writeDouble(cam.z);
        out.writeFloat(cam.yaw);
        out.writeFloat(cam.pitch);
        out.writeFloat(cam.roll);
        out.writeFloat(cam.fov);
        out.writeFloat(cam.handSwingProgress);

        // HUD state snapshot (vitals / effects / scoreboard / player list).
        hud.write(out);

        // Entity snapshot.
        out.writeInt(entities.size());
        for (EntityFrame e : entities) {
            writeEntity(e, tick);
        }
        lastTick = tick;
    }

    /**
     * Write a per-tick camera update as quantized deltas against {@code key}.
     * Allocation-free: all values are primitives.
     */
    public void writeTick(long tick,
                          double dx, double dy, double dz,
                          float dyaw, float dpitch, float droll,
                          float dfov, float handSwing) throws IOException {
        out.writeByte(RecordType.TICK.id());
        IoUtil.writeVarIntZigZag(out, (int) (tick - lastTick));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(dx * POS_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(dy * POS_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(dz * POS_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(shortestAngle(dyaw) * ROT_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(shortestAngle(dpitch) * ROT_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(shortestAngle(droll) * ROT_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(dfov * ROT_SCALE));
        IoUtil.writeVarIntZigZag(out, (int) Math.round(handSwing * HAND_SCALE));
        lastTick = tick;
    }

    public void writeBlockChange(BlockChange change) throws IOException {
        out.writeByte(RecordType.BLOCK_CHANGE.id());
        IoUtil.writeVarInt(out, (int) change.tick);
        out.writeInt(change.x);
        out.writeInt(change.y);
        out.writeInt(change.z);
        IoUtil.writeVarInt(out, change.stateId);
    }

    /** Write a full terrain chunk column (palette-compressed). */
    public void writeChunkColumn(ChunkColumn column) throws IOException {
        out.writeByte(RecordType.CHUNK.id());
        out.writeInt(column.originX);
        out.writeInt(column.originZ);
        out.writeInt(column.bottomY);
        IoUtil.writeVarInt(out, column.height);

        IoUtil.writeVarInt(out, column.palette.length);
        for (int stateId : column.palette) {
            IoUtil.writeVarInt(out, stateId);
        }
        for (int idx : column.data) {
            IoUtil.writeVarInt(out, idx);
        }
    }

    public void writeEnd() throws IOException {
        out.writeByte(RecordType.END.id());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        out.flush();
        out.close();
    }

    private void writeEntity(EntityFrame e, long tick) throws IOException {
        IoUtil.writeVarInt(out, e.entityId);
        IoUtil.writeVarInt(out, (int) tick);
        IoUtil.writeVarInt(out, e.typeId);
        out.writeDouble(e.x);
        out.writeDouble(e.y);
        out.writeDouble(e.z);
        out.writeFloat(e.yaw);
        out.writeFloat(e.pitch);
        out.writeFloat(e.headYaw);
        out.writeFloat(e.health);
        out.writeFloat(e.maxHealth);
        out.writeBoolean(e.customName != null);
        if (e.customName != null) {
            IoUtil.writeString(out, e.customName);
        }
        out.writeByte(e.flags);
    }

    /** Wrap an angle difference to [-180, 180). */
    static float shortestAngle(float delta) {
        float d = delta % 360.0f;
        if (d >= 180.0f) {
            d -= 360.0f;
        } else if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
    }
}
