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
     * Suitable for rendering; streaming/random-access is a future optimization.
     */
    public ReplayState readAll() throws IOException {
        ReplayState state = new ReplayState(metadata);
        CameraFrame lastKey = null;
        List<EntityFrame> keyEntities = null;

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
                    int count = IoUtil.readVarInt(body);
                    keyEntities = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        keyEntities.add(readEntity());
                    }
                    state.cameraFrames.add(cam);
                    state.applyKeyframeEntities(cam.tick, keyEntities);
                    lastKey = cam;
                }
                case TICK -> {
                    if (lastKey == null) {
                        throw new IOException("TICK record before any KEYFRAME");
                    }
                    long tick = IoUtil.readVarInt(body);
                    double dx = body.readShort() / ReplayWriter.POS_DELTA_SCALE;
                    double dy = body.readShort() / ReplayWriter.POS_DELTA_SCALE;
                    double dz = body.readShort() / ReplayWriter.POS_DELTA_SCALE;
                    float dyaw = body.readShort() / (float) ReplayWriter.ROT_DELTA_SCALE;
                    float dpitch = body.readShort() / (float) ReplayWriter.ROT_DELTA_SCALE;
                    float fov = body.readUnsignedByte();
                    float handSwing = body.readUnsignedByte() / 100.0f;

                    CameraFrame cam = new CameraFrame(
                            tick,
                            lastKey.x + dx, lastKey.y + dy, lastKey.z + dz,
                            lastKey.yaw + dyaw, lastKey.pitch + dpitch, lastKey.roll,
                            fov, handSwing);
                    state.cameraFrames.add(cam);
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
        int typeId = IoUtil.readVarInt(body);
        double x = body.readDouble();
        double y = body.readDouble();
        double z = body.readDouble();
        float yaw = body.readFloat();
        float pitch = body.readFloat();
        float headYaw = body.readFloat();
        return new EntityFrame(entityId, typeId, x, y, z, yaw, pitch, headYaw);
    }

    @Override
    public void close() throws IOException {
        gzip.close();
        raw.close();
    }
}
