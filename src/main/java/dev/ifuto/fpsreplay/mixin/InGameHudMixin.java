package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the preview timeline / controls overlay at the bottom of the screen
 * while a replay is being previewed.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void fpsreplay$previewOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Renderer.isPreviewing()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String status = Renderer.previewStatus();
        int y = client.getWindow().getScaledHeight() - 14;
        context.drawTextWithShadow(client.textRenderer, "REPLAY  " + status, 4, y, 0xFFFFFF);
        context.drawTextWithShadow(client.textRenderer, "Space: play/pause   ←/→: seek   Esc: exit", 4, y - 12, 0xAAAAAA);
    }
}
