package dev.ifuto.fpsreplay.client;

import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Thin wrapper around JCodec's {@link AWTSequenceEncoder} for writing an H.264
 * MP4 from a stream of {@link BufferedImage} frames.
 *
 * <p>Pure-Java, no ffmpeg required — this is what enables "mp4 で好きな解像度・
 * 好きな FPS での出力" directly from the mod.</p>
 */
public final class Mp4Exporter implements AutoCloseable {
    private final AWTSequenceEncoder encoder;

    public Mp4Exporter(File out, int fps) throws IOException {
        this.encoder = AWTSequenceEncoder.createSequenceEncoder(out, fps);
    }

    public void addFrame(BufferedImage frame) throws IOException {
        encoder.encodeImage(frame);
    }

    @Override
    public void close() throws IOException {
        encoder.finish();
    }
}
