package dev.ifuto.fpsreplay.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent mod settings, stored as a simple {@code .properties} file in the
 * config directory (no external config library needed).
 */
public final class ReplayConfig {
    // --- Recording (lightweight) ---
    /** gzip level 0-9; higher = smaller files, slightly more CPU. */
    public static int compressionLevel = 6;
    /** Ticks between keyframes. Lower = finer entity snapshots, larger files. */
    public static int keyframeInterval = 20;
    /** Radius (blocks) around the player in which entities are recorded. */
    public static int entityRange = 64;
    /** Whether to also record block changes ("packets") for world fidelity. */
    public static boolean recordBlockChanges = true;

    // --- Rendering (upscale at output time) ---
    /** Default output width when not specified on the command line (4K). */
    public static int renderWidth = 3840;
    /** Default output height (4K). */
    public static int renderHeight = 2160;
    /** Default output framerate when not specified (360fps). */
    public static int renderFps = 360;
    /** "linear" or "spline" interpolation for sub-tick frames. */
    public static String interpolationMode = "linear";

    private ReplayConfig() {
    }

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("fps-replay.properties");
    }

    public static void load() {
        Path path = configFile();
        if (!Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            FpsReplayClient.LOGGER.warn("Could not load config, using defaults", e);
            return;
        }
        compressionLevel = intProp(props, "compressionLevel", compressionLevel);
        keyframeInterval = intProp(props, "keyframeInterval", keyframeInterval);
        entityRange = intProp(props, "entityRange", entityRange);
        recordBlockChanges = boolProp(props, "recordBlockChanges", recordBlockChanges);
        renderWidth = intProp(props, "renderWidth", renderWidth);
        renderHeight = intProp(props, "renderHeight", renderHeight);
        renderFps = intProp(props, "renderFps", renderFps);
        interpolationMode = props.getProperty("interpolationMode", interpolationMode);
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("compressionLevel", String.valueOf(compressionLevel));
        props.setProperty("keyframeInterval", String.valueOf(keyframeInterval));
        props.setProperty("entityRange", String.valueOf(entityRange));
        props.setProperty("recordBlockChanges", String.valueOf(recordBlockChanges));
        props.setProperty("renderWidth", String.valueOf(renderWidth));
        props.setProperty("renderHeight", String.valueOf(renderHeight));
        props.setProperty("renderFps", String.valueOf(renderFps));
        props.setProperty("interpolationMode", interpolationMode);
        try {
            Path path = configFile();
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "FPS Replay settings");
            }
        } catch (IOException e) {
            FpsReplayClient.LOGGER.warn("Could not save config", e);
        }
    }

    private static int intProp(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolProp(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        return Boolean.parseBoolean(v);
    }
}
