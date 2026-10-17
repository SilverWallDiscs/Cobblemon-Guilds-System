package dev.silver.cobbleguilds;

import java.util.*;

/** Deterministic server-side quest rotation. Objective types not backed by tested events are not offered. */
public final class QuestCatalog {
    private QuestCatalog() {}
    public record Definition(String name, String kind, String qualifier, int target, int minimum, int candies, String rewardItemId, String rewardItemData, int rewardCount) {
        public Definition(String name,String kind,String qualifier,int target,int minimum,int candies){this(name,kind,qualifier,target,minimum,candies,"cobblemon:rare_candy","",Math.max(1,candies));}
        public Definition(String name,String kind,int target,int minimum,int candies){this(name,kind,"",target,minimum,candies);}
    }
    public static final List<Definition> DAILY = List.of(
        new Definition("Pokémon Collector", "capture", 100, 5, 5),
        new Definition("Capture Expedition", "capture", 50, 3, 3),
        new Definition("Guild Fundraiser", "deposit", 100000, 5000, 3),
        new Definition("Pokémon Marathon", "capture", 150, 10, 7),
        new Definition("Shiny Hunters", "shiny_capture", 3, 1, 10),
        new Definition("Magikarp Bounty", "species_capture", "magikarp", 20, 2, 5),
        new Definition("Pikachu Bounty", "species_capture", "pikachu", 10, 1, 5),
        new Definition("Water-type Expedition", "type_capture", "water", 50, 3, 3),
        new Definition("Fire-type Expedition", "type_capture", "fire", 50, 3, 3),
        new Definition("Grass-type Expedition", "type_capture", "grass", 50, 3, 3),
        new Definition("Pokédex Expedition", "unique_capture", 50, 3, 6),
        new Definition("Treasury Challenge", "deposit", 50000, 2500, 2)
    );
    public static final List<Definition> WEEKLY = List.of(
        new Definition("Weekly Pokémon Hunt", "capture", 500, 15, 15),
        new Definition("Weekly Treasury Drive", "deposit", 500000, 25000, 12),
        new Definition("Weekly Shiny Hunt", "shiny_capture", 6, 1, 20),
        new Definition("Weekly Pokédex Hunt", "unique_capture", 100, 8, 14)
    );
    private static List<Definition> configuredDaily = DAILY;
    private static List<Definition> configuredWeekly = WEEKLY;
    public static void configure(List<Definition> daily,List<Definition> weekly){
        if(!daily.isEmpty() && !weekly.isEmpty()){
            configuredDaily=List.copyOf(daily);configuredWeekly=List.copyOf(weekly);
        }
    }
    public static void refresh(Guild g, String day, String week, long dailyEnd, long weeklyEnd,boolean bingoEnabled){
        refresh(g,day,week,dailyEnd,weeklyEnd,bingoEnabled,"cobblemon:rare_candy","",3,2,1);
    }
    public static void refresh(Guild g, String day, String week, long dailyEnd, long weeklyEnd,boolean bingoEnabled,
                               String bingoRewardItemId,String bingoRewardItemData,int bingoRewardCount,int dailyCount,int weeklyCount){
        // One stable random-looking rotation per guild/day; does not change on reboot.
        List<Definition> daily=new ArrayList<>(configuredDaily);
        Collections.shuffle(daily,new Random(Objects.hash(g.id,day)));
        for(int i=0;i<Math.min(dailyCount,daily.size());i++){
            Definition d=daily.get(i);
            String id="day-"+day+"-"+i;
            g.quests.computeIfAbsent(id,key->create(id,d,dailyEnd,g.funds().toString()));
        }
        List<Definition> weekly=new ArrayList<>(configuredWeekly);
        Collections.shuffle(weekly,new Random(Objects.hash(g.id,week)));
        for(int i=0;i<Math.min(weeklyCount,weekly.size());i++){
            String id="week-"+week+"-"+i;
            Definition def=weekly.get(i);
            g.quests.computeIfAbsent(id,key->create(id,def,weeklyEnd,g.funds().toString()));
        }
        if(!bingoEnabled)return;
        // Nine weekly cooperative Bingo tiles. Their rewards use the existing claim ledger.
        List<Definition> pool=new ArrayList<>();pool.addAll(configuredDaily);pool.addAll(configuredWeekly);
        Collections.shuffle(pool,new Random(Objects.hash(g.id,week,"bingo")));
        for(int i=0;i<9;i++){
            Definition d=pool.get(i % pool.size());String bingoId="bingo-"+week+"-"+i;
            g.quests.computeIfAbsent(bingoId,key->{
                Guild.Quest q=create(bingoId,d,weeklyEnd,g.funds().toString());
                q.name="Bingo: "+d.name();q.candies=Math.min(3,d.candies());
                q.rewardItemId=bingoRewardItemId;q.rewardItemData=bingoRewardItemData;
                q.rewardCount=bingoRewardCount;return q;
            });
        }
    }
    private static Guild.Quest create(String id,Definition d,long end,String bankBaseline){
        Guild.Quest q=new Guild.Quest(id,d.name(),d.target(),d.minimum(),d.candies(),end);
        q.kind=d.kind();q.qualifier=d.qualifier();q.highestBankFunds=bankBaseline;
        q.rewardItemId=d.rewardItemId();q.rewardItemData=d.rewardItemData();q.rewardCount=d.rewardCount();return q;
    }
}
