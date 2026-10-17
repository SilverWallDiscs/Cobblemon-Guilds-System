package dev.silver.cobbleguilds;

import java.util.*;
import java.math.BigInteger;

/** All data is keyed by UUID; usernames are not reliable identifiers. */
public final class Guild {
    public String id = UUID.randomUUID().toString();
    public String name = "Guild";
    public String tag = "GLD";
    public String color = "§7";
    public String description = "";
    public String icon = "minecraft:name_tag";
    public boolean publicGuild = true;
    public boolean closed = false;
    public String leader = "";
    public Map<String, String> members = new LinkedHashMap<>(); // UUID -> rank ID
    public Map<String, Rank> ranks = new LinkedHashMap<>();
    public Set<String> pending = new LinkedHashSet<>();
    public Set<String> banned = new LinkedHashSet<>();
    public Set<String> invitations = new LinkedHashSet<>(); // privileged invitations: immediate join after acceptance
    public Set<String> ordinaryInvites = new LinkedHashSet<>(); // ordinary invitations: create application on acceptance
    public String bank = "0";
    public int chestRows = 1;
    public int level = 1;
    public long experience = 0;
    public int xpCurveVersion = 0;
    public Map<String,String> memberNames = new LinkedHashMap<>();
    public Map<String, Effect> effects = new LinkedHashMap<>();
    public Map<String, Quest> quests = new LinkedHashMap<>();
    public String lastFeeDay = "";
    public String lastWarned = "";
    public List<String> audit = new ArrayList<>();
    // Additional data is kept in world-scoped guilds.json; fields added in later versions are backwards compatible.
    public int memberSlotsBonus = 0;
    public String homeWorld = "";
    public double homeX, homeY, homeZ;
    public float homeYaw, homePitch;
    public String announcement = "";
    public String pendingLeader = "";
    public long leadershipTokenExpiresAt = 0;
    public Map<String,String> banReasons = new LinkedHashMap<>();
    public String lastQuestDay = "";
    public String lastQuestWeek = "";
    public int questStreak = 0;
    public long totalCaptures = 0;
    public long weeklyCaptures = 0;
    public String weeklyCapturePeriod = "";
    public int questsCompleted = 0;
    public Set<String> achievements = new LinkedHashSet<>();
    public Set<String> allies = new LinkedHashSet<>();
    public Set<String> incomingAlliances = new LinkedHashSet<>();
    public Set<String> completedBingoLines = new LinkedHashSet<>();
    public String lastCompletedDailyQuestDay = "";

    public static Guild fresh(UUID creator, String name, String tag, String color, boolean publicGuild, String description) {
        Guild g = new Guild();
        g.name = name; g.tag = tag; g.color = color;
        g.xpCurveVersion = 1;
        g.publicGuild = publicGuild; g.description = description;
        g.leader = creator.toString();
        g.members.put(g.leader, "leader");
        g.ranks.put("leader", new Rank("Leader", Integer.MAX_VALUE, false));
        g.ranks.put("member", new Rank("Member", 0, true));
        return g;
    }
    public int capacity(int baseLimit) { return Math.min(100, Math.max(1, baseLimit + memberSlotsBonus)); }
    public boolean canManageRank(UUID actor, String targetRankId) {
        if (!allowed(actor, "manage_ranks") || "leader".equals(targetRankId)) return false;
        Rank own = rank(actor), target = ranks.get(targetRankId);
        return own != null && target != null && target.priority < own.priority;
    }
    public boolean validDefaultRank() {
        return ranks.entrySet().stream().filter(e -> !e.getKey().equals("leader") && e.getValue().isDefault).count() == 1;
    }
    public boolean contains(UUID id) { return !closed && members.containsKey(id.toString()); }
    public boolean isLeader(UUID id) { return leader.equals(id.toString()); }
    public Rank rank(UUID id) { return ranks.get(members.get(id.toString())); }
    public boolean allowed(UUID id, String permission) {
        if (!contains(id)) return false;
        if (isLeader(id)) return true;
        Rank rank = rank(id);
        return rank != null && rank.permissions.contains(permission);
    }
    public String defaultRank() {
        return ranks.entrySet().stream().filter(e -> e.getValue().isDefault).map(Map.Entry::getKey).findFirst().orElse("member");
    }
    public BigInteger funds() {
        try { return new BigInteger(bank); } catch (RuntimeException ex) { return BigInteger.ZERO; }
    }
    public void addAudit(String event) {
        audit.add(java.time.Instant.now() + " " + event);
        if (audit.size() > 300) audit.remove(0);
    }
    public static final class Rank {
        public String name;
        public int priority;
        public boolean isDefault;
        public Set<String> permissions = new LinkedHashSet<>();
        public Rank(String name, int priority, boolean isDefault) {
            this.name = name; this.priority = priority; this.isDefault = isDefault;
        }
    }
    public static final class Effect {
        public String id;
        public long expiresAt;
        public boolean permanent;
        public Effect(String id,long expiresAt, boolean permanent){this.id=id;this.expiresAt=expiresAt;this.permanent=permanent;}
    }
    public static final class Quest {
        public String id;
        public String name;
        public int target;
        public int minimum;
        public int candies;
        public String rewardItemId="cobblemon:rare_candy";
        public String rewardItemData="";
        public int rewardCount=0;
        public String kind = "capture"; // capture | deposit (more event types added when their Cobblemon hooks are verified)
        public String qualifier = "";
        public String highestBankFunds = "0";
        public Set<String> uniqueSpecies = new LinkedHashSet<>();
        public int extraMoneyForFirst = 10000, extraMoneyForSecond = 5000, extraMoneyForThird = 3000;
        public long expiresAt;
        public boolean completed;
        public Map<String,Integer> contributions = new LinkedHashMap<>();
        public Set<String> claimed = new LinkedHashSet<>();
        public Quest(String id,String name,int target,int minimum,int candies,long expiresAt){
            this.id=id;this.name=name;this.target=target;this.minimum=minimum;this.candies=candies;this.expiresAt=expiresAt;
        }
        public int progress(){long total=0;for(int n:contributions.values()) total+=Math.max(0,n);return (int)Math.min(target,total);}
        public int amount(UUID id){return contributions.getOrDefault(id.toString(),0);}
        public boolean eligible(UUID id){return completed && amount(id)>=minimum && !claimed.contains(id.toString());}
    }
}
