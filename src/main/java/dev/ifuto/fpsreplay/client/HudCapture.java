package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Builds a {@link HudState} snapshot from the live client. Captures player
 * vitals, status effects, the scoreboard, and the tab player list so the
 * replay can reproduce the full HUD.
 */
public final class HudCapture {
    private HudCapture() {
    }

    public static HudState capture(MinecraftClient client) {
        HudState h = new HudState();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return h;
        }

        // --- Vitals ---
        h.health = player.getHealth();
        h.maxHealth = player.getMaxHealth();
        h.absorption = player.getAbsorptionAmount();
        h.food = player.getHungerManager().getFoodLevel();
        h.saturation = player.getHungerManager().getSaturationLevel();
        h.air = player.getAir();
        h.maxAir = player.getMaxAir();
        h.armor = player.getArmor();
        h.experienceLevel = player.experienceLevel;
        h.experienceProgress = player.experienceProgress;
        h.experienceTotal = player.totalExperience;
        h.score = player.getScore();
        h.playerListVisible = client.options.playerListKey.isPressed();

        // --- Hotbar / inventory ---
        PlayerInventory inv = player.getInventory();
        h.selectedSlot = inv.selectedSlot;
        for (int i = 0; i < inv.main.size(); i++) {
            h.mainInventory.add(encodeItem(inv.main.get(i), world));
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            h.armorSlots.add(encodeItem(inv.armor.get(i), world));
        }
        h.offHand = encodeItem(inv.offHand.get(0), world);

        // --- Status effects ---
        for (StatusEffectInstance inst : player.getStatusEffects()) {
            int id = Registries.STATUS_EFFECT.getRawId(inst.getEffectType().value());
            h.effects.add(new HudState.Effect(id, inst.getAmplifier(), inst.getDuration(),
                    inst.isAmbient(), inst.shouldShowParticles(), inst.shouldShowIcon()));
        }

        // --- Tab player list ---
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile() != null ? entry.getProfile().getName() : "";
                h.playerList.add(new HudState.PlayerEntry(
                        entry.getProfile() != null ? entry.getProfile().getId() : new java.util.UUID(0, 0),
                        name, entry.getLatency(),
                        entry.getGameMode() != null ? entry.getGameMode().getId() : 0,
                        textJson(entry.getDisplayName(), world)));
            }
        }

        // --- Scoreboard ---
        Scoreboard sb = world.getScoreboard();
        if (sb != null) {
            for (ScoreboardObjective objective : sb.getObjectives()) {
                int slot = -1;
                if (objective == sb.getObjectiveForSlot(ScoreboardDisplaySlot.LIST)) {
                    slot = 0;
                } else if (objective == sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR)) {
                    slot = 1;
                } else if (objective == sb.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME)) {
                    slot = 2;
                }
                h.objectives.add(new HudState.Objective(objective.getName(), textJson(objective.getDisplayName(), world), slot));
            }
            for (Team team : sb.getTeams()) {
                Formatting color = team.getColor();
                h.teams.add(new HudState.Team(
                        team.getName(), textJson(team.getDisplayName(), world),
                        textJson(team.getPrefix(), world), textJson(team.getSuffix(), world),
                        color != null ? color.getName() : "",
                        team.isFriendlyFireAllowed(), team.shouldShowFriendlyInvisibles(),
                        team.getCollisionRule().ordinal(),
                        team.getNameTagVisibilityRule().ordinal(),
                        team.getDeathMessageVisibilityRule().ordinal()));
            }
            for (ScoreboardObjective objective : sb.getObjectives()) {
                for (ScoreboardEntry entry : sb.getScoreboardEntries(objective)) {
                    h.scores.add(new HudState.Score(
                            objective.getName(),
                            entry.owner(),
                            entry.value()));
                }
            }
        }

        return h;
    }

    private static String textJson(Text text, ClientWorld world) {
        if (text == null) {
            return null;
        }
        try {
            return Text.Serialization.toJsonString(text, world.getRegistryManager());
        } catch (Throwable t) {
            return text.getString();
        }
    }

    /** Serialize an item stack to SNBT (empty string for empty slots). */
    private static String encodeItem(ItemStack stack, ClientWorld world) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            NbtElement nbt = stack.encode(world.getRegistryManager());
            if (nbt instanceof NbtCompound compound) {
                return NbtHelper.toNbtProviderString(compound);
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }
}
