package dev.silver.cobbleguilds;

/** An increasing XP cost per level prevents large quest target values from inflating guild levels. */
public final class GuildLevels {
    private GuildLevels(){}
    // Total XP to REACH level n: 125*(n-1)*n, i.e. 250,750,1500,2500...
    public static long threshold(int level){
        if(level<=1)return 0;
        long n=Math.min(level,10_000);
        return 125L*(n-1)*n;
    }
    public static int level(long experience){
        long xp=Math.max(0,experience);
        int lo=1,hi=10_000;
        while(lo<hi){int mid=lo+(hi-lo+1)/2;if(threshold(mid)<=xp)lo=mid;else hi=mid-1;}
        return lo;
    }
    public static int questReward(int target){
        return Math.max(25,Math.min(150,target/100));
    }
    public static int questReward(String kind,int target){
        // Treasury/deposit quests scale with the actual money raised, but never
        // give one XP per dollar. Two hard 100K+ drives grant roughly four levels,
        // not hundreds. Regular catch quests remain much smaller.
        return "deposit".equals(kind)?Math.max(50,Math.min(1250,target/80)):questReward(target);
    }
    public static void reconcile(Guild guild){
        if(guild.xpCurveVersion<1){
            // The former scheme awarded one XP per objective unit and was exploitable
            // by currency quests. Preserve some progress, but cap legacy conversion.
            guild.experience=Math.min(2500,Math.max(0,guild.experience/200));
            guild.xpCurveVersion=1;
        }
        guild.level=level(guild.experience);
    }
    public static void grant(Guild guild,long amount){
        if(amount<0)return;
        guild.experience= guild.experience > Long.MAX_VALUE-amount?Long.MAX_VALUE:guild.experience+amount;
        guild.level=level(guild.experience);
    }
}
