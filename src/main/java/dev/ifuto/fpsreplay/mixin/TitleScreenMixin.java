package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.FlashTextures;
import dev.ifuto.fpsreplay.client.ImageButton;
import dev.ifuto.fpsreplay.client.ReplayListScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "リプレイ一覧 / Replay List" button (with the author-provided icon)
 * to the main title screen.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void fpsreplay$addReplayButton(CallbackInfo ci) {
        ImageButton button = new ImageButton(10, 10, 150, 20,
                FlashTextures.REPLAY_LIST,
                Text.translatable("gui.flash-replay.replay_list"),
                b -> MinecraftClient.getInstance().setScreen(new ReplayListScreen(this)));
        this.addDrawableChild(button);
    }
}
