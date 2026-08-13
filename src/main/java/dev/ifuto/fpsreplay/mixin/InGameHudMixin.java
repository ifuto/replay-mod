package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Renderer;
import dev.ifuto.fpsreplay.client.TabListOverlay;
import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the recorded tab (player list) overlay and the preview timeline
 * controls while a replay is being previewed or exported.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void fpsreplay$overlays(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Recorded tab player list — only when the player had it open.
        if (Renderer.isRendering()) {
            HudState hud = Renderer.currentHud();
            if (hud != null && hud.playerListVisible) {
                TabListOverlay.render(context, client.getWindow().getScaledWidth(), hud);
            }
        }

        // Preview timeline / controls.
        if (Renderer.isPreviewing()) {
            String status = Renderer.previewStatus();
            int y = client.getWindow().getScaledHeight() - 14;
            context.drawTextWithShadow(client.textRenderer, "REPLAY  " + status, 4, y, 0xFFFFFF);
            context.drawTextWithShadow(client.textRenderer, "Space: play/pause   \u2190/\u2192: seek   Esc: exit", 4, y - 12, 0xAAAAAA);
        }
    }
}
