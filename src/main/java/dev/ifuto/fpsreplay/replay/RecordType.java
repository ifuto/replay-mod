package dev.ifuto.fpsreplay.replay;

/**
 * The kinds of records that can appear in a replay stream.
 *
 * <p>Each record is {@code [1 byte type][payload]}. The stream is:
 * {@code [header] KEYFRAME (TICK* | BLOCK_CHANGE*)* END}, gzip-compressed.</p>
 */
public enum RecordType {
    /**
     * Full-precision snapshot of the camera and all nearby entities.
     * Written once every {@code keyframeInterval} ticks. Serves as the anchor
     * that subsequent {@link #TICK} records encode deltas against.
     */
    KEYFRAME((byte) 1),

    /**
     * Per-tick camera update, encoded as quantized deltas relative to the last
     * keyframe. This is the hot record and is kept tiny (11 bytes + framing).
     */
    TICK((byte) 2),

    /** A single block change at an absolute position. Written on demand. */
    BLOCK_CHANGE((byte) 3),

    /** Terminator marking a clean end of stream. */
    END((byte) 0x7F);

    private final byte id;

    RecordType(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    public static RecordType fromId(byte id) {
        for (RecordType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown record type id: " + id);
    }
}
