package dev.silver.cobbleguilds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.*;

/** Runtime configuration. Reload never replaces the object referenced by live services. */
public final class GuildConfig {
    public int configVersion = 2;
    public boolean dailyFee = true;
    public String dailyFeePrice = "10000";
    public String guildCreatePrice = "1000000";
    public String timeZone = "system";
    public int maxGuildMembers = 10;
    public boolean questsEnabled = true;
    public int dailyQuestCount = 2;
    public int weeklyQuestCount = 1;
    public boolean bingoEnabled = true;
    public int bingoLineExperience = 150;
    public String bingoRewardItemId = "cobblemon:rare_candy";
    public String bingoRewardItemData = "";
    public int bingoRewardCount = 3;
    public boolean chatSpyEnabled = true;
    public boolean noHud = true;
    public boolean buffPurchasesEnabled = true;
    public Map<String,String> buffPrices = new LinkedHashMap<>();
    public Set<String> disabledBuffs = new LinkedHashSet<>();
    public boolean guildHomesEnabled = true;
    public int guildHomeCooldownSeconds = 120;
    public boolean announcementsEnabled = true;
    public boolean upgradesEnabled = true;
    public int guildMemberUpgradeCost = 1500000;
    public int guildMemberUpgradeIncrement = 5;
    public String[] chestUpgradePrices = {"100000","500000","1000000","3000000","5000000"};
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("cobbleguilds.json");}
    public static GuildConfig load(){
        try {
            GuildConfig loaded;
            if(Files.exists(path())){
                String raw=Files.readString(path(),StandardCharsets.UTF_8);
                loaded=JSON.fromJson(raw,GuildConfig.class);
                if(loaded==null)throw new IllegalArgumentException("Empty configuration");
                // v0.3.x shipped disabled-by-default. Enable on the first schema migration.
                if(!com.google.gson.JsonParser.parseString(raw).getAsJsonObject().has("configVersion") || loaded.configVersion<2){
                    loaded.buffPurchasesEnabled=true;loaded.configVersion=2;
                }
            } else loaded=new GuildConfig();
            loaded.validate();loaded.save();return loaded;
        }catch(Exception e){throw new IllegalStateException("Invalid CobbleGuilds configuration; original file unchanged: "+path(),e);}
    }
    public void validate(){
        if(disabledBuffs==null || buffPrices==null)throw new IllegalArgumentException("Null buff settings");
        if(dailyQuestCount<1||dailyQuestCount>5||weeklyQuestCount<1||weeklyQuestCount>3)throw new IllegalArgumentException("Invalid quest counts");
        if(configVersion<2||bingoLineExperience<0||bingoLineExperience>500||bingoRewardCount<1||bingoRewardCount>64)throw new IllegalArgumentException("Invalid bingo configuration");
        if(maxGuildMembers<1||maxGuildMembers>100||guildHomeCooldownSeconds<0||guildHomeCooldownSeconds>86400)throw new IllegalArgumentException("Invalid member/home configuration");
        if(chestUpgradePrices==null||chestUpgradePrices.length<5)throw new IllegalArgumentException("Missing chest upgrade tiers");
        positive(dailyFeePrice);positive(guildCreatePrice);
        for(String cost:chestUpgradePrices)positive(cost);
        for(var e:buffPrices.entrySet()){
            if(GuildBuffs.byId(e.getKey())==null)throw new IllegalArgumentException("Unknown buff price key: "+e.getKey());
            positive(e.getValue());
        }
        for(String id:disabledBuffs)if(GuildBuffs.byId(id)==null)throw new IllegalArgumentException("Unknown disabled buff: "+id);
        if(bingoRewardItemId==null||bingoRewardItemData==null)throw new IllegalArgumentException("Invalid bingo reward");
    }
    private static void positive(String value){
        if(value==null||!value.matches("[0-9]{1,40}")||new BigInteger(value).signum()<0)throw new IllegalArgumentException("Invalid CobbleDollars value");
    }
    public void save(){
        validate();
        try{
            Files.createDirectories(path().getParent());Path tmp=path().resolveSibling("cobbleguilds.json.tmp");
            Files.writeString(tmp,JSON.toJson(this),StandardCharsets.UTF_8);
            try{Files.move(tmp,path(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){throw new IllegalStateException("Could not save CobbleGuilds config",e);}
    }
    /** Fully validate disk input BEFORE mutating in-memory settings. */
    public void reload(){
        try{
            GuildConfig candidate=JSON.fromJson(Files.readString(path(),StandardCharsets.UTF_8),GuildConfig.class);
            if(candidate==null)throw new IllegalArgumentException("Empty config");
            candidate.validate();copyFrom(candidate);
        }catch(Exception e){throw new IllegalArgumentException("Config reload failed; runtime values were preserved: "+e.getMessage(),e);}
    }
    private void copyFrom(GuildConfig c){
        configVersion=c.configVersion;dailyFee=c.dailyFee;dailyFeePrice=c.dailyFeePrice;guildCreatePrice=c.guildCreatePrice;
        timeZone=c.timeZone;maxGuildMembers=c.maxGuildMembers;questsEnabled=c.questsEnabled;dailyQuestCount=c.dailyQuestCount;weeklyQuestCount=c.weeklyQuestCount;bingoEnabled=c.bingoEnabled;
        bingoLineExperience=c.bingoLineExperience;bingoRewardItemId=c.bingoRewardItemId;bingoRewardItemData=c.bingoRewardItemData;
        bingoRewardCount=c.bingoRewardCount;chatSpyEnabled=c.chatSpyEnabled;noHud=c.noHud;
        buffPurchasesEnabled=c.buffPurchasesEnabled;buffPrices=new LinkedHashMap<>(c.buffPrices);disabledBuffs=new LinkedHashSet<>(c.disabledBuffs);
        guildHomesEnabled=c.guildHomesEnabled;guildHomeCooldownSeconds=c.guildHomeCooldownSeconds;
        announcementsEnabled=c.announcementsEnabled;upgradesEnabled=c.upgradesEnabled;
        guildMemberUpgradeCost=c.guildMemberUpgradeCost;guildMemberUpgradeIncrement=c.guildMemberUpgradeIncrement;
        chestUpgradePrices=c.chestUpgradePrices.clone();
    }
}
