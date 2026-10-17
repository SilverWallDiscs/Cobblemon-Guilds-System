package dev.silver.cobbleguilds;

import java.util.*;

/** Deterministic server-side quest rotation. Objective types not backed by tested events are not offered. */
public final class QuestCatalog {
    private QuestCatalog() {}
    public record Definition(String name, String kind, int target, int minimum, int candies) {}
    public static final List<Definition> DAILY = List.of(
        new Definition("Pokémon Collector", "capture", 100, 5, 5),
        new Definition("Capture Expedition", "capture", 50, 3, 3),
        new Definition("Guild Fundraiser", "deposit", 100000, 5000, 3),
        new Definition("Pokémon Marathon", "capture", 150, 10, 7),
        new Definition("Treasury Challenge", "deposit", 50000, 2500, 2)
    );
    public static final List<Definition> WEEKLY = List.of(
        new Definition("Weekly Pokémon Hunt", "capture", 500, 15, 15),
        new Definition("Weekly Treasury Drive", "deposit", 500000, 25000, 12)
    );
    public static void refresh(Guild g, String day, String week, long dailyEnd, long weeklyEnd){
        // One stable random-looking rotation per guild/day; does not change on reboot.
        List<Definition> daily=new ArrayList<>(DAILY);
        Collections.shuffle(daily,new Random(Objects.hash(g.id,day)));
        for(int i=0;i<2;i++){
            Definition d=daily.get(i);
            String id="day-"+day+"-"+i;
            g.quests.computeIfAbsent(id,key->create(id,d,dailyEnd,g.funds().toString()));
        }
        List<Definition> weekly=new ArrayList<>(WEEKLY);
        Collections.shuffle(weekly,new Random(Objects.hash(g.id,week)));
        String id="week-"+week;
        g.quests.computeIfAbsent(id,key->create(id,weekly.get(0),weeklyEnd,g.funds().toString()));
    }
    private static Guild.Quest create(String id,Definition d,long end,String bankBaseline){
        Guild.Quest q=new Guild.Quest(id,d.name(),d.target(),d.minimum(),d.candies(),end);
        q.kind=d.kind();q.highestBankFunds=bankBaseline;return q;
    }
}
