package dev.ifuto.fpsreplay.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A screen listing saved replays. Each row has a "プレビュー" (preview) and
 * "出力" (export) button — so playback and rendering can be driven entirely
 * from the GUI, no commands required.
 *
 * <p>Preview/export render into the live client world, so they require being
 * inside a world. When opened from the title screen (no world loaded), the
 * row buttons are disabled and a hint is shown.</p>
 */
public final class ReplayListScreen extends Screen {
    private final Screen parent;
    private final boolean inWorld;

    public ReplayListScreen(Screen parent) {
        super(Text.translatable("gui.flash-replay.replay_list"));
        this.parent = parent;
        this.inWorld = MinecraftClient.getInstance().world != null;
    }

    @Override
    protected void init() {
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "replays");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".fpr"));
        List<File> sorted = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                sorted.add(f);
            }
        }
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());

        int y = 40;
        int rowWidth = 300;
        int left = width / 2 - rowWidth / 2;
        for (File f : sorted) {
            addDrawableChild(ButtonWidget.builder(Text.literal(f.getName()), b -> preview(f))
                    .dimensions(left, y, rowWidth - 118, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.flash-replay.preview"), b -> preview(f))
                    .dimensions(left + rowWidth - 114, y, 54, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.flash-replay.export"), b -> export(f))
                    .dimensions(left + rowWidth - 56, y, 56, 20)
                    .build());
            y += 24;
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.flash-replay.back"), b -> close())
                .dimensions(width / 2 - 50, height - 30, 100, 20)
                .build());
    }

    private void preview(File f) {
        if (!inWorld) {
            return;
        }
        // Preview inside a dedicated GUI screen (game is paused/frozen).
        MinecraftClient.getInstance().setScreen(new ReplayPreviewScreen(this, f));
    }

    private void export(File f) {
        if (!inWorld) {
            return;
        }
        MinecraftClient.getInstance().setScreen(new ExportScreen(this, f));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.6+: background is rendered by super.render(); do not call
        // renderBackground() here (would double-blur and crash).
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        if (!inWorld) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("gui.flash-replay.need_world"), width / 2, 28, 0xFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
