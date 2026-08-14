package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.ChunkColumn;
import dev.ifuto.fpsreplay.replay.ReplayWriter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Captures only the <b>visible surface</b> of the terrain while recording —
 * for each 16x16 column, the topmost non-air block per (x,z) and its height.
 * This keeps recording cheap (no full 3D column scans) and avoids FPS spikes
 * by draining a couple of columns per tick.
 */
public final class TerrainRecorder {
    private static final int MAX_PER_TICK = 2;

    private final Set<Long> recorded = new HashSet<>();
    private final Set<Long> queued = new HashSet<>();
    private final Deque<int[]> queue = new ArrayDeque<>();

    public void reset() {
        recorded.clear();
        queued.clear();
        queue.clear();
    }

    /** Enqueue not-yet-recorded columns within {@code chunkRadius} of the player. */
    public void enqueueRadius(MinecraftClient client, int chunkRadius) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return;
        }
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                int ox = (pcx + dx) << 4;
                int oz = (pcz + dz) << 4;
                long key = ChunkColumn.key(ox, oz);
                if (recorded.contains(key) || queued.contains(key)) {
                    continue;
                }
                queued.add(key);
                queue.add(new int[]{ox, oz});
            }
        }
    }

    /** Drain up to {@link #MAX_PER_TICK} queued columns (called every tick). */
    public void drain(MinecraftClient client, ReplayWriter writer) throws IOException {
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }
        int written = 0;
        while (written < MAX_PER_TICK && !queue.isEmpty()) {
            int[] c = queue.poll();
            long key = ChunkColumn.key(c[0], c[1]);
            queued.remove(key);
            if (recorded.contains(key)) {
                continue;
            }
            recorded.add(key);
            ChunkColumn column = snapshotColumn(world, c[0], c[1]);
            if (column != null) {
                writer.writeChunkColumn(column);
                written++;
            }
        }
    }

    private ChunkColumn snapshotColumn(ClientWorld world, int originX, int originZ) {
        int bottomY = world.getBottomY();
        int topY = bottomY + world.getHeight();
        int airId = Block.STATE_IDS.getRawId(net.minecraft.block.Blocks.AIR.getDefaultState());

        int[] heights = new int[256];
        int[] states = new int[256];
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = originX + lx;
                int z = originZ + lz;
                int foundY = bottomY - 1;
                int foundState = airId;
                // Scan top-down: first non-air block is the visible surface.
                for (int y = topY - 1; y >= bottomY; y--) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    foundY = y;
                    foundState = Block.STATE_IDS.getRawId(state);
                    break;
                }
                int idx = ChunkColumn.index(lx, lz);
                heights[idx] = foundY;
                states[idx] = foundState;
            }
        }
        return new ChunkColumn(originX, originZ, bottomY, heights, states);
    }
}
