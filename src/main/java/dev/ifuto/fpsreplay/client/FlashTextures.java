package dev.ifuto.fpsreplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Registers the mod's GUI button textures (derived from the author-provided
 * images in {@code images/}) so they can be drawn with {@code DrawContext}.
 */
public final class FlashTextures {
    public static final Identifier RECORD_START = Identifier.of("flash-replay", "textures/gui/record_start.png");
    public static final Identifier RECORD_STOP = Identifier.of("flash-replay", "textures/gui/record_stop.png");
    public static final Identifier REPLAY_LIST = Identifier.of("flash-replay", "textures/gui/replay_list.png");

    private FlashTextures() {
    }

    public static void registerAll(MinecraftClient client) {
        register(client, RECORD_START);
        register(client, RECORD_STOP);
        register(client, REPLAY_LIST);
    }

    private static void register(MinecraftClient client, Identifier id) {
        try {
            var resource = client.getResourceManager().getResource(id).orElseThrow();
            NativeImage image;
            try (InputStream in = resource.getInputStream()) {
                image = NativeImage.read(in);
            }
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        } catch (Exception e) {
            FlashReplayClient.LOGGER.warn("[Flash Replay] Could not register texture {}", id, e);
        }
    }
}
