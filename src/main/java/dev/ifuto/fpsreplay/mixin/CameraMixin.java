package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.CameraCapture;
import dev.ifuto.fpsreplay.client.Recorder;
import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link Camera#update} to (a) capture the exact rendered camera while
 * recording, and (b) force the camera to the interpolated replay pose while
 * rendering. Roll is not applied (it caused the camera to flip upside-down);
 * position + yaw/pitch reproduce the viewpoint exactly.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void fpsreplay$afterUpdate(World area, Entity focusedEntity, boolean thirdPerson,
                                       boolean inverseView, float tickDelta, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;

        if (Renderer.isRendering()) {
            float[] pose = Renderer.renderCamera();
            if (pose != null) {
                invokeSetPos(pose[0], pose[1], pose[2]);
                invokeSetRotation(pose[3], pose[4]);
            }
        } else if (Recorder.isRecording()) {
            CameraCapture.capture(self);
        }
    }

    @org.spongepowered.asm.mixin.gen.Invoker("setPos")
    abstract void invokeSetPos(double x, double y, double z);

    @org.spongepowered.asm.mixin.gen.Invoker("setRotation")
    abstract void invokeSetRotation(float yaw, float pitch);
}
