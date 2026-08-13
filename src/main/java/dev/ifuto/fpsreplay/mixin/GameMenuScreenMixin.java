package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.FlashTextures;
import dev.ifuto.fpsreplay.client.ImageButton;
import dev.ifuto.fpsreplay.client.Recorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Adds a "録画開始 / 録画停止" toggle button (with the author-provided icon)
 * to the pause (game menu) screen.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void fpsreplay$addRecordButton(CallbackInfo ci) {
        ImageButton button = new ImageButton(10, 10, 150, 20,
                recordTexture(), recordLabel(), this::toggle);
        this.addDrawableChild(button);
    }

    private static Text recordLabel() {
        return Text.translatable(Recorder.isRecording()
                ? "gui.flash-replay.record_stop"
                : "gui.flash-replay.record_start");
    }

    private static Identifier recordTexture() {
        return Recorder.isRecording() ? FlashTextures.RECORD_STOP : FlashTextures.RECORD_START;
    }

    private void toggle(ButtonWidget button) {
        if (Recorder.isRecording()) {
            Recorder.stop();
        } else {
            String name = "replay-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Recorder.start(MinecraftClient.getInstance(), name);
        }
        if (button instanceof ImageButton ib) {
            ib.setMessage(recordLabel());
            ib.setTexture(recordTexture());
        }
    }
}
