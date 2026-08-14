package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.BlockChange;
import dev.ifuto.fpsreplay.replay.CameraFrame;
import dev.ifuto.fpsreplay.replay.EntityDelta;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final List<EntityFrame> newEntityScratch = new ArrayList<>(32);
    private final List<EntityDelta> deltaScratch = new ArrayList<>(128);
    private final Map<Integer, EntityFrame> lastEntity = new HashMap<>();
    private final Map<Integer, Long> lastEntityFullTick = new HashMap<>();
    private final TerrainRecorder terrainRecorder = new TerrainRecorder();
    /** Pending block changes, drained once per tick (avoids per-change I/O lag). */
    private final java.util.concurrent.ConcurrentLinkedQueue<BlockChange> blockQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_BLOCK_CHANGES_PER_TICK = 2000;

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
            // Queue the terrain around the player; it drains over subsequent
            // ticks (a few columns per tick) to avoid an FPS spike.
            instance.terrainRecorder.enqueueRadius(client, ReplayConfig.terrainChunkRadius);
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
        // Enqueue only (no I/O on the hot path). Drained once per tick.
        MinecraftClient client = MinecraftClient.getInstance();
        long tick = client.world != null ? client.world.getTime() : 0L;
        int stateId = Block.STATE_IDS.getRawId(state);
        if (instance.blockQueue.size() < 100_000) {
            instance.blockQueue.add(new BlockChange(tick, pos.getX(), pos.getY(), pos.getZ(), stateId));
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
        float fov = client.options.getFov().getValue().floatValue();
        float handSwing;
        if (CameraCapture.valid) {
            x = CameraCapture.x;
            y = CameraCapture.y;
            z = CameraCapture.z;
            yaw = CameraCapture.yaw;
            pitch = CameraCapture.pitch;
        } else {
            var eye = player.getEyePos();
            x = eye.x;
            y = eye.y;
            z = eye.z;
            yaw = player.getYaw();
            pitch = player.getPitch();
        }
        handSwing = player.handSwingProgress;

        try {
            if (lastKeyTick == Long.MIN_VALUE || tick - lastKeyTick >= ReplayConfig.keyframeInterval) {
                // Keyframe: capture HUD, anchor the delta encoding, and
                // snapshot any newly-seen terrain columns. Written first so the
                // tick delta is anchored to an absolute tick on the first record.
                lastKey = new CameraFrame(tick, x, y, z, yaw, pitch, 0.0f, fov, handSwing);
                writer.writeKeyframe(tick, lastKey, HudCapture.capture(client));
                terrainRecorder.enqueueRadius(client, ReplayConfig.terrainChunkRadius);
                lastKeyTick = tick;
            } else {
                writer.writeTick(
                        tick,
                        x - lastKey.x, y - lastKey.y, z - lastKey.z,
                        yaw - lastKey.yaw, pitch - lastKey.pitch, 0.0f,
                        fov - lastKey.fov, handSwing);
            }

            // Drain terrain snapshot queue a few columns per tick (no spikes).
            terrainRecorder.drain(client, writer);

            // Flush buffered block changes (bounded, batched once per tick).
            int flushed = 0;
            BlockChange bc;
            while (flushed < MAX_BLOCK_CHANGES_PER_TICK && (bc = blockQueue.poll()) != null) {
                writer.writeBlockChange(bc);
                flushed++;
            }

            // Entities: new/refreshed entities are written at full precision;
            // moved entities as quantized deltas; static entities are skipped.
            int interval = Math.max(1, ReplayConfig.entityRecordInterval);
            if (tick % interval == 0) {
                sampleEntities(world, player);
                writer.writeEntities(tick, newEntityScratch, deltaScratch);
            }
        } catch (IOException e) {
            FlashReplayClient.LOGGER.error("[Flash Replay] Recording write failed", e);
            stop();
        }
    }

    private void sampleEntities(ClientWorld world, ClientPlayerEntity self) {
        newEntityScratch.clear();
        deltaScratch.clear();
        long tick = world.getTime();
        double rangeSq = (double) ReplayConfig.entityRange * ReplayConfig.entityRange;
        long refreshInterval = Math.max(1, ReplayConfig.keyframeInterval);

        for (Entity entity : world.getEntities()) {
            if (entity == self) {
                continue;
            }
            if (entity.squaredDistanceTo(self) > rangeSq) {
                continue;
            }
            EntityFrame cur = toFrame(entity, tick);
            EntityFrame prev = lastEntity.get(entity.getId());
            Long prevFullTick = lastEntityFullTick.get(entity.getId());

            if (prev == null || prevFullTick == null || tick - prevFullTick >= refreshInterval) {
                // New entity, or periodic refresh (keeps health/name in sync).
                newEntityScratch.add(cur);
                lastEntity.put(entity.getId(), cur);
                lastEntityFullTick.put(entity.getId(), tick);
            } else {
                EntityDelta delta = deltaBetween(prev, cur);
                if (delta.isZero()) {
                    // Static: skip entirely (costs nothing).
                    continue;
                }
                deltaScratch.add(delta);
                lastEntity.put(entity.getId(), cur);
            }
        }
    }

    private static EntityFrame toFrame(Entity entity, long tick) {
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
        return new EntityFrame(
                entity.getId(), tick, typeId,
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch(), headYaw,
                health, maxHealth, customName, flags);
    }

    private static EntityDelta deltaBetween(EntityFrame prev, EntityFrame cur) {
        return new EntityDelta(
                cur.entityId,
                (int) Math.round((cur.x - prev.x) * ReplayWriter.POS_SCALE),
                (int) Math.round((cur.y - prev.y) * ReplayWriter.POS_SCALE),
                (int) Math.round((cur.z - prev.z) * ReplayWriter.POS_SCALE),
                (int) Math.round(shortestAngle(cur.yaw - prev.yaw) * ReplayWriter.ROT_SCALE),
                (int) Math.round(shortestAngle(cur.pitch - prev.pitch) * ReplayWriter.ROT_SCALE),
                (int) Math.round(shortestAngle(cur.headYaw - prev.headYaw) * ReplayWriter.ROT_SCALE));
    }

    private static float shortestAngle(float delta) {
        float d = delta % 360.0f;
        if (d >= 180.0f) {
            d -= 360.0f;
        } else if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
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
