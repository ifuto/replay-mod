package dev.ifuto.fpsreplay.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A snapshot of the client-side HUD state that must be reproduced for a
 * faithful first-person replay: player vitals (hearts / hunger / armor / air /
 * experience), active status effects, the scoreboard, and the tab player list.
 *
 * <p>Captured at every keyframe (not every tick) because this state changes
 * rarely. All text is stored as JSON strings so formatting is preserved while
 * the format itself stays Minecraft-free.</p>
 */
public final class HudState {
    // --- Vitals (own player) ---
    public float health;
    public float maxHealth;
    public float absorption;
    public int food;
    public float saturation;
    public int air;
    public int maxAir;
    public int armor;
    public int experienceLevel;
    public float experienceProgress;
    public int experienceTotal;
    public int score;

    // --- Status effects ---
    public static final class Effect {
        public int id;
        public int amplifier;
        public int durationTicks;
        public boolean ambient;
        public boolean showParticles;
        public boolean showIcon;

        public Effect(int id, int amplifier, int durationTicks,
                      boolean ambient, boolean showParticles, boolean showIcon) {
            this.id = id;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.ambient = ambient;
            this.showParticles = showParticles;
            this.showIcon = showIcon;
        }
    }

    public final List<Effect> effects = new ArrayList<>();

    // --- Tab player list ---
    public static final class PlayerEntry {
        public UUID uuid;
        public String name;
        public int latency;
        /** 0=survival, 1=creative, 2=adventure, 3=spectator. */
        public int gameMode;
        /** JSON text, or null. */
        public String displayName;

        public PlayerEntry(UUID uuid, String name, int latency, int gameMode, String displayName) {
            this.uuid = uuid;
            this.name = name;
            this.latency = latency;
            this.gameMode = gameMode;
            this.displayName = displayName;
        }
    }

    public final List<PlayerEntry> playerList = new ArrayList<>();

    // --- Scoreboard ---
    public static final class Objective {
        public String name;
        public String displayName; // JSON text
        /** 0=list, 1=sidebar, 2=belowName, -1=none. */
        public int slot;

        public Objective(String name, String displayName, int slot) {
            this.name = name;
            this.displayName = displayName;
            this.slot = slot;
        }
    }

    public static final class Team {
        public String name;
        public String displayName; // JSON text
        public String prefix;      // JSON text
        public String suffix;      // JSON text
        public int color;          // ARGB
        public boolean friendlyFire;
        public boolean seeFriendlyInvisibles;
        /** Ordinals of the MC enums (CollisionRule / VisibilityRule). */
        public int collisionRule;
        public int nameTagVisibility;
        public int deathMessageVisibility;

        public Team(String name, String displayName, String prefix, String suffix, int color,
                    boolean friendlyFire, boolean seeFriendlyInvisibles,
                    int collisionRule, int nameTagVisibility, int deathMessageVisibility) {
            this.name = name;
            this.displayName = displayName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.color = color;
            this.friendlyFire = friendlyFire;
            this.seeFriendlyInvisibles = seeFriendlyInvisibles;
            this.collisionRule = collisionRule;
            this.nameTagVisibility = nameTagVisibility;
            this.deathMessageVisibility = deathMessageVisibility;
        }
    }

    public static final class Score {
        public String objective;
        public String player;
        public int value;

        public Score(String objective, String player, int value) {
            this.objective = objective;
            this.player = player;
            this.value = value;
        }
    }

    public final List<Objective> objectives = new ArrayList<>();
    public final List<Team> teams = new ArrayList<>();
    public final List<Score> scores = new ArrayList<>();

