package dev.ifuto.fpsreplay.replay;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes records into a replay stream.
 *
 * <p><b>Lightweight design:</b> the per-tick record encodes the camera as
 * quantized deltas against the last keyframe, so a full tick is ~15 bytes
 * total instead of ~32 bytes of raw doubles. Keyframes (full precision,
 * plus entity snapshots) only appear once per {@code keyframeInterval} ticks.</p>
 *
 * <p>Quantization:</p>
 * <ul>
 *   <li>position delta: {@code short} per axis at 1/256-block resolution
 *       (covers +-128 blocks from the keyframe — plenty for one interval).</li>
 *   <li>rotation delta: {@code short} per axis at 0.01-degree resolution.</li>
 *   <li>fov: {@code byte} at 1-degree resolution (0-255 covers all of MC's range).</li>
 *   <li>hand-swing: {@code byte} at 0.01 resolution (0-100).</li>
 * </ul>
 */
public final class ReplayWriter implements AutoCloseable {
    /** Position delta scale: stored short = world * 256. */
    static final double POS_DELTA_SCALE = 256.0;
    /** Rotation delta scale: stored short = degrees * 100. */
    static final double ROT_DELTA_SCALE = 100.0;

    private final DataOutputStream out;
    private boolean closed;

    ReplayWriter(DataOutputStream body) {
        this.out = body;
    }

    public void writeKeyframe(long tick, CameraFrame cam, List<EntityFrame> entities) throws IOException {
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
        IoUtil.writeVarInt(out, entities.size());
        for (EntityFrame e : entities) {
            writeEntity(out, e);
        }
    }

    /**
     * Write a per-tick camera update as deltas against {@code key}.
     */
    public void writeTick(long tick, CameraFrame cam, CameraFrame key) throws IOException {
        out.writeByte(RecordType.TICK.id());
        IoUtil.writeVarInt(out, (int) tick);
        out.writeShort((short) Math.round((cam.x - key.x) * POS_DELTA_SCALE));
        out.writeShort((short) Math.round((cam.y - key.y) * POS_DELTA_SCALE));
        out.writeShort((short) Math.round((cam.z - key.z) * POS_DELTA_SCALE));
        out.writeShort((short) Math.round(shortestAngle(cam.yaw - key.yaw) * ROT_DELTA_SCALE));
        out.writeShort((short) Math.round(shortestAngle(cam.pitch - key.pitch) * ROT_DELTA_SCALE));
        out.writeByte((byte) Math.round(cam.fov));
        out.writeByte((byte) Math.round(cam.handSwingProgress * 100.0));
    }

    public void writeBlockChange(BlockChange change) throws IOException {
        out.writeByte(RecordType.BLOCK_CHANGE.id());
        out.writeInt(change.x);
        out.writeInt(change.y);
        out.writeInt(change.z);
        IoUtil.writeVarInt(out, change.stateId);
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
        // Flush + finish the gzip trailer, then the underlying stream.
        out.flush();
        out.close();
    }

    private static void writeEntity(DataOutputStream out, EntityFrame e) throws IOException {
        IoUtil.writeVarInt(out, e.entityId);
        IoUtil.writeVarInt(out, e.typeId);
        out.writeDouble(e.x);
        out.writeDouble(e.y);
        out.writeDouble(e.z);
        out.writeFloat(e.yaw);
        out.writeFloat(e.pitch);
        out.writeFloat(e.headYaw);
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
