package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.EntityFrame;
import dev.ifuto.fpsreplay.replay.Interpolation;
import dev.ifuto.fpsreplay.replay.ReplayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproduces recorded entities (mobs and other entities, including modded
 * ones) while a replay is being rendered or previewed.
 *
 * <p>For each recorded entity id, a real entity of the recorded type is
 * spawned into the client world and driven from the recorded keyframes
 * (interpolated at render time). Health, max-health, custom name and flags
 * (glowing/sneaking/sprinting) are applied so the entity looks exactly as it
 * did during recording.</p>
 *
 * <p>To avoid double-rendering (recorded mob overlapping the <i>live</i> mob
 * that happens to exist in the current world), live non-player entities are
 * made invisible+silent for the duration of the replay and restored
 * afterwards.</p>
 */
public final class ReplayEntityManager {
    private final Map<Integer, Entity> actors = new HashMap<>();
    private final Map<Integer, Boolean> wasInvisible = new HashMap<>();
    private final Map<Integer, Boolean> wasSilent = new HashMap<>();
    private boolean active;

    public void start(ClientWorld world) {
        active = true;
        // Hide live non-player entities so they don't overlap the replayed ones.
        for (Entity e : world.getEntities()) {
            if (e instanceof PlayerEntity) {
                continue;
            }
            wasInvisible.put(e.getId(), e.isInvisible());
            wasSilent.put(e.getId(), e.isSilent());
            e.setInvisible(true);
            e.setSilent(true);
        }
    }

    public void update(MinecraftClient client, ReplayState state, double tick) {
        if (!active || client.world == null) {
            return;
        }
        ClientWorld world = client.world;
        for (Map.Entry<Integer, List<EntityFrame>> entry : state.entityTracks.entrySet()) {
            int entityId = entry.getKey();
            List<EntityFrame> track = entry.getValue();
            if (track.isEmpty()) {
                continue;
            }
            EntityFrame frame = interpolate(track, tick);
            Entity actor = actors.get(entityId);
            if (actor == null || actor.isRemoved()) {
                actor = createActor(world, frame);
                if (actor == null) {
                    continue;
                }
                actors.put(entityId, actor);
            }
            applyFrame(actor, frame, world);
        }
    }

    public void stop(ClientWorld world) {
        if (!active) {
            return;
        }
        active = false;
        // Remove replay actors.
        for (Entity actor : actors.values()) {
            try {
                actor.discard();
            } catch (Throwable t) {
                // ignore
            }
        }
        actors.clear();
        // Restore live entities.
        if (world != null) {
            for (Entity e : world.getEntities()) {
                if (e instanceof PlayerEntity) {
                    continue;
                }
                Boolean inv = wasInvisible.get(e.getId());
                Boolean sil = wasSilent.get(e.getId());
                if (inv != null) {
                    e.setInvisible(inv);
                }
                if (sil != null) {
                    e.setSilent(sil);
                }
            }
        }
        wasInvisible.clear();
        wasSilent.clear();
    }

    private Entity createActor(ClientWorld world, EntityFrame frame) {
        try {
            var type = Registries.ENTITY_TYPE.get(frame.typeId);
            Entity e = type.create(world, SpawnReason.COMMAND);
            if (e != null) {
                world.spawnEntity(e);
            }
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    private void applyFrame(Entity actor, EntityFrame frame, ClientWorld world) {
        try {
            actor.refreshPositionAndAngles(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
            if (actor instanceof LivingEntity living) {
                if (frame.health >= 0.0f) {
                    living.setHealth(frame.health);
                }
                if (frame.maxHealth > 0.0f) {
                    var attr = living.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    if (attr != null) {
                        attr.setBaseValue(frame.maxHealth);
                    }
                }
                living.setHeadYaw(frame.headYaw);
            }
            if (frame.customName != null) {
                Text name = Texts.fromJson(frame.customName);
                actor.setCustomName(name);
                actor.setCustomNameVisible(true);
            }
            actor.setGlowing((frame.flags & EntityFrame.FLAG_GLOWING) != 0);
            actor.setSneaking((frame.flags & EntityFrame.FLAG_SNEAKING) != 0);
            actor.setSprinting((frame.flags & EntityFrame.FLAG_SPRINTING) != 0);
        } catch (Throwable t) {
            // Best-effort: never let one entity abort the whole frame.
        }
    }

    /** Find the bracketing samples for {@code tick} (binary search) and interpolate. */
    private EntityFrame interpolate(List<EntityFrame> track, double tick) {
        if (track.size() == 1) {
            return track.get(0);
        }
        // Tracks are ascending by tick (one sample per recorded tick).
        int lo = 0;
        int hi = track.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (track.get(mid).tick <= tick) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int i = lo;
        if (i >= track.size() - 1) {
            return track.get(track.size() - 1);
        }
        EntityFrame a = track.get(i);
        EntityFrame b = track.get(i + 1);
        if (a.tick >= b.tick) {
            return b;
        }
        double u = (tick - a.tick) / (double) (b.tick - a.tick);
        u = Math.max(0.0, Math.min(1.0, u));
        return new EntityFrame(
                a.entityId, (long) Math.round(tick), a.typeId,
                Interpolation.lerp(a.x, b.x, u),
                Interpolation.lerp(a.y, b.y, u),
                Interpolation.lerp(a.z, b.z, u),
                Interpolation.lerpAngle(a.yaw, b.yaw, (float) u),
                Interpolation.lerpAngle(a.pitch, b.pitch, (float) u),
                Interpolation.lerpAngle(a.headYaw, b.headYaw, (float) u),
                a.health < 0 || b.health < 0 ? -1 : Interpolation.lerp(a.health, b.health, (float) u),
                a.maxHealth < 0 || b.maxHealth < 0 ? -1 : Interpolation.lerp(a.maxHealth, b.maxHealth, (float) u),
                a.customName, a.flags);
    }
}
