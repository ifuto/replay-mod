package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.CameraFrame;
import dev.ifuto.fpsreplay.replay.HudState;
import dev.ifuto.fpsreplay.replay.Interpolation;
import dev.ifuto.fpsreplay.replay.ReplayFile;
import dev.ifuto.fpsreplay.replay.ReplayReader;
import dev.ifuto.fpsreplay.replay.ReplayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Replay renderer with two modes:
 * <ul>
 *   <li><b>Export</b> — re-render the replay into an offscreen framebuffer at
 *       any resolution (4K/8K) and framerate (360fps+), writing PNG frames or
 *       a single H.264 MP4 (via JCodec).</li>
 *   <li><b>Preview</b> — play the replay live in the main window with
 *       timeline controls.</li>
 * </ul>
 *
 * <p>The camera is reproduced exactly: the interpolated pose is injected into
 * the real {@code Camera} (see {@code CameraMixin}) so view bobbing, roll and
 * FOV are preserved, and the recorded HUD state (vitals/effects/scoreboard) is
 * applied so the HUD renders as it did during recording.</p>
 */
public final class Renderer {
    public enum Mode {EXPORT_PNG, EXPORT_MP4, PREVIEW}

    public enum Format {PNG, MP4}

    private static Renderer instance;

    private final ReplayState state;
    private final Mode mode;
    private final Format format;
    private final int width;
    private final int height;
    private final int fps;
    private final boolean spline;
    private final File outDir;

    private Framebuffer renderFb;
    private Mp4Exporter mp4;
    private double currentTick;
    private double dt;
    private long frameIndex;
    private final float[] renderCamera = new float[7];
    private boolean playing = true;
    private long lastNanos;
    private HudState lastAppliedHud;

    private Renderer(ReplayState state, Mode mode, Format format, int width, int height, int fps, File outDir) {
        this.state = state;
        this.mode = mode;
        this.format = format;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.outDir = outDir;
        this.spline = "spline".equalsIgnoreCase(ReplayConfig.interpolationMode);
    }

    public static boolean isRendering() {
        return instance != null;
    }

    public static boolean isPreviewing() {
        return instance != null && instance.mode == Mode.PREVIEW;
    }

    public static Renderer active() {
        return instance;
    }

    /** The offscreen framebuffer, or null when previewing (used by the mixin). */
    public static Framebuffer activeFramebuffer() {
        return (instance != null && instance.mode != Mode.PREVIEW) ? instance.renderFb : null;
    }

    /** The interpolated camera pose {x,y,z,yaw,pitch,roll,fov} for the mixins. */
    public static float[] renderCamera() {
        return instance == null ? null : instance.renderCamera;
    }

    public static String previewStatus() {
        if (instance == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.1fs / %.1fs  %s", instance.currentTimeSeconds(),
                instance.state.durationSeconds(), instance.playing ? "▶" : "❚❚");
    }

    /** Start an export render. */
    public static void export(MinecraftClient client, File file, Format format, int width, int height, int fps) {
        start(client, file, format == Format.PNG ? Mode.EXPORT_PNG : Mode.EXPORT_MP4, format, width, height, fps);
    }

    /** Start a live preview. */
    public static void preview(MinecraftClient client, File file) {
        start(client, file, Mode.PREVIEW, Format.PNG, 0, 0, 0);
    }

