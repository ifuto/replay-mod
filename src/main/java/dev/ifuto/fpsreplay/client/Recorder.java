package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.BlockChange;
import dev.ifuto.fpsreplay.replay.CameraFrame;
import dev.ifuto.fpsreplay.replay.EntityFrame;
import dev.ifuto.fpsreplay.replay.ReplayFile;
import dev.ifuto.fpsreplay.replay.ReplayMetadata;
import dev.ifuto.fpsreplay.replay.ReplayWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.SharedConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The recorder. This is the "軽量化" heart of the mod: instead of capturing
 * pixels every frame, it samples the camera once per <b>tick</b> (20/s) and
 * writes a few quantized bytes per sample. Entity snapshots and block changes
 * are written as sparse events, so the recording cost is negligible even in
 * heavy scenes.
 */
public final class Recorder implements AutoCloseable {
    private static Recorder instance;

    private final ReplayWriter writer;
    private final File file;
    private long lastKeyTick = Long.MIN_VALUE;
    private CameraFrame lastKey;

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

    /**
     * Begin recording into {@code <gameDir>/replays/<name>.fpr}.
     *
     * @return the created file, or null on failure.
     */
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
            FpsReplayClient.LOGGER.info("[FPS Replay] Recording started -> {}", file);
            return file;
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Failed to start recording", e);
            return null;
        }
    }

    /** Called once per client tick while recording. */
    public static void tick() {
        if (instance != null) {
            instance.sample();
        }
    }

    /** Called whenever a block changes in the client world (from the mixin). */
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
        try {
            instance.close();
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Failed to finalize recording", e);
        }
        FpsReplayClient.LOGGER.info("[FPS Replay] Recording saved -> {}", instance.file);
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
        Vec3d eye = player.getEyePos();
        float fov = (float) (double) client.options.getFov().getValue();
        CameraFrame cam = new CameraFrame(
                tick,
                eye.x, eye.y, eye.z,
                player.getYaw(), player.getPitch(), 0.0f,
                fov, player.handSwingProgress);

        try {
            if (lastKey == null || tick - lastKeyTick >= ReplayConfig.keyframeInterval) {
                List<EntityFrame> entities = sampleEntities(world, player);
                writer.writeKeyframe(tick, cam, entities);
                lastKey = cam;
                lastKeyTick = tick;
            } else {
                writer.writeTick(tick, cam, lastKey);
            }
        } catch (IOException e) {
            FpsReplayClient.LOGGER.error("[FPS Replay] Recording write failed", e);
            stop();
        }
    }

    private List<EntityFrame> sampleEntities(ClientWorld world, ClientPlayerEntity self) {
        double rangeSq = (double) ReplayConfig.entityRange * ReplayConfig.entityRange;
        List<EntityFrame> out = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity == self) {
                continue;
            }
            if (entity.squaredDistanceTo(self) > rangeSq) {
                continue;
            }
            int typeId = Registries.ENTITY_TYPE.getRawId(entity.getType());
            float headYaw = entity instanceof LivingEntity living ? living.getHeadYaw() : entity.getYaw();
            out.add(new EntityFrame(
                    entity.getId(), typeId,
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYaw(), entity.getPitch(), headYaw));
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        writer.writeEnd();
        writer.close();
    }
}
