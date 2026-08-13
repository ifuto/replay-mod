package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.BlockChange;
import dev.ifuto.fpsreplay.replay.CameraFrame;
import dev.ifuto.fpsreplay.replay.EntityFrame;
import dev.ifuto.fpsreplay.replay.ReplayFile;
import dev.ifuto.fpsreplay.replay.ReplayMetadata;
import dev.ifuto.fpsreplay.replay.ReplayWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The recorder — the "死ぬほど軽量化" core.
 *
 * <p>Instead of capturing pixels, it writes a handful of quantized bytes once
 * per <b>tick</b> (20/s), sampled from the real render camera (so the exact
 * viewpoint, including bobbing/roll/FOV, is preserved). The hot path is
 * allocation-free: no per-tick objects, no boxing, minimal bytes via zigzag
 * varint deltas. Entity + HUD snapshots only occur at keyframes.</p>
 */
public final class Recorder implements AutoCloseable {
    private static Recorder instance;

    private final ReplayWriter writer;
    private final File file;
    private long lastKeyTick = Long.MIN_VALUE;
    private final CameraFrame lastKey = new CameraFrame(0, 0, 0, 0, 0, 0, 0, 0, 0);
    private final List<EntityFrame> entityScratch = new ArrayList<>(128);

    private Recorder(ReplayWriter writer, File file) {
        this.writer = writer;
        this.file = file;
    }

    public static boolean isRecording() {
        return instance != null;
    }

    public static File currentFile() {
        return instance == null ? null : instance.file;
    }

    public static File start(MinecraftClient client, String name) {
        stop();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return null;
        }
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "replays");
        File file = new File(dir, name + ".fpr");

        ReplayMetadata meta = new ReplayMetadata(
                SharedConstants.getGameVersion().getName(),
                name,
                world.getSeed(),
                20,
                System.currentTimeMillis(),
                ReplayConfig.keyframeInterval);

        try {
            ReplayWriter writer = ReplayFile.create(file, meta, ReplayConfig.compressionLevel);
            instance = new Recorder(writer, file);
            CameraCapture.reset();
            FpsReplayClient.LOGGER.info("[FPS Replay] Recording started -> {}", file);
            return file;
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Failed to start recording", e);
            return null;
        }
    }

    public static void tick() {
        if (instance != null) {
            instance.sample();
        }
    }

    public static void onBlockChange(BlockPos pos, BlockState state) {
        if (instance == null || !ReplayConfig.recordBlockChanges) {
            return;
        }
        try {
            int stateId = Block.STATE_IDS.getRawId(state);
            instance.writer.writeBlockChange(new BlockChange(pos.getX(), pos.getY(), pos.getZ(), stateId));
        } catch (IOException e) {
            FpsReplayClient.LOGGER.warn("[FPS Replay] Failed to record block change", e);
        }
    }

    public static void stop() {
        if (instance == null) {
            return;
        }
        Recorder r = instance;
        try {
            r.close();
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Failed to finalize recording", e);
        }
        FpsReplayClient.LOGGER.info("[FPS Replay] Recording saved -> {}", r.file);
        instance = null;
    }

    private void sample() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) {
            return;
        }

        long tick = world.getTime();

        // Exact camera: prefer the captured render camera (includes bob/roll);
        // fall back to the raw eye position on the very first sample.
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        float roll;
        float fov;
        float handSwing;
        if (CameraCapture.valid) {
            x = CameraCapture.x;
            y = CameraCapture.y;
            z = CameraCapture.z;
            yaw = CameraCapture.yaw;
            pitch = CameraCapture.pitch;
            roll = CameraCapture.roll;
            fov = CameraCapture.fov;
        } else {
            var eye = player.getEyePos();
            x = eye.x;
            y = eye.y;
            z = eye.z;
            yaw = player.getYaw();
            pitch = player.getPitch();
            roll = 0.0f;
            fov = (float) (double) client.options.getFov().getValue();
        }
        handSwing = player.handSwingProgress;

        try {
            if (lastKeyTick == Long.MIN_VALUE || tick - lastKeyTick >= ReplayConfig.keyframeInterval) {
                // Keyframe: update the reused anchor + capture HUD/entities.
                lastKey.tick = tick;
                lastKey.x = x;
                lastKey.y = y;
                lastKey.z = z;
                lastKey.yaw = yaw;
                lastKey.pitch = pitch;
                lastKey.roll = roll;
                lastKey.fov = fov;
                lastKey.handSwingProgress = handSwing;
                sampleEntities(world, player);
                writer.writeKeyframe(tick, lastKey, HudCapture.capture(client), entityScratch);
                lastKeyTick = tick;
            } else {
                writer.writeTick(
                        tick,
                        x - lastKey.x, y - lastKey.y, z - lastKey.z,
                        yaw - lastKey.yaw, pitch - lastKey.pitch, roll - lastKey.roll,
                        fov - lastKey.fov, handSwing);
            }
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Recording write failed", e);
            stop();
        }
    }

    private void sampleEntities(ClientWorld world, ClientPlayerEntity self) {
        entityScratch.clear();
        double rangeSq = (double) ReplayConfig.entityRange * ReplayConfig.entityRange;
        for (Entity entity : world.getEntities()) {
            if (entity == self) {
                continue;
            }
            if (entity.squaredDistanceTo(self) > rangeSq) {
                continue;
            }
            int typeId = Registries.ENTITY_TYPE.getRawId(entity.getType());
            float headYaw = entity instanceof LivingEntity living ? living.getHeadYaw() : entity.getYaw();
            entityScratch.add(new EntityFrame(
                    entity.getId(), typeId,
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYaw(), entity.getPitch(), headYaw));
        }
    }

    @Override
    public void close() throws IOException {
        writer.writeEnd();
        writer.close();
    }
}
