package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Applies a recorded {@link HudState} back onto the live client so the real
 * HUD (hearts, hunger, effect icons, scoreboard sidebar, hotbar) renders
 * exactly as it did while recording.
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
            player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(h.maxHealth);
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
            FlashReplayClient.LOGGER.warn("[Flash Replay] vitals apply failed", t);
        }

        // Hotbar / inventory.
        try {
            PlayerInventory inv = player.getInventory();
            var main = inv.getMainStacks();
            for (int i = 0; i < main.size() && i < h.mainInventory.size(); i++) {
                main.set(i, decodeItem(h.mainInventory.get(i), world));
            }
            if (h.armorSlots.size() >= 4) {
                player.equipStack(EquipmentSlot.FEET, decodeItem(h.armorSlots.get(0), world));
                player.equipStack(EquipmentSlot.LEGS, decodeItem(h.armorSlots.get(1), world));
                player.equipStack(EquipmentSlot.CHEST, decodeItem(h.armorSlots.get(2), world));
                player.equipStack(EquipmentSlot.HEAD, decodeItem(h.armorSlots.get(3), world));
            }
            player.equipStack(EquipmentSlot.OFFHAND, decodeItem(h.offHand, world));
            inv.setSelectedSlot(clamp(h.selectedSlot, 0, 8));
        } catch (Throwable t) {
            FlashReplayClient.LOGGER.warn("[Flash Replay] inventory apply failed", t);
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
            FlashReplayClient.LOGGER.warn("[Flash Replay] effect apply failed", t);
        }

        // Scoreboard.
        applyScoreboard(world.getScoreboard(), h);
    }

    private static void applyScoreboard(Scoreboard sb, HudState h) {
        if (sb == null) {
            return;
        }
        try {
            for (ScoreboardObjective existing : sb.getObjectives()) {
                sb.removeObjective(existing);
            }
            for (Team existing : sb.getTeams()) {
                sb.removeTeam(existing);
            }

            for (HudState.Objective o : h.objectives) {
                Text displayName = Texts.fromJson(o.displayName);
                ScoreboardObjective objective = sb.addObjective(
                        o.name, ScoreboardCriterion.DUMMY, displayName,
                        ScoreboardCriterion.RenderType.INTEGER, false, null);
                switch (o.slot) {
                    case 0 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.LIST, objective);
                    case 1 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
                    case 2 -> sb.setObjectiveSlot(ScoreboardDisplaySlot.BELOW_NAME, objective);
                    default -> { }
                }
            }

            for (HudState.Team t : h.teams) {
                Team team = sb.addTeam(t.name);
                team.setDisplayName(Texts.fromJson(t.displayName));
                team.setPrefix(Texts.fromJson(t.prefix));
                team.setSuffix(Texts.fromJson(t.suffix));
                team.setColor(t.colorName == null || t.colorName.isEmpty() ? null : Formatting.byName(t.colorName));
                team.setFriendlyFireAllowed(t.friendlyFire);
                team.setShowFriendlyInvisibles(t.seeFriendlyInvisibles);
                team.setCollisionRule(Team.CollisionRule.values()[clamp(t.collisionRule, 0, Team.CollisionRule.values().length - 1)]);
                team.setNameTagVisibilityRule(Team.VisibilityRule.values()[clamp(t.nameTagVisibility, 0, Team.VisibilityRule.values().length - 1)]);
                team.setDeathMessageVisibilityRule(Team.VisibilityRule.values()[clamp(t.deathMessageVisibility, 0, Team.VisibilityRule.values().length - 1)]);
            }

            for (HudState.Score s : h.scores) {
                ScoreboardObjective objective = sb.getNullableObjective(s.objective);
                if (objective != null) {
                    sb.getOrCreateScore(ScoreHolder.fromName(s.player), objective).setScore(s.value);
                }
            }
        } catch (Throwable t) {
            FlashReplayClient.LOGGER.warn("[Flash Replay] scoreboard apply failed", t);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Deserialize an item stack from SNBT (empty for empty slots). */
    private static ItemStack decodeItem(String snbt, ClientWorld world) {
        if (snbt == null || snbt.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            NbtElement nbt = StringNbtReader.readCompound(snbt);
            return ItemStack.CODEC
                    .parse(world.getRegistryManager().getOps(NbtOps.INSTANCE), nbt)
                    .result()
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
