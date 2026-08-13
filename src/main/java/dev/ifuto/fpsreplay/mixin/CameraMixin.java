package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.CameraCapture;
import dev.ifuto.fpsreplay.client.Recorder;
import dev.ifuto.fpsreplay.client.Renderer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link Camera#update} to (a) capture the exact rendered camera while
 * recording, and (b) force the camera to the interpolated replay pose while
 * rendering. This is what makes the viewpoint reproduction pixel-exact,
 * including view bobbing and roll.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void fpsreplay$afterUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                       boolean inverseView, float tickDelta, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;

        if (Renderer.isRendering()) {
            float[] pose = Renderer.renderCamera();
            if (pose != null) {
                self.setPos(pose[0], pose[1], pose[2]);
                self.setRotation(pose[3], pose[4]);
                float roll = pose[5];
                if (roll != 0.0f) {
                    self.rotate(new Quaternionf().rotateZ((float) Math.toRadians(roll)));
                }
            }
        } else if (Recorder.isRecording()) {
            CameraCapture.capture(self);
        }
    }
}
