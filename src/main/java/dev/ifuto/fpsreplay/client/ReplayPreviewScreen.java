package dev.ifuto.fpsreplay.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.File;

/**
 * A screen that plays a replay <b>inside a GUI</b>: the game is paused
 * (frozen — no world ticking, no player movement) while the recorded camera
 * renders the world behind this screen. Controls are shown as an overlay.
 */
public final class ReplayPreviewScreen extends Screen {
    private final Screen parent;
    private final File replayFile;

    public ReplayPreviewScreen(Screen parent, File replayFile) {
        super(Text.translatable("gui.flash-replay.preview"));
        this.parent = parent;
        this.replayFile = replayFile;
    }

    @Override
    protected void init() {
        // Start driving the replay camera (world renders behind this screen).
        Renderer.preview(client, replayFile);
    }

    @Override
    public boolean shouldPause() {
        // Freeze the world while previewing (singleplayer).
        return true;
    }

    @Override
    public void close() {
        Renderer.stop(client);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Translucent control bar at the bottom; the replay world renders behind.
        context.fill(0, height - 32, width, height, 0x80000000);
        String status = Renderer.previewStatus();
        context.drawTextWithShadow(textRenderer, Text.literal(status == null ? "" : status), 8, height - 24, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer,
                Text.translatable("gui.flash-replay.preview_hint"), 8, height - 13, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
