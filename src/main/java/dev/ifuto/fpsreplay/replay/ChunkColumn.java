package dev.ifuto.fpsreplay.replay;

/**
 * A recorded 16x16 block-column (one chunk column) of terrain.
 *
 * <p>Stores every block state in the column as palette indices so the replay
 * can rebuild the world independently of the live client world. Air is always
 * palette index 0.</p>
 */
public final class ChunkColumn {
    /** Block X of the column origin (chunkX * 16). */
    public final int originX;
    /** Block Z of the column origin (chunkZ * 16). */
    public final int originZ;
    public final int bottomY;
    public final int height;
    /** Block state registry ids, palette[0] = air. */
    public final int[] palette;
    /** Palette index per block, length = 16 * 16 * height, y/x/z order. */
    public final int[] data;

    public ChunkColumn(int originX, int originZ, int bottomY, int height, int[] palette, int[] data) {
        this.originX = originX;
        this.originZ = originZ;
        this.bottomY = bottomY;
        this.height = height;
        this.palette = palette;
        this.data = data;
    }

    public static long key(int originX, int originZ) {
        return ((long) originX << 32) | (originZ & 0xFFFFFFFFL);
    }

    public long key() {
        return key(originX, originZ);
    }

    public int blockIndex(int x, int y, int z) {
        int lx = x - originX;
        int lz = z - originZ;
        int ly = y - bottomY;
        return (ly * 16 + lz) * 16 + lx;
    }
}
