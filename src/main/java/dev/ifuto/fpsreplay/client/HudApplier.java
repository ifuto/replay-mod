package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Applies a recorded {@link HudState} back onto the live client so the real
 * HUD (hearts, hunger, effect icons, scoreboard sidebar) renders exactly as it
 * did while recording.
 *
 * <p>Every section is best-effort and wrapped so a mapping mismatch in one
 * section never aborts rendering.</p>
 */
public final class HudApplier {
    private HudApplier() {
    }

    public static void apply(MinecraftClient client, HudState h) {
        if (h == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }

        // Vitals.
        try {
            player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(h.maxHealth);
            player.setHealth(h.health);
            player.setAbsorptionAmount(h.absorption);
            player.getHungerManager().setFoodLevel(h.food);
            player.getHungerManager().setSaturationLevel(h.saturation);
            player.setAir(h.air);
            player.experienceLevel = h.experienceLevel;
            player.experienceProgress = h.experienceProgress;
            player.totalExperience = h.experienceTotal;
            player.setScore(h.score);
        } catch (Throwable t) {
            FpsReplayClient.LOGGER.warn("[FPS Replay] vitals apply failed", t);
        }

        // Status effects.
        try {
            player.clearStatusEffects();
            for (HudState.Effect e : h.effects) {
                Registries.STATUS_EFFECT.getEntry(e.id).ifPresent(entry ->
                        player.addStatusEffect(new StatusEffectInstance(
                                entry, e.durationTicks, e.amplifier,
                                e.ambient, e.showParticles, e.showIcon)));
            }
        } catch (Throwable t) {
            FpsReplayClient.LOGGER.warn("[FPS Replay] effect apply failed", t);
        }

        // Scoreboard.
        applyScoreboard(world.getScoreboard(), h);
    }

    private static void applyScoreboard(Scoreboard sb, HudState h) {
        if (sb == null) {
            return;
        }
        try {
            // Clear existing objectives and teams.
            for (ScoreboardObjective existing : sb.getObjectives()) {
                sb.removeObjective(existing);
            }
            for (Team existing : sb.getTeams()) {
                sb.removeTeam(existing);
            }

            // Rebuild objectives.
            for (HudState.Objective o : h.objectives) {
                Text displayName = textFromJson(o.displayName, o.displayName);
                ScoreboardObjective objective = sb.addObjective(
                        o.name, ScoreboardCriterion.DUMMY, displayName, ScoreboardCriterion.RenderType.INTEGER);
                switch (o.slot) {
                    case 0 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.LIST, objective);
                    case 1 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
                    case 2 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.BELOW_NAME, objective);
                    default -> { }
                }
            }

            // Rebuild teams.
            for (HudState.Team t : h.teams) {
                Team team = sb.addTeam(t.name);
                team.setDisplayName(textFromJson(t.displayName, t.displayName));
                team.setPrefix(textFromJson(t.prefix, t.prefix));
                team.setSuffix(textFromJson(t.suffix, t.suffix));
                team.setColor(Formatting.byColorValue(t.color));
                team.setFriendlyFireAllowed(t.friendlyFire);
                team.setShowFriendlyInvisibles(t.seeFriendlyInvisibles);
                team.setCollisionRule(Team.CollisionRule.values()[clamp(t.collisionRule, 0, Team.CollisionRule.values().length - 1)]);
                team.setNameTagVisibilityRule(Team.VisibilityRule.values()[clamp(t.nameTagVisibility, 0, Team.VisibilityRule.values().length - 1)]);
                team.setDeathMessageVisibilityRule(Team.VisibilityRule.values()[clamp(t.deathMessageVisibility, 0, Team.VisibilityRule.values().length - 1)]);
            }

            // Rebuild scores.
            for (HudState.Score s : h.scores) {
                ScoreboardObjective objective = sb.getNullableObjective(s.objective);
                if (objective != null) {
                    sb.getPlayerScore(s.player, objective).setScore(s.value);
                }
            }
        } catch (Throwable t) {
            FpsReplayClient.LOGGER.warn("[FPS Replay] scoreboard apply failed", t);
        }
    }

    private static Text textFromJson(String json, String fallback) {
        if (json == null || json.isEmpty()) {
            return Text.literal(fallback == null ? "" : fallback);
        }
        try {
            Text text = Text.Serialization.fromJson(json, MinecraftClient.getInstance().world.getRegistryManager());
            return text != null ? text : Text.literal(fallback);
        } catch (Throwable t) {
            return Text.literal(fallback == null ? "" : fallback);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
