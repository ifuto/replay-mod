package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.CameraFrame;
import dev.ifuto.fpsreplay.replay.Interpolation;
import dev.ifuto.fpsreplay.replay.ReplayFile;
import dev.ifuto.fpsreplay.replay.ReplayReader;
import dev.ifuto.fpsreplay.replay.ReplayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Offline renderer. Replays a recorded {@link ReplayState} by driving the
 * client's normal render loop and capturing each frame to an offscreen
 * framebuffer at <b>any</b> resolution (e.g. 4K/8K) and <b>any</b> framerate
 * (e.g. 360fps) — decoupled from what was recorded. Sub-tick motion is
 * synthesized by interpolating between recorded samples.
 */
public final class Renderer {
    private static Renderer instance;

    private final ReplayState state;
    private final int width;
    private final int height;
    private final int fps;
    private final boolean spline;
    private final File outDir;

    private Framebuffer renderFb;
    private double currentTick;
    private double dt;
    private long frameIndex;
    private float eyeHeight = 1.62f;
    private double originalFov;

    private Renderer(ReplayState state, int width, int height, int fps, File outDir) {
        this.state = state;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.outDir = outDir;
        this.spline = "spline".equalsIgnoreCase(ReplayConfig.interpolationMode);
    }

    public static boolean isRendering() {
        return instance != null;
    }

    public static Renderer active() {
        return instance;
    }

    /** The offscreen framebuffer, or null when not rendering (used by the mixin). */
    public static Framebuffer activeFramebuffer() {
        return instance == null ? null : instance.renderFb;
    }

    /**
     * Begin rendering a replay.
     *
     * @param file  the {@code .fpr} replay to render
     * @param width output width in pixels (e.g. 3840)
     * @param height output height in pixels (e.g. 2160)
     * @param fps   output framerate (e.g. 360)
     */
    public static void start(MinecraftClient client, File file, int width, int height, int fps) {
        stop(client);
        try {
            ReplayState state;
            try (ReplayReader reader = ReplayFile.open(file)) {
                state = reader.readAll();
            }
            if (state.cameraFrames.isEmpty()) {
                throw new IOException("Replay contains no camera frames");
            }

            File outDir = new File(file.getParentFile(), file.getName().replace(".fpr", "") + "_out");
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IOException("Could not create output directory: " + outDir);
            }

            Renderer r = new Renderer(state, width, height, fps, outDir);
            r.currentTick = state.startTick();
            r.dt = (double) state.metadata.tickRate / fps;

            ClientPlayerEntity player = client.player;
            if (player != null) {
                r.eyeHeight = player.getStandingEyeHeight();
                r.originalFov = (double) client.options.getFov().getValue();
            }

            r.renderFb = new Framebuffer(width, height, true);
            r.renderFb.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            client.options.hudHidden = true;

            instance = r;
            FpsReplayClient.LOGGER.info(
                    "[FPS Replay] Rendering {} -> {} @ {}x{} @ {}fps ({}s of footage)",
                    file.getName(), outDir.getPath(), width, height, fps,
                    String.format(Locale.ROOT, "%.2f", state.durationSeconds()));
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Failed to start rendering", e);
            stop(client);
        }
    }

    public static void stop(MinecraftClient client) {
        if (instance == null) {
            return;
        }
        Renderer r = instance;
        instance = null;
        client.options.hudHidden = false;
        if (client.player != null) {
            client.options.getFov().setValue(r.originalFov);
        }
        if (r.renderFb != null) {
            r.renderFb.delete();
            r.renderFb = null;
        }
        FpsReplayClient.LOGGER.info("[FPS Replay] Rendering finished: {} frames written to {}",
                r.frameIndex, r.outDir.getPath());
    }

    /** Called at the start of the client render (before the world is drawn). */
    public void preFrame(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            stop(client);
            return;
        }
        CameraFrame cam = interpolate(currentTick);
        // Place the camera entity at the replay eye position (feet = eye - eyeHeight).
        player.refreshPositionAndAngles(
                cam.x, cam.y - eyeHeight, cam.z,
                cam.yaw, cam.pitch);
        client.options.getFov().setValue((double) cam.fov);
    }

    /** Called at the end of the client render (after the frame has been drawn). */
    public void postFrame(MinecraftClient client) {
        if (renderFb == null) {
            stop(client);
            return;
        }
        captureFrame();
        currentTick += dt;
        frameIndex++;
        if (currentTick > state.endTick()) {
            stop(client);
        }
    }

    private void captureFrame() {
        try (var image = ScreenshotRecorder.takeScreenshot(width, height, renderFb, t -> {})) {
            File out = new File(outDir, String.format(Locale.ROOT, "frame_%08d.png", frameIndex));
            image.writeTo(out);
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Frame write failed", e);
            stop(MinecraftClient.getInstance());
        }
    }

    /** Compute the camera pose at fractional tick {@code t}. */
    private CameraFrame interpolate(double t) {
        var frames = state.cameraFrames;
        int i = state.cameraIndexForTick(t);
        if (i < 0) {
            return frames.get(0);
        }
        if (i >= frames.size() - 1) {
            return frames.get(frames.size() - 1);
        }
        CameraFrame a = frames.get(i);
        CameraFrame b = frames.get(i + 1);
        double span = b.tick - a.tick;
        double u = span <= 0 ? 0 : (t - a.tick) / span;
        u = Math.max(0, Math.min(1, u));

        if (spline) {
            CameraFrame p0 = i > 0 ? frames.get(i - 1) : a;
            CameraFrame p3 = i + 2 < frames.size() ? frames.get(i + 2) : b;
            return new CameraFrame(
                    (long) Math.round(t),
                    Interpolation.catmullRom(p0.x, a.x, b.x, p3.x, (float) u),
                    Interpolation.catmullRom(p0.y, a.y, b.y, p3.y, (float) u),
                    Interpolation.catmullRom(p0.z, a.z, b.z, p3.z, (float) u),
                    Interpolation.lerpAngle(a.yaw, b.yaw, (float) u),
                    Interpolation.lerpAngle(a.pitch, b.pitch, (float) u),
                    a.roll,
                    Interpolation.lerp(a.fov, b.fov, (float) u),
                    Interpolation.lerp(a.handSwingProgress, b.handSwingProgress, (float) u));
        }

        return new CameraFrame(
                (long) Math.round(t),
                Interpolation.lerp(a.x, b.x, u),
                Interpolation.lerp(a.y, b.y, u),
                Interpolation.lerp(a.z, b.z, u),
                Interpolation.lerpAngle(a.yaw, b.yaw, (float) u),
                Interpolation.lerpAngle(a.pitch, b.pitch, (float) u),
                a.roll,
                Interpolation.lerp(a.fov, b.fov, (float) u),
                Interpolation.lerp(a.handSwingProgress, b.handSwingProgress, (float) u));
    }
}
