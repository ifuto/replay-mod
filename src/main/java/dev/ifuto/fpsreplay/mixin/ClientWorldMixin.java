package dev.ifuto.fpsreplay.mixin;

import dev.ifuto.fpsreplay.client.Recorder;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures block-state mutations while recording, so the replay can record
 * the world-changing "packets" (pistons, explosions, redstone, etc.) in
 * addition to the camera track.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void fpsreplay$onBlockChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            Recorder.onBlockChange(pos, state);
        }
    }
}
