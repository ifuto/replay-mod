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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures the world's terrain (block columns) while recording, so the replay
 * can reconstruct the world independently of the live client world.
 *
 * <p>Instead of snapshotting every new column in a single tick (which causes
 * a visible FPS spike), newly-seen columns are pushed onto a queue and drained
 * a few at a time across ticks. Each column is palette-compressed into a
 * {@code CHUNK} record; subsequent edits are captured by {@code BLOCK_CHANGE}.</p>
 */
public final class TerrainRecorder {
    /** Max columns written per tick (bounds the per-tick snapshot cost). */
    private static final int MAX_PER_TICK = 2;

    private final Set<Long> recorded = new HashSet<>();
    private final Set<Long> queued = new HashSet<>();
    private final Deque<int[]> queue = new ArrayDeque<>();

    public void reset() {
        recorded.clear();
        queued.clear();
        queue.clear();
    }

    public boolean hasPending() {
        return !queue.isEmpty();
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
        int height = world.getHeight();
        int topY = bottomY + height;

        Map<Integer, Integer> paletteMap = new HashMap<>();
        java.util.List<Integer> palette = new java.util.ArrayList<>();
        palette.add(Block.STATE_IDS.getRawId(net.minecraft.block.Blocks.AIR.getDefaultState()));
        paletteMap.put(palette.get(0), 0);

        int[] data = new int[16 * 16 * height];
        int i = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = bottomY; y < topY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    pos.set(originX + x, y, originZ + z);
                    BlockState state = world.getBlockState(pos);
                    int stateId = Block.STATE_IDS.getRawId(state);
                    Integer idx = paletteMap.get(stateId);
                    if (idx == null) {
                        idx = palette.size();
                        paletteMap.put(stateId, idx);
                        palette.add(stateId);
                    }
                    data[i++] = idx;
                }
            }
        }

        int[] paletteArr = new int[palette.size()];
        for (int p = 0; p < palette.size(); p++) {
            paletteArr[p] = palette.get(p);
        }
        return new ChunkColumn(originX, originZ, bottomY, height, paletteArr, data);
    }
}
