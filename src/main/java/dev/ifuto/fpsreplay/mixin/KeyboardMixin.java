package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes keyboard input to the preview player while previewing, and swallows
 * normal gameplay keys so the replay isn't disturbed. Escape exits preview.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void fpsreplay$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!Renderer.isPreviewing()) {
            return;
        }
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                Renderer.stop(MinecraftClient.getInstance());
            } else {
                Renderer.active().handlePreviewKey(key);
            }
        }
        ci.cancel();
    }
}
