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
    private CameraFrame lastKey;
    private final List<EntityFrame> entityScratch = new ArrayList<>(128);
    private final TerrainRecorder terrainRecorder = new TerrainRecorder();

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
                SharedConstants.getGameVersion().id(),
                name,
                resolveSeed(client),
                20,
                System.currentTimeMillis(),
                ReplayConfig.keyframeInterval);

        try {
            ReplayWriter writer = ReplayFile.create(file, meta, ReplayConfig.compressionLevel);
            instance = new Recorder(writer, file);
            CameraCapture.reset();
            instance.terrainRecorder.reset();
            // Immediately snapshot the terrain around the player so the replay
            // is self-contained even if the player never moves.
            instance.terrainRecorder.tick(client, writer, ReplayConfig.terrainChunkRadius);
            FlashReplayClient.LOGGER.info("[Flash Replay] Recording started -> {}", file);
            return file;
        } catch (IOException e) {
            FlashReplayClient.LOGGER.error("[Flash Replay] Failed to start recording", e);
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
            MinecraftClient client = MinecraftClient.getInstance();
            long tick = client.world != null ? client.world.getTime() : 0L;
            int stateId = Block.STATE_IDS.getRawId(state);
            instance.writer.writeBlockChange(new BlockChange(tick, pos.getX(), pos.getY(), pos.getZ(), stateId));
        } catch (IOException e) {
            FlashReplayClient.LOGGER.warn("[Flash Replay] Failed to record block change", e);
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
            FlashReplayClient.LOGGER.error("[Flash Replay] Failed to finalize recording", e);
        }
        FlashReplayClient.LOGGER.info("[Flash Replay] Recording saved -> {}", r.file);
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
        // FOV is read from the client option (the in-game dynamic FOV is not
        // captured — it is not exposed via a stable public API).
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        float roll;
        float fov = client.options.getFov().getValue().floatValue();
        float handSwing;
        if (CameraCapture.valid) {
            x = CameraCapture.x;
            y = CameraCapture.y;
            z = CameraCapture.z;
            yaw = CameraCapture.yaw;
            pitch = CameraCapture.pitch;
            roll = CameraCapture.roll;
        } else {
            var eye = player.getEyePos();
            x = eye.x;
            y = eye.y;
            z = eye.z;
            yaw = player.getYaw();
            pitch = player.getPitch();
            roll = 0.0f;
        }
        handSwing = player.handSwingProgress;

        try {
            if (lastKeyTick == Long.MIN_VALUE || tick - lastKeyTick >= ReplayConfig.keyframeInterval) {
                // Keyframe: capture HUD, anchor the delta encoding, and
                // snapshot any newly-seen terrain columns. Written first so the
                // tick delta is anchored to an absolute tick on the first record.
                lastKey = new CameraFrame(tick, x, y, z, yaw, pitch, roll, fov, handSwing);
                writer.writeKeyframe(tick, lastKey, HudCapture.capture(client));
                terrainRecorder.tick(client, writer, ReplayConfig.terrainChunkRadius);
                lastKeyTick = tick;
            } else {
                writer.writeTick(
                        tick,
                        x - lastKey.x, y - lastKey.y, z - lastKey.z,
                        yaw - lastKey.yaw, pitch - lastKey.pitch, roll - lastKey.roll,
                        fov - lastKey.fov, handSwing);
            }

            // Entities are captured at the configured interval (default every
            // tick) so render-time interpolation can reproduce motion finer
            // than the server even sends over packets.
            int interval = Math.max(1, ReplayConfig.entityRecordInterval);
            if (tick % interval == 0) {
                sampleEntities(world, player);
                writer.writeEntities(tick, entityScratch);
            }
        } catch (IOException e) {
            FlashReplayClient.LOGGER.error("[Flash Replay] Recording write failed", e);
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
            float headYaw = entity.getYaw();
            float health = -1.0f;
            float maxHealth = -1.0f;
            if (entity instanceof LivingEntity living) {
                headYaw = living.getHeadYaw();
                health = living.getHealth();
                maxHealth = living.getMaxHealth();
            }
            int flags = 0;
            if (entity.isGlowing()) {
                flags |= EntityFrame.FLAG_GLOWING;
            }
            if (entity.isSneaking()) {
                flags |= EntityFrame.FLAG_SNEAKING;
            }
            if (entity.isSprinting()) {
                flags |= EntityFrame.FLAG_SPRINTING;
            }
            String customName = null;
            if (entity.hasCustomName()) {
                customName = Texts.toJson(entity.getCustomName());
            }
            entityScratch.add(new EntityFrame(
                    entity.getId(), world.getTime(), typeId,
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYaw(), entity.getPitch(), headYaw,
                    health, maxHealth, customName, flags));
        }
    }

    /** The seed is only metadata (replays render in the live world); resolve it best-effort. */
    private static long resolveSeed(MinecraftClient client) {
        try {
            if (client.getServer() != null && client.getServer().getOverworld() != null) {
                return client.getServer().getOverworld().getSeed();
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    @Override
    public void close() throws IOException {
        writer.writeEnd();
        writer.close();
    }
}
