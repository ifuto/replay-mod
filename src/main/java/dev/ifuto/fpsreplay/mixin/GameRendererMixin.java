package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.CameraCapture;
import dev.ifuto.fpsreplay.client.Recorder;
import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Positions the camera at the interpolated replay pose before each frame is
 * drawn, captures the exact FOV while recording, and forces the recorded FOV
 * while rendering.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsreplay$preRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (Renderer.isRendering()) {
            Renderer.active().preFrame(MinecraftClient.getInstance());
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"))
    private void fpsreplay$onFov(Camera camera, float tickDelta, boolean changingFov,
                                 CallbackInfoReturnable<Double> cir) {
        if (Renderer.isRendering()) {
            float[] pose = Renderer.renderCamera();
            if (pose != null) {
                cir.setReturnValue((double) pose[6]);
            }
        } else if (Recorder.isRecording()) {
            CameraCapture.captureFov(cir.getReturnValue());
        }
    }
}
