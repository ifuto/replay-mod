package dev.ifuto.fpsreplay.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Wires up config, commands, and the per-tick recorder sampling.
 */
public final class FlashReplayClient implements ClientModInitializer {
    public static final String MOD_ID = "flash-replay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ReplayConfig.load();

        ReplayCommands.register();

        // Register button textures after the client (and its resource manager)
        // are fully started — doing it in onInitializeClient NPEs because the
        // resource manager isn't ready yet.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                FlashTextures.registerAll(client);
            } catch (Throwable t) {
                LOGGER.warn("[Flash Replay] texture registration failed", t);
            }
        });

        // Sample the camera once per client tick while recording, and poll
        // preview keys (GLFW-based, no mixin) while previewing.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Recorder.isRecording()) {
                Recorder.tick();
            }
            if (Renderer.isPreviewing()) {
                Renderer.pollPreviewKeys(client);
            }
        });

        // Stop cleanly if the player leaves the world/server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Recorder.stop());

        // Finalize everything on shutdown.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Recorder.stop();
            Renderer.stop(client);
            ReplayConfig.save();
        });
    }
}
