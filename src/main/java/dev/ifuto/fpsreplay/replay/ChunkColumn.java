package dev.ifuto.fpsreplay.replay;

/**
 * A recorded 16x16 surface slice of terrain — only the topmost visible block
 * per (x, z) column, plus its height. This is what the camera actually sees,
 * so it reproduces the visible world while costing ~400x less than a full
 * 3D chunk column (256 blocks instead of ~100k).
 */
public final class ChunkColumn {
    /** Block X of the column origin (chunkX * 16). */
    public final int originX;
    /** Block Z of the column origin (chunkZ * 16). */
    public final int originZ;
    public final int bottomY;
    /** 256 top-solid-block Y per (x, z); {@code bottomY - 1} if empty. */
    public final int[] heights;
    /** 256 raw block-state registry ids at the surface. */
    public final int[] states;

    public ChunkColumn(int originX, int originZ, int bottomY, int[] heights, int[] states) {
        this.originX = originX;
        this.originZ = originZ;
        this.bottomY = bottomY;
        this.heights = heights;
        this.states = states;
    }

    public static long key(int originX, int originZ) {
        return ((long) originX << 32) | (originZ & 0xFFFFFFFFL);
    }

    public long key() {
        return key(originX, originZ);
    }

    /** Index into heights/states for a local (x, z) within the 16x16 column. */
    public static int index(int lx, int lz) {
        return lz * 16 + lx;
    }
}
