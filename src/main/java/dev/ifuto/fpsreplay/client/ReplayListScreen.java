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
 */
public final class ReplayListScreen extends Screen {
    private final Screen parent;

    public ReplayListScreen(Screen parent) {
        super(Text.translatable("gui.flash-replay.replay_list"));
        this.parent = parent;
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

        int y = 34;
        int rowWidth = 300;
        int left = width / 2 - rowWidth / 2;
        for (File f : sorted) {
            String label = f.getName();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> preview(f))
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
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(null);
        Renderer.preview(client, f);
        if (client.player != null) {
            client.inGameHud.getChatHud().addMessage(Text.translatable("gui.flash-replay.preview_hint"));
        }
    }

    private void export(File f) {
        MinecraftClient.getInstance().setScreen(new ExportScreen(this, f));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
