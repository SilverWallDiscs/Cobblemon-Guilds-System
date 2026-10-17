package dev.silver.cobbleguilds;

import java.util.UUID;
import java.math.BigInteger;

/** Runs without Minecraft dependencies: javac -d /tmp/guildtest Guild.java GuildDomainSelfTest.java */
public final class GuildDomainSelfTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        UUID leader=UUID.randomUUID(),member=UUID.randomUUID();
        Guild guild=Guild.fresh(leader,"Rare Candy","RARE","§b",true,"Capture together!");
        check(guild.contains(leader),"leader in guild");
        check(guild.isLeader(leader),"leader identity");
        check(guild.allowed(leader,"bank_withdraw"),"leader always allowed");
        check(!guild.contains(member),"outsiders not members");
        check(guild.defaultRank().equals("member"),"one default role");
        guild.members.put(member.toString(),guild.defaultRank());
        check(!guild.allowed(member,"bank_withdraw"),"member initially cannot withdraw");
        guild.ranks.get("member").permissions.add("chest");
        check(guild.allowed(member,"chest"),"permission grant works");
        guild.bank=new BigInteger("10000000000000000000000000000000000000000").toString();
        check(guild.funds().compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0,"bank uses BigInteger");
        Guild.Quest quest=new Guild.Quest("daily-1","Pokémon Collector",100,5,5,System.currentTimeMillis()+86400000L);
        quest.contributions.put(leader.toString(),96);
        quest.contributions.put(member.toString(),4);
        check(quest.progress()==100,"shared progress");
        quest.completed=true;
        check(quest.eligible(leader),"eligible if minimum met");
        check(!quest.eligible(member),"below minimum receives no reward");
        quest.claimed.add(leader.toString());
        check(!quest.eligible(leader),"cannot claim twice");
        check(guild.capacity(10)==10,"base member capacity");
        guild.memberSlotsBonus=10;
        check(guild.capacity(10)==20,"member capacity upgrade");
        check(guild.canManageRank(leader,"member"),"leader can manage members");
        check(!guild.canManageRank(leader,"leader"),"leader rank must not be editable");
        check(guild.validDefaultRank(),"exactly one non-leader default rank");
        Guild r=Guild.fresh(UUID.randomUUID(),"Quest Team","QT","§a",true,"");
        QuestCatalog.refresh(r,"2026-10-17","2026-W42",System.currentTimeMillis()+86400000L,System.currentTimeMillis()+604800000L);
        check(r.quests.size()==3,"two daily quests and one weekly");
        QuestCatalog.refresh(r,"2026-10-17","2026-W42",System.currentTimeMillis()+86400000L,System.currentTimeMillis()+604800000L);
        check(r.quests.size()==3,"daily reload does not duplicate quests");
        check(r.quests.values().stream().anyMatch(q->q.kind.equals("capture")||q.kind.equals("deposit")),"quest rotation only advertises wired objectives");
        guild.closed=true;
        check(!guild.contains(leader),"closed guild no longer accessible");
        System.out.println("PASS: core guild domain invariants");
    }
}
