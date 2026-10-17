package dev.silver.cobbleguilds;

public final class GuildLevelsSelfTest {
    public static void main(String[] args){
        Guild g=Guild.fresh(java.util.UUID.randomUUID(),"Test","TST","§a",true,"");
        if(GuildLevels.level(0)!=1 || GuildLevels.level(250)!=2 || GuildLevels.level(750)!=3 || GuildLevels.level(2499)!=4) throw new AssertionError("XP progression");
        if(GuildLevels.questReward(100000)!=150 || GuildLevels.questReward(100)!=25) throw new AssertionError("Quest XP cap");
        for(int i=0;i<2;i++) GuildLevels.grant(g,GuildLevels.questReward("deposit",100000));
        if(g.level!=5)throw new AssertionError("Two large treasury objectives should reach level 5, not 551");
        g.xpCurveVersion=0;g.experience=550000;GuildLevels.reconcile(g);
        if(g.level>5||g.xpCurveVersion!=1)throw new AssertionError("Old XP migrated");
        System.out.println("GuildLevelsSelfTest passed: XP migration and capped quest XP");
    }
}
