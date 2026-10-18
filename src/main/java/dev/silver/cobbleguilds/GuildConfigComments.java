package dev.silver.cobbleguilds;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides actual # comments directly inside the config files.
 * They are stripped before JSON parsing by GuildHashComments.
 */
public final class GuildConfigComments {
    private GuildConfigComments() {}

    private static final Map<String,String> SERVER_HELP = new LinkedHashMap<>();
    private static final Map<String,String> QUEST_HELP = new LinkedHashMap<>();
    static {
        SERVER_HELP.put("configVersion", "Internal migration version. Leave this unchanged.");
        SERVER_HELP.put("dailyFee", "true charges each active guild daily maintenance; false disables the maintenance charge.");
        SERVER_HELP.put("dailyFeePrice", "Daily maintenance price in CobbleDollars, as a quoted whole number (e.g. \"10000\"). Used only when dailyFee is true.");
        SERVER_HELP.put("guildCreatePrice", "CobbleDollars deducted from the player when they create a guild, quoted as a whole number.");
        SERVER_HELP.put("timeZone", "Use \"system\" for the server's timezone or an IANA zone like \"America/Santiago\"; sets daily/weekly reset dates.");
        SERVER_HELP.put("maxGuildMembers", "Maximum initial guild size, from 1 to 100. Capacity upgrades may increase it per guild.");
        SERVER_HELP.put("questsEnabled", "true enables daily and weekly quests; false stops quest progression/rewards.");
        SERVER_HELP.put("dailyQuestCount", "How many daily quests appear in each new rotation: 1 to 5. At least one daily template must be enabled.");
        SERVER_HELP.put("weeklyQuestCount", "How many weekly quests appear in each new rotation: 1 to 3. At least one weekly template must be enabled.");
        SERVER_HELP.put("bingoEnabled", "true enables the cooperative weekly 3x3 guild Bingo; false disables it.");
        SERVER_HELP.put("bingoLineExperience", "Guild XP per completed Bingo line: 0 to 500, independent of item rewards.");
        SERVER_HELP.put("bingoRewardItemId", "Fallback registry ID for the Bingo reward item, e.g. \"minecraft:diamond\"; changing the actual item is best done via /guild config.");
        SERVER_HELP.put("bingoRewardItemData", "Compressed Base64 item NBT including modded components. Usually edit via /guild config, not manually; empty means use the ID above.");
        SERVER_HELP.put("bingoRewardCount", "Number of items awarded for a Bingo reward: 1 to 64, subject to that item's maximum stack size.");
        SERVER_HELP.put("chatSpyEnabled", "Allow authorized administrators using ChatSpy to see /g and /ga messages. false disables spying.");
        SERVER_HELP.put("noHud", "Legacy presentation preference: guild information uses chest menus and commands, not an always-visible HUD.");
        SERVER_HELP.put("buffPurchasesEnabled", "true enables purchasing implemented guild buffs with guild-bank money; false hides/disables purchases.");
        SERVER_HELP.put("buffPrices", "Override individual buff prices in CobbleDollars. Example: {\"catch_x2\":\"12000000\"}. Empty {} uses the default prices. Supported IDs: SUPPORTED_BUFF_IDS.");
        SERVER_HELP.put("disabledBuffs", "Buff IDs to hide from new purchases; example [\"friendship_x2\"]. Empty [] enables all available buffs. Existing active effects are not cancelled.");
        SERVER_HELP.put("guildHomesEnabled", "true permits using guild homes and their teleport controls.");
        SERVER_HELP.put("guildHomeCooldownSeconds", "How long a player waits between guild-home teleports: 0 to 86400 seconds.");
        SERVER_HELP.put("announcementsEnabled", "Enable or disable guild announcements.");
        SERVER_HELP.put("upgradesEnabled", "Enable or disable paid guild upgrades.");
        SERVER_HELP.put("guildMemberUpgradeCost", "Cost in guild-bank CobbleDollars to purchase a member-capacity upgrade.");
        SERVER_HELP.put("guildMemberUpgradeIncrement", "Extra member slots gained per capacity upgrade.");
        SERVER_HELP.put("chestUpgradePrices", "Five ordered CobbleDollar prices for upgrading the shared guild chest from 1 to 6 rows; do not remove tiers.");

        QUEST_HELP.put("name", "Player-facing quest name (1-48 characters). Use a unique, descriptive title.");
        QUEST_HELP.put("kind", "Objective: capture (any), shiny_capture, type_capture, unique_capture, species_capture, or deposit (CobbleDollars). These are the supported kinds.");
        QUEST_HELP.put("qualifier", "For type_capture enter a lowercase type, e.g. \"water\"; for species_capture enter a lowercase species, e.g. \"magikarp\". Other kinds can use \"\".");
        QUEST_HELP.put("cadence", "\"daily\" or \"weekly\": determines the rotation this quest may appear in.");
        QUEST_HELP.put("target", "Total guild-wide amount required to complete this quest. Whole number from 1 to 200000000.");
        QUEST_HELP.put("minimum", "Individual contribution required before a member may claim. Must be 1 to target.");
        QUEST_HELP.put("candies", "Old-version Rare Candy reward count; kept for migration/fallback. For current rewards, use rewardItemId/rewardCount and the admin GUI.");
        QUEST_HELP.put("rewardItemId", "Registry ID, e.g. \"minecraft:diamond\" or \"cobblemon:rare_candy\". For full modded NBT, select a real item in /guild config.");
        QUEST_HELP.put("rewardItemData", "Compressed Base64 ItemStack data (may contain modded components). Auto-generated from an admin-selected item; don't edit by hand.");
        QUEST_HELP.put("rewardCount", "Reward item quantity from 1 to 64; must fit the selected item's maximum stack size.");
        QUEST_HELP.put("enabled", "true allows this quest in new rotations; false excludes it. Keep at least one enabled daily and one weekly quest.");
    }

    /** Attach field comments to Gson's pretty-printed JSON without changing values. */
    private static String annotate(String json,Map<String,String> help,String heading){
        StringBuilder result=new StringBuilder();
        result.append("# ").append(heading).append('\n');
        result.append("# Edit the JSON values below. Lines starting with # are explanations.\n");
        result.append("# CobbleGuilds accepts hash comments; strict JSON tools do not.\n");
        for(String line:json.split("\n",-1)){
            String trimmed=line.stripLeading();
            if(trimmed.startsWith("\"")){
                int end=trimmed.indexOf('"',1);
                if(end>1 && trimmed.substring(end+1).stripLeading().startsWith(":")){
                    String note=help.get(trimmed.substring(1,end));
                    if(note!=null){
                        String indent=line.substring(0,line.length()-trimmed.length());
                        for(String explanation:note.split("\n"))result.append(indent).append("# ").append(explanation).append('\n');
                    }
                }
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }

    public static String server(GuildConfig config){
        String ids=GuildBuffs.OFFERS.stream().map(GuildBuffs.Offer::id).collect(Collectors.joining(", "));
        Map<String,String> notes=new LinkedHashMap<>(SERVER_HELP);
        notes.replaceAll((k,v)->v.replace("SUPPORTED_BUFF_IDS",ids));
        return annotate(GuildConfig.JSON.toJson(config),notes,"CobbleGuilds server configuration");
    }

    public static String quests(GuildQuestConfig config){
        Map<String,String> notes=new LinkedHashMap<>(QUEST_HELP);
        notes.put("quests","Daily and weekly quest templates. Each includes its own objective and reward.\nUse /guild config to select custom modded ItemStacks.");
        return annotate(GuildConfig.JSON.toJson(config),notes,"CobbleGuilds quest templates");
    }
}
