package dev.ifuto.fpsreplay.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * On-disk replay container.
 *
 * <p>Layout:</p>
 * <pre>
 *   [4 bytes magic "FPRL"]
 *   [4 bytes version, little-endian] = 1
 *   [metadata: fixed-order fields, see ReplayMetadata]
 *   [gzip-compressed record stream ... END]
 * </pre>
 *
 * <p>The record stream is gzip so the whole file stays small (recording is
 * "packets, not pixels") and is directly inspectable with standard tools.</p>
 */
public final class ReplayFile {
    public static final byte[] MAGIC = new byte[]{'F', 'P', 'R', 'L'};
    public static final int VERSION = 1;

    /** Default gzip level (0-9). Higher = smaller file, slightly more CPU. */
    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    private ReplayFile() {
    }

    /** Open a new file for writing and return a bound {@link ReplayWriter}. */
    public static ReplayWriter create(File file, ReplayMetadata metadata, int compressionLevel) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        FileOutputStream fos = new FileOutputStream(file);
        DataOutputStream header = new DataOutputStream(fos);
        header.write(MAGIC);
        header.writeInt(Integer.reverseBytes(VERSION)); // little-endian on disk
        writeMetadata(header, metadata);
        header.flush();

        OutputStream body = new GZIPOutputStream(fos, 8192) {
            {
                // GZIPOutputStream exposes no public constructor with a level,
                // so we swap the (protected) `def` Deflater for one with the
                // requested level while keeping the gzip framing. Release the
                // original default deflater to avoid leaking native memory.
                Deflater old = this.def;
                this.def = new Deflater(compressionLevel, true);
                old.end();
            }
        };
        return new ReplayWriter(new DataOutputStream(body));
    }

    /** Open an existing file for reading and return a bound {@link ReplayReader}. */
    public static ReplayReader open(File file) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(file));
        byte[] magic = new byte[4];
        in.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                in.close();
                throw new IOException("Not a replay file (bad magic): " + file);
            }
        }
        int version = Integer.reverseBytes(in.readInt());
        if (version != VERSION) {
            in.close();
            throw new IOException("Unsupported replay version: " + version + " (expected " + VERSION + ")");
        }
        ReplayMetadata metadata = readMetadata(in);
        return new ReplayReader(in, metadata);
    }

    private static void writeMetadata(DataOutputStream out, ReplayMetadata meta) throws IOException {
        IoUtil.writeString(out, meta.minecraftVersion == null ? "?" : meta.minecraftVersion);
        IoUtil.writeString(out, meta.worldName == null ? "?" : meta.worldName);
        out.writeLong(meta.worldSeed);
        out.writeInt(meta.tickRate);
        out.writeLong(meta.startTimeMillis);
        out.writeInt(meta.keyframeInterval);
    }

    private static ReplayMetadata readMetadata(DataInputStream in) throws IOException {
        ReplayMetadata meta = new ReplayMetadata();
        meta.minecraftVersion = IoUtil.readString(in);
        meta.worldName = IoUtil.readString(in);
        meta.worldSeed = in.readLong();
        meta.tickRate = in.readInt();
        meta.startTimeMillis = in.readLong();
        meta.keyframeInterval = in.readInt();
        return meta;
    }
}
