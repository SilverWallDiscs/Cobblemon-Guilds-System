package dev.silver.cobbleguilds;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Configurable quest templates. Accepted objective types are those with actual server event hooks. */
public final class GuildQuestConfig {
    public static final class Entry {
        public String name, kind, qualifier = "", cadence;
        public int target, minimum, candies;
        public boolean enabled = true;
        public Entry(){}
        Entry(String cadence,QuestCatalog.Definition d){
            this.cadence=cadence;name=d.name();kind=d.kind();qualifier=d.qualifier();
            target=d.target();minimum=d.minimum();candies=d.candies();
        }
    }
    public List<Entry> quests = new ArrayList<>();
    public static void init(){
        Path path=FabricLoader.getInstance().getConfigDir().resolve("cobbleguilds-quests.json");
        try {
            GuildQuestConfig cfg;
            if(Files.exists(path)) cfg=GuildConfig.JSON.fromJson(Files.readString(path),GuildQuestConfig.class);
            else {
                cfg=new GuildQuestConfig();
                for(var q:QuestCatalog.DAILY)cfg.quests.add(new Entry("daily",q));
                for(var q:QuestCatalog.WEEKLY)cfg.quests.add(new Entry("weekly",q));
                Files.createDirectories(path.getParent());
                Files.writeString(path,GuildConfig.JSON.toJson(cfg),StandardCharsets.UTF_8);
            }
            if(cfg==null || cfg.quests==null)throw new IllegalArgumentException("Missing quests array");
            List<QuestCatalog.Definition> daily=new ArrayList<>(),weekly=new ArrayList<>();
            for(Entry e:cfg.quests){
                if(e==null || !e.enabled)continue;
                if(e.name==null || e.name.isBlank() || e.name.length()>48 || e.target<1 || e.target>200000000
                    || e.minimum<1 || e.minimum>e.target || e.candies<0 || e.candies>64)continue;
                if(e.kind==null || !Set.of("capture","shiny_capture","type_capture","unique_capture","deposit").contains(e.kind))continue;
                if("type_capture".equals(e.kind) && (e.qualifier==null || !e.qualifier.matches("[a-z]{3,15}")))continue;
                QuestCatalog.Definition def=new QuestCatalog.Definition(e.name,e.kind,e.qualifier==null?"":e.qualifier,e.target,e.minimum,e.candies);
                if("daily".equals(e.cadence)) daily.add(def);
                if("weekly".equals(e.cadence)) weekly.add(def);
            }
            if(!daily.isEmpty() && !weekly.isEmpty())QuestCatalog.configure(daily,weekly);
            else CobbleGuilds.LOG.warn("Quest config requires at least one valid daily and one valid weekly quest; keeping defaults.");
        }catch(Exception e){
            CobbleGuilds.LOG.error("Cannot load quest configuration; retaining built-in defaults. File: {}",path,e);
        }
    }
}
