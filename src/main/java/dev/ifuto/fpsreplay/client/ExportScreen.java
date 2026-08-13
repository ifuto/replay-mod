package dev.ifuto.fpsreplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.Locale;

/**
 * A GUI for choosing export settings (resolution, framerate, format) and
 * starting an offline render of a replay — no command line needed.
 */
public final class ExportScreen extends Screen {
    private static final int[][] RESOLUTIONS = {
            {1920, 1080},
            {2560, 1440},
            {3840, 2160},
            {7680, 4320},
    };
    private static final int[] FPS_OPTIONS = {30, 60, 120, 240, 360};

    private final Screen parent;
    private final File replayFile;

    private int widthSel = 3840;
    private int heightSel = 2160;
    private int fpsSel = 360;
    private Renderer.Format formatSel = Renderer.Format.MP4;

    private ButtonWidget[] resolutionButtons;
    private ButtonWidget[] fpsButtons;
    private ButtonWidget mp4Button;
    private ButtonWidget pngButton;

    public ExportScreen(Screen parent, File replayFile) {
        super(Text.translatable("gui.flash-replay.export_screen"));
        this.parent = parent;
        this.replayFile = replayFile;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        // Resolution preset buttons.
        resolutionButtons = new ButtonWidget[RESOLUTIONS.length];
        int rx = cx - 150;
        int ry = 44;
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            final int idx = i;
            int[] res = RESOLUTIONS[i];
            resolutionButtons[i] = ButtonWidget.builder(Text.literal(res[0] + "x" + res[1]), b -> {
                        widthSel = res[0];
                        heightSel = res[1];
                        refreshLabels();
                    })
                    .dimensions(rx, ry, 140, 20)
                    .build();
            addDrawableChild(resolutionButtons[i]);
            rx += 150;
            if (rx > cx + 150 - 140) {
                rx = cx - 150;
                ry += 24;
            }
        }

        // FPS preset buttons.
        fpsButtons = new ButtonWidget[FPS_OPTIONS.length];
        int fx = cx - 150;
        int fy = ry + 36;
        for (int i = 0; i < FPS_OPTIONS.length; i++) {
            final int idx = i;
            int f = FPS_OPTIONS[i];
            fpsButtons[i] = ButtonWidget.builder(Text.literal(f + " fps"), b -> {
                        fpsSel = f;
                        refreshLabels();
                    })
                    .dimensions(fx, fy, 84, 20)
                    .build();
            addDrawableChild(fpsButtons[i]);
            fx += 92;
            if (fx > cx + 150 - 84) {
                fx = cx - 150;
                fy += 24;
            }
        }

        // Format buttons.
        int fy2 = fy + 36;
        mp4Button = ButtonWidget.builder(Text.literal("MP4"), b -> {
                    formatSel = Renderer.Format.MP4;
                    refreshLabels();
                })
                .dimensions(cx - 100, fy2, 95, 20)
                .build();
        pngButton = ButtonWidget.builder(Text.literal("PNG"), b -> {
                    formatSel = Renderer.Format.PNG;
                    refreshLabels();
                })
                .dimensions(cx + 5, fy2, 95, 20)
                .build();
        addDrawableChild(mp4Button);
        addDrawableChild(pngButton);

        // Start + cancel.
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.flash-replay.start_export"), b -> startExport())
                .dimensions(cx - 110, height - 52, 100, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.flash-replay.cancel"), b -> close())
                .dimensions(cx + 10, height - 52, 100, 20)
                .build());

        refreshLabels();
    }

    private void refreshLabels() {
        if (resolutionButtons == null) {
            return;
        }
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            int[] res = RESOLUTIONS[i];
            boolean sel = res[0] == widthSel && res[1] == heightSel;
            resolutionButtons[i].setMessage(Text.literal((sel ? "✓ " : "") + res[0] + "x" + res[1]));
        }
        for (int i = 0; i < FPS_OPTIONS.length; i++) {
            boolean sel = FPS_OPTIONS[i] == fpsSel;
            fpsButtons[i].setMessage(Text.literal((sel ? "✓ " : "") + FPS_OPTIONS[i] + " fps"));
        }
        mp4Button.setMessage(Text.literal((formatSel == Renderer.Format.MP4 ? "✓ " : "") + "MP4"));
        pngButton.setMessage(Text.literal((formatSel == Renderer.Format.PNG ? "✓ " : "") + "PNG"));
    }

    private void startExport() {
        MinecraftClient client = MinecraftClient.getInstance();
        Renderer.export(client, replayFile, formatSel, widthSel, heightSel, fpsSel);
        client.setScreen(null);
        if (client.player != null) {
            client.inGameHud.getChatHud().addMessage(Text.translatable("gui.flash-replay.exporting"));
        }
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
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(replayFile.getName()), width / 2, 30, 0xAAAAAA);

        int cx = width / 2;
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.flash-replay.resolution"), cx - 150, 34, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.flash-replay.framerate"), cx - 150, 44 + 48, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.flash-replay.format"), cx - 150, 44 + 48 + 36 + 6, 0xFFFFFF);

        context.drawCenteredTextWithShadow(textRenderer,
                String.format(Locale.ROOT, "%dx%d @ %d fps (%s)", widthSel, heightSel, fpsSel,
                        formatSel == Renderer.Format.MP4 ? "MP4" : "PNG"),
                cx, height - 62, 0xFFD700);
        super.render(context, mouseX, mouseY, delta);
    }
}
