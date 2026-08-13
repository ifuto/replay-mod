package dev.ifuto.fpsreplay.replay;

/**
 * Static metadata stored in the replay file header.
 *
 * <p>Stored before the gzip body so the file can be identified and inspected
 * without decompressing the whole stream.</p>
 */
public final class ReplayMetadata {
    public String minecraftVersion;
    public String worldName;
    public long worldSeed;
    /** Ticks per second the world was running at (normally 20). */
    public int tickRate = 20;
    /** Epoch millis when recording started. */
    public long startTimeMillis;
    /** How many ticks between keyframes (entity snapshot granularity). */
    public int keyframeInterval = 20;

    public ReplayMetadata() {
    }

    public ReplayMetadata(String minecraftVersion, String worldName, long worldSeed,
                          int tickRate, long startTimeMillis, int keyframeInterval) {
        this.minecraftVersion = minecraftVersion;
        this.worldName = worldName;
        this.worldSeed = worldSeed;
        this.tickRate = tickRate;
        this.startTimeMillis = startTimeMillis;
        this.keyframeInterval = keyframeInterval;
    }
}
