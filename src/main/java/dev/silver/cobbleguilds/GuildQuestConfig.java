package dev.silver.cobbleguilds;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validated, hot-reloadable quest templates. Changes apply to NEW rotations, not existing payouts. */
public final class GuildQuestConfig {
    public static final class Entry {
        public String name, kind, qualifier = "", cadence;
        public int target, minimum, candies;
        public String rewardItemId="cobblemon:rare_candy", rewardItemData="";
        public int rewardCount=0; // legacy config migrates original candy amount
        public boolean enabled = true;
        public Entry(){}
        Entry(String cadence, QuestCatalog.Definition d){
            this.cadence=cadence;name=d.name();kind=d.kind();qualifier=d.qualifier();
            target=d.target();minimum=d.minimum();candies=d.candies();rewardCount=Math.max(1,d.candies());
        }
    }
    public List<Entry> quests=new ArrayList<>();
    private static GuildQuestConfig current;
    public static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("cobbleguilds-quests.json");}
    public static GuildQuestConfig current(){if(current==null)throw new IllegalStateException("Quests not initialized");return current;}
    public static void init(){
        if(!Files.exists(path())){
            GuildQuestConfig defaults=new GuildQuestConfig();
            for(var q:QuestCatalog.DAILY)defaults.quests.add(new Entry("daily",q));
            for(var q:QuestCatalog.WEEKLY)defaults.quests.add(new Entry("weekly",q));
            defaults.save();
        }else {reload();current.save();} // Preserve existing quest settings and regenerate # explanations.
    }
    /** Only swap the live catalog after every required entry passes validation. */
    public static void reload(){
        try{
            GuildQuestConfig candidate=GuildConfig.JSON.fromJson(GuildHashComments.strip(Files.readString(path(),StandardCharsets.UTF_8)),GuildQuestConfig.class);
            if(candidate==null)throw new IllegalArgumentException("Empty quest config");
            candidate.apply();current=candidate;
        }catch(Exception e){throw new IllegalArgumentException("Quest reload failed, old settings retained: "+e.getMessage(),e);}
    }
    public void save(){
        apply();
        try{
            Files.createDirectories(path().getParent());Path temp=path().resolveSibling(path().getFileName()+".tmp");
            Files.writeString(temp,GuildConfigComments.quests(this),StandardCharsets.UTF_8);
            try{Files.move(temp,path(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);}
            current=this;
        }catch(Exception e){throw new IllegalStateException("Could not write quest config",e);}
    }
    private void apply(){
        if(quests==null||quests.size()>150)throw new IllegalArgumentException("Quests array missing or too large");
        List<QuestCatalog.Definition> daily=new ArrayList<>(),weekly=new ArrayList<>();
        for(Entry e:quests){
            if(e==null||e.name==null||e.name.isBlank()||e.name.length()>48||e.target<1||e.target>200000000||e.minimum<1||e.minimum>e.target)
                throw new IllegalArgumentException("Invalid quest name/target/minimum");
            if(e.candies<0||e.candies>64)throw new IllegalArgumentException("Invalid legacy candy amount");
            if(!Set.of("capture","shiny_capture","type_capture","unique_capture","deposit","species_capture").contains(e.kind))
                throw new IllegalArgumentException("Unsupported quest objective: "+e.kind);
            if(("type_capture".equals(e.kind)||"species_capture".equals(e.kind))&&(e.qualifier==null||!e.qualifier.matches("[a-z]{3,32}")))
                throw new IllegalArgumentException("Invalid type/species qualifier");
            if(!"daily".equals(e.cadence)&&!"weekly".equals(e.cadence))throw new IllegalArgumentException("Invalid cadence");
            if(e.rewardItemId==null || e.rewardItemData==null)throw new IllegalArgumentException("Missing reward item");
            if(e.rewardCount==0)e.rewardCount=Math.max(1,e.candies); // backwards compatibility
            if(e.rewardCount<1||e.rewardCount>64)throw new IllegalArgumentException("Reward count must be 1-64");
            if(e.enabled){
                var def=new QuestCatalog.Definition(e.name,e.kind,e.qualifier==null?"":e.qualifier,e.target,e.minimum,e.candies,e.rewardItemId,e.rewardItemData,e.rewardCount);
                ("daily".equals(e.cadence)?daily:weekly).add(def);
            }
        }
        if(daily.isEmpty()||weekly.isEmpty())throw new IllegalArgumentException("At least one daily and weekly quest must remain enabled");
        QuestCatalog.configure(daily,weekly);
    }
}
