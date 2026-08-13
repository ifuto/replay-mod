package dev.ifuto.fpsreplay.replay;

/**
 * A single world-state mutation captured during recording (a "block packet").
 *
 * <p>The block is stored as a raw registry id for compactness; it is resolved
 * back to a {@code BlockState} when the replay is loaded on a matching version.</p>
 */
public final class BlockChange {
    public final int x;
    public final int y;
    public final int z;
    public final int stateId;

    public BlockChange(int x, int y, int z, int stateId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.stateId = stateId;
    }
}
