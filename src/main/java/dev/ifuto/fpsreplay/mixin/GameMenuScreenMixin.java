package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Recorder;
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
 * Adds a "録画開始 / 録画停止" toggle button to the pause (game menu) screen.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private void fpsreplay$addRecordButton(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        ButtonWidget button = ButtonWidget.builder(
                        Text.literal(Recorder.isRecording() ? "■ 録画停止" : "● 録画開始"),
                        b -> toggle(b))
                .dimensions(10, 10, 130, 20)
                .build();
        screen.addDrawableChild(button);
    }

    private void toggle(ButtonWidget button) {
        if (Recorder.isRecording()) {
            Recorder.stop();
        } else {
            String name = "replay-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Recorder.start(MinecraftClient.getInstance(), name);
        }
        button.setMessage(Text.literal(Recorder.isRecording() ? "■ 録画停止" : "● 録画開始"));
    }
}
