package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Recorder;
import dev.ifuto.fpsreplay.client.ReplayListScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Adds a "録画開始 / 録画停止" toggle button and a "リプレイ一覧" button to
 * the pause (game menu) screen.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    private ButtonWidget recordButton;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void fpsreplay$addRecordButton(CallbackInfo ci) {
        recordButton = ButtonWidget.builder(recordLabel(), b -> toggle())
                .dimensions(10, 10, 130, 20)
                .build();
        this.addDrawableChild(recordButton);

        ButtonWidget listButton = ButtonWidget.builder(
                        Text.translatable("gui.flash-replay.replay_list"),
                        b -> MinecraftClient.getInstance().setScreen(new ReplayListScreen(this)))
                .dimensions(10, 34, 130, 20)
                .build();
        this.addDrawableChild(listButton);
    }

    private static Text recordLabel() {
        return Text.translatable(Recorder.isRecording()
                ? "gui.flash-replay.record_stop"
                : "gui.flash-replay.record_start");
    }

    private void toggle() {
        if (Recorder.isRecording()) {
            Recorder.stop();
        } else {
            String name = "replay-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Recorder.start(MinecraftClient.getInstance(), name);
        }
        if (recordButton != null) {
            recordButton.setMessage(recordLabel());
        }
    }
}
