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
 * drawn while a replay is being rendered or previewed.
 *
 * <p>FOV is handled via the client options value (see {@code Renderer}) rather
 * than the private {@code GameRenderer#getFov}, which changed visibility and
 * return type across versions.</p>
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
