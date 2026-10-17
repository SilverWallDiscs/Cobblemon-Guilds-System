package dev.silver.cobbleguilds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class GuildConfig {
    public boolean dailyFee = true;
    public String dailyFeePrice = "10000";
    public String guildCreatePrice = "1000000";
    public String timeZone = "system";
    public int maxGuildMembers = 10;
    public boolean questsEnabled = true;
    public boolean chatSpyEnabled = true;
    public boolean noHud = true;
    public boolean guildHomesEnabled = true;
    public int guildHomeCooldownSeconds = 120;
    public boolean announcementsEnabled = true;
    public boolean upgradesEnabled = true;
    public int guildMemberUpgradeCost = 1500000;
    public int guildMemberUpgradeIncrement = 5;
    public String[] chestUpgradePrices = {"100000","500000","1000000","3000000","5000000"};
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static GuildConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cobbleguilds.json");
        try {
            if (Files.exists(path)) {
                GuildConfig read = JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), GuildConfig.class);
                if (read != null) return read;
            }
            GuildConfig result = new GuildConfig();
            Files.createDirectories(path.getParent());
            Files.writeString(path,JSON.toJson(result),StandardCharsets.UTF_8);
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Could not read CobbleGuilds config: "+path, ex); }
    }
}