    private static void start(MinecraftClient client, File file, Mode mode, Format format,
                              int width, int height, int fps) {
        stop(client);
        try {
            ReplayState state;
            try (ReplayReader reader = ReplayFile.open(file)) {
                state = reader.readAll();
            }
            if (state.cameraFrames.isEmpty()) {
                throw new IOException("Replay contains no camera frames");
            }

            File outDir = null;
            if (mode != Mode.PREVIEW) {
                outDir = new File(file.getParentFile(), file.getName().replace(".fpr", "") + "_out");
                if (!outDir.exists() && !outDir.mkdirs()) {
                    throw new IOException("Could not create output directory: " + outDir);
                }
            }

            Renderer r = new Renderer(state, mode, format, width, height, fps, outDir);
            r.currentTick = state.startTick();
            r.dt = (double) state.metadata.tickRate / (mode == Mode.PREVIEW ? 60 : fps);

            if (mode != Mode.PREVIEW) {
                r.renderFb = new Framebuffer(width, height, true);
                r.renderFb.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                if (format == Format.MP4) {
                    r.mp4 = new Mp4Exporter(new File(outDir, "output.mp4"), fps);
                }
            }
            r.lastNanos = System.nanoTime();

            instance = r;
            FpsReplayClient.LOGGER.info("[FPS Replay] {} {}{}", mode == Mode.PREVIEW ? "Previewing" : "Rendering",
                    file.getName(),
                    mode == Mode.PREVIEW ? "" : String.format(Locale.ROOT, " @ %dx%d @ %dfps (%s)",
                            width, height, fps, format));
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
        if (r.mp4 != null) {
            try {
                r.mp4.close();
            } catch (IOException e) {
                FpsReplayClient.LOGGER.error("[FPS Replay] Failed to finalize MP4", e);
            }
            r.mp4 = null;
        }
        if (r.renderFb != null) {
            r.renderFb.delete();
            r.renderFb = null;
        }
        FpsReplayClient.LOGGER.info("[FPS Replay] {} finished ({} frames)",
                r.mode == Mode.PREVIEW ? "Preview" : "Rendering", r.frameIndex);
    }

    /** Called at the start of the client render, before the world is drawn. */
    public void preFrame(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            stop(client);
            return;
        }
        CameraFrame cam = interpolate(currentTick);

        // Keep the player entity at the replay position so world rendering,
        // chunk loading and entity culling all follow the camera.
        player.refreshPositionAndAngles(cam.x, cam.y - 1.62f, cam.z, cam.yaw, cam.pitch);

        // Publish the exact pose for CameraMixin / GameRendererMixin to force.
        renderCamera[0] = (float) cam.x;
        renderCamera[1] = (float) cam.y;
        renderCamera[2] = (float) cam.z;
        renderCamera[3] = cam.yaw;
        renderCamera[4] = cam.pitch;
        renderCamera[5] = cam.roll;
        renderCamera[6] = cam.fov;

        // Reproduce the HUD (hearts / hunger / effects / scoreboard), only
        // when the relevant keyframe changes (rebuilding is not free).
        HudState hud = state.hudStateAt((long) Math.floor(currentTick));
        if (hud != lastAppliedHud) {
            HudApplier.apply(client, hud);
            lastAppliedHud = hud;
        }
    }

    /** Called at the end of the client render, after the frame is drawn. */
    public void postFrame(MinecraftClient client) {
        if (mode == Mode.PREVIEW) {
            advancePreview();
            return;
        }
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

    /** Keyboard-driven preview controls (Space=play/pause, arrows=seek). */
    public void handlePreviewKey(int keyCode) {
        if (mode != Mode.PREVIEW) {
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            playing = !playing;
        } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            currentTick = Math.max(state.startTick(), currentTick - 20);
        } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            currentTick = Math.min(state.endTick(), currentTick + 20);
        }
    }

    private void advancePreview() {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (playing) {
            currentTick += elapsed * state.metadata.tickRate;
        }
        if (currentTick >= state.endTick()) {
            currentTick = state.startTick(); // loop
        }
    }

    private void captureFrame() {
        try {
            NativeImage img = grabFrame();
            if (format == Format.MP4) {
                mp4.addFrame(toBufferedImage(img));
                img.close();
            } else {
                File out = new File(outDir, String.format(Locale.ROOT, "frame_%08d.png", frameIndex));
                img.writeTo(out);
                img.close();
            }
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Frame capture failed", e);
            stop(MinecraftClient.getInstance());
        }
    }

    private NativeImage grabFrame() {
        NativeImage img = new NativeImage(width, height, false);
        renderFb.beginRead();
        img.loadFromTextureImage(0, false);
        renderFb.endRead();
        img.mirrorVertically();
        return img;
    }

    private static BufferedImage toBufferedImage(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bi.setRGB(x, y, img.getPixelColor(x, y));
            }
        }
        return bi;
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
                    Interpolation.catmullRom(p0.x, a.x, b.x, p3.x, u),
                    Interpolation.catmullRom(p0.y, a.y, b.y, p3.y, u),
                    Interpolation.catmullRom(p0.z, a.z, b.z, p3.z, u),
                    Interpolation.lerpAngle(a.yaw, b.yaw, (float) u),
                    Interpolation.lerpAngle(a.pitch, b.pitch, (float) u),
                    Interpolation.lerpAngle(a.roll, b.roll, (float) u),
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
                Interpolation.lerpAngle(a.roll, b.roll, (float) u),
                Interpolation.lerp(a.fov, b.fov, (float) u),
                Interpolation.lerp(a.handSwingProgress, b.handSwingProgress, (float) u));
    }

    private double currentTimeSeconds() {
        return (currentTick - state.startTick()) / (double) state.metadata.tickRate;
    }
}
