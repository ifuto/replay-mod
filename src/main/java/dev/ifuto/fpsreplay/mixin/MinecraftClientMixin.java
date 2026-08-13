package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the client render loop to support offline replay rendering.
 *
 * <ul>
 *   <li>{@code getFramebuffer}: swap the window framebuffer for the renderer's
 *       offscreen (e.g. 4K) framebuffer so the whole pipeline draws large.</li>
 *   <li>{@code render} return: capture the finished frame and advance.</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void fpsreplay$getFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer fb = Renderer.activeFramebuffer();
        if (fb != null) {
            cir.setReturnValue(fb);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void fpsreplay$postRender(CallbackInfo ci) {
        if (Renderer.isRendering()) {
            Renderer.active().postFrame((MinecraftClient) (Object) this);
        }
    }
}
