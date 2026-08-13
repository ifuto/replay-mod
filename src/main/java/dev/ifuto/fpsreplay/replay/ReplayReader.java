package dev.ifuto.fpsreplay.replay;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads a replay stream and materializes it into a {@link ReplayState}.
 */
public final class ReplayReader implements AutoCloseable {
    private final ReplayMetadata metadata;
    private final DataInputStream body;
    private final GZIPInputStream gzip;
    private final DataInputStream raw;

    ReplayReader(DataInputStream raw, ReplayMetadata metadata) throws IOException {
        this.raw = raw;
        this.metadata = metadata;
        this.gzip = new GZIPInputStream(raw);
        this.body = new DataInputStream(gzip);
    }

    public ReplayMetadata metadata() {
        return metadata;
    }

    /**
     * Read every record and build an in-memory {@link ReplayState}.
     */
    public ReplayState readAll() throws IOException {
        ReplayState state = new ReplayState(metadata);
        CameraFrame lastKey = null;
        long lastTick = Long.MIN_VALUE;

        while (true) {
            int id = body.readUnsignedByte();
            RecordType type = RecordType.fromId((byte) id);

            switch (type) {
                case KEYFRAME -> {
                    long tick = IoUtil.readVarInt(body);
                    CameraFrame cam = new CameraFrame(
                            tick,
                            body.readDouble(), body.readDouble(), body.readDouble(),
                            body.readFloat(), body.readFloat(), body.readFloat(),
                            body.readFloat(), body.readFloat());
                    HudState hud = HudState.read(body);
                    int count = body.readInt();
                    List<EntityFrame> entities = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entities.add(readEntity());
                    }
                    state.cameraFrames.add(cam);
                    state.hudStates.put(tick, hud);
                    for (EntityFrame e : entities) {
                        state.entityTracks.computeIfAbsent(e.entityId, k -> new ArrayList<>()).add(e);
                    }
                    lastKey = cam;
                    lastTick = tick;
                }
                case TICK -> {
                    if (lastKey == null) {
                        throw new IOException("TICK record before any KEYFRAME");
                    }
                    long tick = lastTick + IoUtil.readVarIntZigZag(body);
                    double dx = IoUtil.readVarIntZigZag(body) / ReplayWriter.POS_SCALE;
                    double dy = IoUtil.readVarIntZigZag(body) / ReplayWriter.POS_SCALE;
                    double dz = IoUtil.readVarIntZigZag(body) / ReplayWriter.POS_SCALE;
                    float dyaw = IoUtil.readVarIntZigZag(body) / (float) ReplayWriter.ROT_SCALE;
                    float dpitch = IoUtil.readVarIntZigZag(body) / (float) ReplayWriter.ROT_SCALE;
                    float droll = IoUtil.readVarIntZigZag(body) / (float) ReplayWriter.ROT_SCALE;
                    float dfov = IoUtil.readVarIntZigZag(body) / (float) ReplayWriter.ROT_SCALE;
                    float handSwing = IoUtil.readVarIntZigZag(body) / (float) ReplayWriter.HAND_SCALE;

                    CameraFrame cam = new CameraFrame(
                            tick,
                            lastKey.x + dx, lastKey.y + dy, lastKey.z + dz,
                            lastKey.yaw + dyaw, lastKey.pitch + dpitch, lastKey.roll + droll,
                            lastKey.fov + dfov, handSwing);
                    state.cameraFrames.add(cam);
                    lastTick = tick;
                }
                case BLOCK_CHANGE -> {
                    int x = body.readInt();
                    int y = body.readInt();
                    int z = body.readInt();
                    int stateId = IoUtil.readVarInt(body);
                    state.blockChanges.add(new BlockChange(x, y, z, stateId));
                }
                case END -> {
                    return state;
                }
            }
        }
    }

    private EntityFrame readEntity() throws IOException {
        int entityId = IoUtil.readVarInt(body);
        long tick = IoUtil.readVarInt(body);
        int typeId = IoUtil.readVarInt(body);
        double x = body.readDouble();
        double y = body.readDouble();
        double z = body.readDouble();
        float yaw = body.readFloat();
        float pitch = body.readFloat();
        float headYaw = body.readFloat();
        float health = body.readFloat();
        float maxHealth = body.readFloat();
        String customName = body.readBoolean() ? IoUtil.readString(body) : null;
        int flags = body.readUnsignedByte();
        return new EntityFrame(entityId, tick, typeId, x, y, z, yaw, pitch, headYaw, health, maxHealth, customName, flags);
    }

    @Override
    public void close() throws IOException {
        gzip.close();
        raw.close();
    }
}
