package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link HudState} snapshot from the live client. Captures player
 * vitals, status effects, the scoreboard, the hotbar/inventory, and the tab
 * player list so the replay can reproduce the full HUD.
 */
public final class HudCapture {
    // Inventory encoding cache: the expensive SNBT serialization of every
    // slot only runs when the inventory actually changed (fingerprint).
    private static long lastInvFingerprint = Long.MIN_VALUE;
    private static final List<String> cachedMain = new ArrayList<>(36);
    private static final List<String> cachedArmor = new ArrayList<>(4);
    private static String cachedOffHand = "";
    private static int cachedSelectedSlot = -1;

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

        // --- Hotbar / inventory (cached; re-encoded only on change) ---
        PlayerInventory inv = player.getInventory();
        long fp = fingerprint(inv, player);
        if (fp == lastInvFingerprint) {
            h.selectedSlot = cachedSelectedSlot;
            h.mainInventory.addAll(cachedMain);
            h.armorSlots.addAll(cachedArmor);
            h.offHand = cachedOffHand;
        } else {
            lastInvFingerprint = fp;
            cachedSelectedSlot = inv.getSelectedSlot();
            h.selectedSlot = cachedSelectedSlot;
            cachedMain.clear();
            for (ItemStack stack : inv.getMainStacks()) {
                String s = encodeItem(stack, world);
                cachedMain.add(s);
                h.mainInventory.add(s);
            }
            cachedArmor.clear();
            // armor: feet, legs, chest, head
            String[] armor = {
                    encodeItem(player.getEquippedStack(EquipmentSlot.FEET), world),
                    encodeItem(player.getEquippedStack(EquipmentSlot.LEGS), world),
                    encodeItem(player.getEquippedStack(EquipmentSlot.CHEST), world),
                    encodeItem(player.getEquippedStack(EquipmentSlot.HEAD), world),
            };
            for (String s : armor) {
                cachedArmor.add(s);
                h.armorSlots.add(s);
            }
            cachedOffHand = encodeItem(player.getEquippedStack(EquipmentSlot.OFFHAND), world);
            h.offHand = cachedOffHand;
        }

        // --- Status effects ---
        for (StatusEffectInstance inst : player.getStatusEffects()) {
            int id = Registries.STATUS_EFFECT.getRawId(inst.getEffectType().value());
            h.effects.add(new HudState.Effect(id, inst.getAmplifier(), inst.getDuration(),
                    inst.isAmbient(), inst.shouldShowParticles(), inst.shouldShowIcon()));
        }

        // --- Tab player list ---
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile() != null ? entry.getProfile().name() : "";
                java.util.UUID uuid = entry.getProfile() != null ? entry.getProfile().id() : new java.util.UUID(0, 0);
                String gameMode = entry.getGameMode() != null ? entry.getGameMode().getId() : "";
                h.playerList.add(new HudState.PlayerEntry(
                        uuid, name, entry.getLatency(), gameMode, Texts.toJson(entry.getDisplayName())));
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
                h.objectives.add(new HudState.Objective(objective.getName(), Texts.toJson(objective.getDisplayName()), slot));
            }
            for (Team team : sb.getTeams()) {
                Formatting color = team.getColor();
                h.teams.add(new HudState.Team(
                        team.getName(), Texts.toJson(team.getDisplayName()),
                        Texts.toJson(team.getPrefix()), Texts.toJson(team.getSuffix()),
                        color != null ? color.getName() : "",
                        team.isFriendlyFireAllowed(), team.shouldShowFriendlyInvisibles(),
                        team.getCollisionRule().ordinal(),
                        team.getNameTagVisibilityRule().ordinal(),
                        team.getDeathMessageVisibilityRule().ordinal()));
            }
            for (ScoreboardObjective objective : sb.getObjectives()) {
                for (ScoreboardEntry entry : sb.getScoreboardEntries(objective)) {
                    h.scores.add(new HudState.Score(objective.getName(), entry.owner(), entry.value()));
                }
            }
        }

        return h;
    }

    /** Cheap fingerprint of the inventory contents (selected slot + item ids + counts). */
    private static long fingerprint(PlayerInventory inv, ClientPlayerEntity player) {
        long h = 0x811c9dc5L;
        h = h * 31 + inv.getSelectedSlot();
        for (ItemStack s : inv.getMainStacks()) {
            h = h * 31 + stackId(s);
            h = h * 31 + s.getCount();
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND}) {
            ItemStack s = player.getEquippedStack(slot);
            h = h * 31 + stackId(s);
            h = h * 31 + s.getCount();
        }
        return h;
    }

    private static int stackId(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Registries.ITEM.getRawId(s.getRegistryEntry().value());
        } catch (Throwable t) {
            return -1; // forces re-encode on next change, safe direction
        }
    }

    /** Serialize an item stack to SNBT (empty string for empty slots). */
    private static String encodeItem(ItemStack stack, ClientWorld world) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            NbtElement nbt = ItemStack.CODEC
                    .encodeStart(world.getRegistryManager().getOps(NbtOps.INSTANCE), stack)
                    .result()
                    .orElse(null);
            return nbt == null ? "" : nbt.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