    public HudState() {
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeFloat(health);
        out.writeFloat(maxHealth);
        out.writeFloat(absorption);
        out.writeInt(food);
        out.writeFloat(saturation);
        out.writeInt(air);
        out.writeInt(maxAir);
        out.writeInt(armor);
        out.writeInt(experienceLevel);
        out.writeFloat(experienceProgress);
        out.writeInt(experienceTotal);
        out.writeInt(score);

        out.writeInt(effects.size());
        for (Effect e : effects) {
            IoUtil.writeVarInt(out, e.id);
            out.writeByte(e.amplifier & 0xFF);
            IoUtil.writeVarInt(out, e.durationTicks);
            int flags = (e.ambient ? 1 : 0) | (e.showParticles ? 2 : 0) | (e.showIcon ? 4 : 0);
            out.writeByte(flags);
        }

        out.writeInt(playerList.size());
        for (PlayerEntry p : playerList) {
            out.writeLong(p.uuid.getMostSignificantBits());
            out.writeLong(p.uuid.getLeastSignificantBits());
            IoUtil.writeString(out, p.name);
            IoUtil.writeVarInt(out, p.latency);
            out.writeByte(p.gameMode & 0xFF);
            out.writeBoolean(p.displayName != null);
            if (p.displayName != null) {
                IoUtil.writeString(out, p.displayName);
            }
        }

        out.writeInt(objectives.size());
        for (Objective o : objectives) {
            IoUtil.writeString(out, o.name);
            IoUtil.writeString(out, o.displayName == null ? "" : o.displayName);
            out.writeByte(o.slot);
        }

        out.writeInt(teams.size());
        for (Team t : teams) {
            IoUtil.writeString(out, t.name);
            IoUtil.writeString(out, t.displayName == null ? "" : t.displayName);
            IoUtil.writeString(out, t.prefix == null ? "" : t.prefix);
            IoUtil.writeString(out, t.suffix == null ? "" : t.suffix);
            out.writeInt(t.color);
            out.writeBoolean(t.friendlyFire);
            out.writeBoolean(t.seeFriendlyInvisibles);
            out.writeByte(t.collisionRule);
            out.writeByte(t.nameTagVisibility);
            out.writeByte(t.deathMessageVisibility);
        }

        out.writeInt(scores.size());
        for (Score s : scores) {
            IoUtil.writeString(out, s.objective);
            IoUtil.writeString(out, s.player);
            IoUtil.writeVarIntZigZag(out, s.value);
        }
    }

    public static HudState read(DataInputStream in) throws IOException {
        HudState h = new HudState();
        h.health = in.readFloat();
        h.maxHealth = in.readFloat();
        h.absorption = in.readFloat();
        h.food = in.readInt();
        h.saturation = in.readFloat();
        h.air = in.readInt();
        h.maxAir = in.readInt();
        h.armor = in.readInt();
        h.experienceLevel = in.readInt();
        h.experienceProgress = in.readFloat();
        h.experienceTotal = in.readInt();
        h.score = in.readInt();

        int effectCount = in.readInt();
        for (int i = 0; i < effectCount; i++) {
            int id = IoUtil.readVarInt(in);
            int amplifier = in.readUnsignedByte();
            int duration = IoUtil.readVarInt(in);
            int flags = in.readUnsignedByte();
            h.effects.add(new Effect(id, amplifier, duration,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
        }

        int playerCount = in.readInt();
        for (int i = 0; i < playerCount; i++) {
            long msb = in.readLong();
            long lsb = in.readLong();
            UUID uuid = new UUID(msb, lsb);
            String name = IoUtil.readString(in);
            int latency = IoUtil.readVarInt(in);
            int gameMode = in.readUnsignedByte();
            String displayName = in.readBoolean() ? IoUtil.readString(in) : null;
            h.playerList.add(new PlayerEntry(uuid, name, latency, gameMode, displayName));
        }

        int objectiveCount = in.readInt();
        for (int i = 0; i < objectiveCount; i++) {
            String name = IoUtil.readString(in);
            String displayName = IoUtil.readString(in);
            int slot = in.readByte();
            h.objectives.add(new Objective(name, displayName, slot));
        }

        int teamCount = in.readInt();
        for (int i = 0; i < teamCount; i++) {
            h.teams.add(new Team(
                    IoUtil.readString(in), IoUtil.readString(in), IoUtil.readString(in), IoUtil.readString(in),
                    in.readInt(), in.readBoolean(), in.readBoolean(),
                    in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte()));
        }

        int scoreCount = in.readInt();
        for (int i = 0; i < scoreCount; i++) {
            h.scores.add(new Score(IoUtil.readString(in), IoUtil.readString(in), IoUtil.readVarIntZigZag(in)));
        }
        return h;
    }
}
