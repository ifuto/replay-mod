package dev.ifuto.fpsreplay.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory representation of a loaded replay: an ordered camera track,
 * per-entity tracks (snapshotted at keyframes), and the block-change log.
 *
 * <p>This is what the renderer consumes. The camera track is per-tick (smooth),
 * entity tracks are per-keyframe (interpolated at render time), and block
 * changes are applied in order to reconstruct world state.</p>
 */
public final class ReplayState {
    public final ReplayMetadata metadata;
    /** Camera samples, ascending by tick (one per tick). */
    public final List<CameraFrame> cameraFrames = new ArrayList<>();
    /** Entity frames grouped by entity id, ascending by tick. */
    public final Map<Integer, List<EntityFrame>> entityTracks = new HashMap<>();
    /** Block changes in recording order. */
    public final List<BlockChange> blockChanges = new ArrayList<>();
    /** HUD snapshots keyed by the tick they were captured at. */
    public final Map<Long, HudState> hudStates = new HashMap<>();

    public ReplayState(ReplayMetadata metadata) {
        this.metadata = metadata;
    }

    /** Find the most recent HUD snapshot at or before {@code tick}. */
    public HudState hudStateAt(long tick) {
        HudState best = null;
        long bestTick = Long.MIN_VALUE;
        for (Map.Entry<Long, HudState> e : hudStates.entrySet()) {
            if (e.getKey() <= tick && e.getKey() > bestTick) {
                bestTick = e.getKey();
                best = e.getValue();
            }
        }
        return best;
    }

    public long startTick() {
        return cameraFrames.isEmpty() ? 0 : cameraFrames.get(0).tick;
    }

    public long endTick() {
        return cameraFrames.isEmpty() ? 0 : cameraFrames.get(cameraFrames.size() - 1).tick;
    }

    public double durationSeconds() {
        return (endTick() - startTick()) / (double) metadata.tickRate;
    }

    /** Find the camera frame index for a given tick (or the nearest before it). */
    public int cameraIndexForTick(double tick) {
        List<CameraFrame> frames = cameraFrames;
        if (frames.isEmpty()) {
            return -1;
        }
        int lo = 0;
        int hi = frames.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (frames.get(mid).tick <= tick) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
