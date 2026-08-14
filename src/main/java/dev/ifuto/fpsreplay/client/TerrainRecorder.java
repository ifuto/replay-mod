package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.ChunkColumn;
import dev.ifuto.fpsreplay.replay.ReplayWriter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures the world's terrain (block columns) while recording, so the replay
 * can reconstruct the world independently of the live client world — the core
 * of being an OBS/ReplayMod-style recorder rather than a "re-render the live
 * world" hack.
 *
 * <p>When a chunk column enters the configured radius around the player for
 * the first time, its full block contents are palette-compressed and written
 * to the replay stream as a {@code CHUNK} record. Subsequent changes are
 * captured by the already-implemented {@code BLOCK_CHANGE} records.</p>
 */
public final class TerrainRecorder {
    private final Set<Long> recorded = new HashSet<>();

    public void reset() {
        recorded.clear();
    }

    /**
     * Snapshot any not-yet-recorded columns within {@code chunkRadius} chunks
     * of the player. Called every keyframe while recording.
     */
    public void tick(MinecraftClient client, ReplayWriter writer, int chunkRadius) throws IOException {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return;
        }
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                long key = ChunkColumn.key(cx << 4, cz << 4);
                if (recorded.contains(key)) {
                    continue;
                }
                recorded.add(key);
                ChunkColumn column = snapshotColumn(world, cx << 4, cz << 4);
                if (column != null) {
                    writer.writeChunkColumn(column);
                }
            }
        }
    }

    private ChunkColumn snapshotColumn(ClientWorld world, int originX, int originZ) {
        int bottomY = world.getBottomY();
        int height = world.getHeight();
        int topY = bottomY + height;

        Map<Integer, Integer> paletteMap = new HashMap<>();
        java.util.List<Integer> palette = new java.util.ArrayList<>();
        // Reserve index 0 for air.
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
