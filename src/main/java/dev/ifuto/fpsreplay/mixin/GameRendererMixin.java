package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions the camera at the interpolated replay pose before each frame is
 * drawn, so the normal render pipeline reproduces the recorded viewpoint.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsreplay$preRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (Renderer.isRendering()) {
            Renderer.active().preFrame(MinecraftClient.getInstance());
        }
    }
}
