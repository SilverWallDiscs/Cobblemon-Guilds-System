package dev.silver.cobbleguilds;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

/** Server-owned guild rules. Never trust GUI clicks or command arguments as authorization. */
public final class GuildService {
    public final MinecraftServer server;
    public final GuildStore store;
    public final GuildConfig config;
    public final GuildMoney money;
    private final Set<UUID> spy = new HashSet<>();
    private int ticks;
    private String lastTickDay = "";
    public GuildService(MinecraftServer server) {
        this.server=server;this.config=GuildConfig.load();GuildQuestConfig.init();this.store=new GuildStore(server);this.money=new GuildMoney(server);
        loadSpy();
        for(Guild g:store.all()) if(!g.closed) ensureQuests(g);
        store.save();
    }
    public static void tell(ServerPlayerEntity p,String msg){p.sendMessage(Text.literal("§6[Guilds] §f"+msg),false);}
    public Guild mine(ServerPlayerEntity p){return store.member(p.getUuid());}
    public boolean isAdmin(ServerPlayerEntity p,String node){
        if(p.hasPermissionLevel(4))return true;
        try {
            Class<?> provider=Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api=provider.getMethod("get").invoke(null);
            Object mgr=api.getClass().getMethod("getUserManager").invoke(api);
            Object user=mgr.getClass().getMethod("getUser",UUID.class).invoke(mgr,p.getUuid());
            if(user==null)return false;
            Object cached=user.getClass().getMethod("getCachedData").invoke(user);
            Object permissions=cached.getClass().getMethod("getPermissionData").invoke(cached);
            for(String permission : new String[]{"cobbleguilds.admin",node}) {
                Object tri=permissions.getClass().getMethod("checkPermission",String.class).invoke(permissions,permission);
                if("TRUE".equals(String.valueOf(tri))) return true;
            }
        } catch(ReflectiveOperationException|LinkageError ignored){}
        return false;
    }
    public boolean authorized(ServerPlayerEntity p,Guild g,String permission,boolean admin){
        return (admin && isAdmin(p,"cobbleguilds.admin.manage")) || g.allowed(p.getUuid(),permission);
    }
    public boolean create(ServerPlayerEntity p,String name,String tag,String color,boolean publicGuild,String desc){
        if(mine(p)!=null){tell(p,"Leave your current guild first.");return false;}
        if(!name.matches("[A-Za-z0-9 _-]{3,24}") || !tag.matches("[A-Za-z0-9]{2,4}")){
            tell(p,"Name must be 3-24 characters; tag must be 2-4 letters/numbers.");return false;
        }
        if(store.named(name)!=null || store.named(tag)!=null || store.all().stream().anyMatch(g->!g.closed&&(g.name.equalsIgnoreCase(tag)||g.tag.equalsIgnoreCase(name)))) {
            tell(p,"That name or tag is already in use.");return false;
        }
        BigInteger price=GuildMoney.amount(config.guildCreatePrice);
        if(!money.debit(p,price)){tell(p,"Not enough CobbleDollars. Guild creation costs $"+price+".");return false;}
        Guild g=Guild.fresh(p.getUuid(),name,tag,color,publicGuild,desc);
        g.lastFeeDay=java.time.LocalDate.now(zone()).toString();
        g.addAudit(p.getName().getString()+" created guild");
        try { ensureQuests(g);store.put(g); }
        catch(RuntimeException e){money.credit(p,price);tell(p,"Creation failed; your money was refunded."); CobbleGuilds.LOG.error("Guild creation failure",e);return false;}
        tell(p,"Created §e"+name+" §ffor $"+price+".");return true;
    }
    public void join(ServerPlayerEntity p,String name){
        Guild g=store.named(name);
        if(g==null){tell(p,"Guild not found.");return;}
        if(mine(p)!=null){tell(p,"You already belong to a guild.");return;}
        String id=p.getUuidAsString();
        if(g.banned.contains(id)){tell(p,"You are banned from that guild.");return;}
        if(g.members.size()>=g.capacity(config.maxGuildMembers)){tell(p,"This guild is full.");return;}
        if(g.publicGuild || g.invitations.remove(id)){
            g.pending.remove(id);g.ordinaryInvites.remove(id);g.members.put(id,g.defaultRank());
            g.addAudit(p.getName().getString()+" joined");store.save();
            tell(p,"Joined guild §e"+g.name+"§f!");
        }else{
            g.ordinaryInvites.remove(id);g.pending.add(id);g.addAudit(p.getName().getString()+" applied");store.save();
            tell(p,"Your application to §e"+g.name+" §fwas submitted.");
        }
    }
    public void invite(ServerPlayerEntity sender,ServerPlayerEntity target){
        Guild g=mine(sender);
        if(g==null){tell(sender,"You are not in a guild.");return;}
        if(mine(target)!=null){tell(sender,"That player is already in a guild.");return;}
        if(g.banned.contains(target.getUuidAsString())){tell(sender,"This player is banned from your guild.");return;}
        if(g.allowed(sender.getUuid(),"manage_applications"))g.invitations.add(target.getUuidAsString());
        else g.ordinaryInvites.add(target.getUuidAsString());
        store.save();
        tell(target,"§e"+sender.getName().getString()+" §finvited you to §e"+g.name+"§f. Use /guild join "+g.name);
        tell(sender,"Guild invitation sent.");
    }
    public void accept(ServerPlayerEntity moderator,ServerPlayerEntity target){
        Guild g=mine(moderator);
        if(g==null||!g.allowed(moderator.getUuid(),"manage_applications")){tell(moderator,"You cannot manage applications.");return;}
        if(!g.pending.contains(target.getUuidAsString())){tell(moderator,"No pending application for this player.");return;}
        if(mine(target)!=null || g.members.size()>=g.capacity(config.maxGuildMembers)){tell(moderator,"The player cannot join.");return;}
        g.pending.remove(target.getUuidAsString());g.members.put(target.getUuidAsString(),g.defaultRank());
        g.addAudit(moderator.getName().getString()+" approved "+target.getName().getString());store.save();
        tell(target,"Your application to "+g.name+" was accepted.");tell(moderator,"Application accepted.");
    }
    public void leave(ServerPlayerEntity p){
        Guild g=mine(p);
        if(g==null){tell(p,"You are not in a guild.");return;}
        if(g.isLeader(p.getUuid())){tell(p,"Transfer leadership or disband your guild first.");return;}
        g.members.remove(p.getUuidAsString());g.addAudit(p.getName().getString()+" left");store.save();tell(p,"You left the guild.");
    }
    public void disband(ServerPlayerEntity p,boolean admin,Guild g){
        if(g==null){tell(p,"Guild not found.");return;}
        if(!admin&&!g.isLeader(p.getUuid())){tell(p,"Only the guild leader can disband it.");return;}
        g.closed=true;g.addAudit(p.getName().getString()+" disbanded guild. Chest and funds preserved for administrative recovery.");store.save();
        tell(p,"Guild disbanded. Remaining bank and chest contents are preserved for admin recovery.");
    }
    public void bankDeposit(ServerPlayerEntity p,BigInteger amount){
        Guild g=mine(p);
        if(g==null){tell(p,"You are not in a guild.");return;}
        if(amount.signum()<=0 || amount.bitLength()>256){tell(p,"Invalid deposit amount.");return;}
        if(!money.debit(p,amount)){tell(p,"Invalid amount or insufficient CobbleDollars.");return;}
        BigInteger previous=g.funds();
        try {g.bank=previous.add(amount).toString();g.addAudit(p.getName().getString()+" deposited $"+amount);
            contribute(g,p,"deposit",amount.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue());store.save();}
        catch(RuntimeException ex){g.bank=previous.toString();money.credit(p,amount);throw ex;}
        tell(p,"Deposited $"+amount+". Guild bank: $"+g.bank);
    }
    public void bankWithdraw(ServerPlayerEntity p,BigInteger amount,boolean admin,Guild g){
        if(g==null){tell(p,"Guild not found.");return;}
        if(!(admin && isAdmin(p,"cobbleguilds.admin.bank")) && !authorized(p,g,"bank_withdraw",admin)){
            tell(p,"No bank withdrawal permission.");return;
        }
        BigInteger previous=g.funds();
        if(amount.signum()<=0||amount.bitLength()>256||previous.compareTo(amount)<0){tell(p,"Insufficient guild funds.");return;}
        g.bank=previous.subtract(amount).toString();
        try {store.save();money.credit(p,amount);g.addAudit(p.getName().getString()+" withdrew $"+amount);store.save();}
        catch(RuntimeException ex){CobbleGuilds.LOG.error("Bank withdrawal reconciliation required for guild {}",g.id,ex);throw ex;}
        tell(p,"Withdrew $"+amount+". Guild bank: $"+g.bank);
    }
    public void upgradeChest(ServerPlayerEntity p){
        Guild g=mine(p);
        if(g==null || !g.allowed(p.getUuid(),"upgrade_chest")){tell(p,"You cannot upgrade the guild chest.");return;}
        if(g.chestRows>=6){tell(p,"Guild chest is already at maximum size.");return;}
        int tier=g.chestRows-1;
        if(tier>=config.chestUpgradePrices.length){tell(p,"Upgrade price is not configured.");return;}
        BigInteger cost=GuildMoney.amount(config.chestUpgradePrices[tier]);
        if(g.funds().compareTo(cost)<0){tell(p,"Guild bank needs $"+cost+" for this upgrade.");return;}
        g.bank=g.funds().subtract(cost).toString();g.chestRows++;
        g.addAudit(p.getName().getString()+" upgraded guild chest to "+(g.chestRows*9)+" slots");store.save();
        tell(p,"Guild chest upgraded to "+(g.chestRows*9)+" slots.");
    }
    public void sendGuildChat(ServerPlayerEntity p,String message){
        Guild g=mine(p);
        if(g==null){tell(p,"You are not in a guild.");return;}
        String plain=message.replace('§',' ').trim();
        if(plain.isEmpty())return;
        String line="§7[Guild] "+g.color+"["+g.tag+"] §f"+p.getName().getString()+": "+plain;
        for(ServerPlayerEntity recipient:server.getPlayerManager().getPlayerList()){
            if(g.contains(recipient.getUuid()))recipient.sendMessage(Text.literal(line),false);
            else if(spy.contains(recipient.getUuid()) && isAdmin(recipient,"cobbleguilds.admin.chatspy"))
                recipient.sendMessage(Text.literal("§8[GuildSpy] "+g.color+"["+g.tag+"] §f"+p.getName().getString()+": "+plain),false);
        }
    }
    public void chatSpy(ServerPlayerEntity p){
        if(!config.chatSpyEnabled || !isAdmin(p,"cobbleguilds.admin.chatspy")){
            tell(p,"You do not have Chat Spy permission.");return;
        }
        boolean enabled;
        if(spy.contains(p.getUuid())){spy.remove(p.getUuid());enabled=false;}else{spy.add(p.getUuid());enabled=true;}
        saveSpy();
        CobbleGuilds.LOG.info("[GuildSpy] {} ({}) {} spy",p.getGameProfile().getName(),p.getUuid(),enabled?"enabled":"disabled");
        tell(p,enabled?"Guild Chat Spy enabled. You can now monitor guild conversations.":"Guild Chat Spy disabled. You will no longer receive guild messages.");
    }
    private void loadSpy(){
        try {
            var path=store.root().resolve("spy.json");
            if(java.nio.file.Files.exists(path)){
                String[] entries=GuildConfig.JSON.fromJson(java.nio.file.Files.readString(path),String[].class);
                if(entries!=null)for(String s:entries)try{spy.add(UUID.fromString(s));}catch(IllegalArgumentException ignored){}
            }
        }catch(Exception e){CobbleGuilds.LOG.warn("Failed to load ChatSpy settings",e);}
    }
    private void saveSpy(){
        try {java.nio.file.Files.writeString(store.root().resolve("spy.json"),GuildConfig.JSON.toJson(spy.stream().map(UUID::toString).toList()));}
        catch(Exception e){CobbleGuilds.LOG.warn("Failed to save ChatSpy settings",e);}
    }
    public void ensureQuests(Guild g){
        if(!config.questsEnabled || g.closed)return;
        var now=ZonedDateTime.now(zone());
        String day=now.toLocalDate().toString();
        String week=now.get(WeekFields.ISO.weekBasedYear())+"-W"+now.get(WeekFields.ISO.weekOfWeekBasedYear());
        long dailyEnd=now.toLocalDate().plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli();
        long weeklyEnd=now.toLocalDate().plusDays(8-now.getDayOfWeek().getValue()).atStartOfDay(zone()).toInstant().toEpochMilli();
        QuestCatalog.refresh(g, day, week, dailyEnd, weeklyEnd);
        g.quests.entrySet().removeIf(e -> !e.getValue().completed && e.getValue().expiresAt < System.currentTimeMillis() - 172800000L);
        // Completed quests are retained for 7 days after expiry so returning contributors can claim.
        g.quests.entrySet().removeIf(e -> e.getValue().completed && e.getValue().expiresAt < System.currentTimeMillis() - 604800000L);
    }
    /** Capture events must originate from CobblemonEvents.POKEMON_CAPTURED, not from player commands. */
    public void captured(ServerPlayerEntity p,String species,boolean shiny,String primaryType,String secondaryType){
        Guild g=mine(p);
        if(g==null)return;
        contributeCapture(g,p,species,shiny,primaryType,secondaryType);
    }
    public void contributeCapture(Guild g,ServerPlayerEntity player,String species,boolean shiny,String primaryType,String secondaryType){
        if(!config.questsEnabled || g.closed || !g.contains(player.getUuid()))return;
        ensureQuests(g);
        long now=System.currentTimeMillis();boolean changed=false;
        for(Guild.Quest q:g.quests.values()){
            if(q.completed || q.expiresAt<=now || q.progress()>=q.target)continue;
            boolean accepted=switch(q.kind==null?"":q.kind){
                case "capture"->true;
                case "shiny_capture"->shiny;
                case "type_capture"->q.qualifier!=null && (q.qualifier.equals(primaryType)||q.qualifier.equals(secondaryType));
                case "unique_capture"->q.uniqueSpecies!=null && q.uniqueSpecies.add(species);
                default ->false;
            };
            if(!accepted)continue;
            int old=q.amount(player.getUuid());
            q.contributions.put(player.getUuidAsString(), Math.min(Integer.MAX_VALUE,old+1));
            changed=true;
            if(q.progress()>=q.target)complete(g,q);
        }
        if(changed)store.save();
    }
    public void contribute(Guild g, ServerPlayerEntity player, String kind, int amount){
        if(g==null || g.closed || !config.questsEnabled || amount<=0 || !g.contains(player.getUuid())) return;
        ensureQuests(g);
        long now=System.currentTimeMillis();
        boolean changed=false;
        for(Guild.Quest q:g.quests.values()){
            if(q.completed || q.expiresAt<=now || !kind.equals(q.kind) || q.progress()>=q.target)continue;
            int effective=amount;
            if("deposit".equals(kind)){
                BigInteger highest;
                try{highest=new BigInteger(q.highestBankFunds);}catch(RuntimeException ex){highest=BigInteger.ZERO;}
                BigInteger current=g.funds();
                if(current.compareTo(highest)<=0)continue; // Withdraw/redeposit cannot recycle already credited money.
                effective=current.subtract(highest).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
                q.highestBankFunds=current.toString();
            }
            int old=q.amount(player.getUuid());
            q.contributions.put(player.getUuidAsString(), (int)Math.min(Integer.MAX_VALUE, (long)old+effective));changed=true;
            if(q.progress()>=q.target)complete(g,q);
        }
        if(changed)store.save();
    }
    private void complete(Guild g,Guild.Quest q){
        q.completed=true;
        g.experience=Math.min(Long.MAX_VALUE-100000,g.experience+q.target);
        g.level=1+(int)Math.min(9999,g.experience/1000);
        if(q.id.startsWith("day-") && !java.time.LocalDate.now(zone()).toString().equals(g.lastCompletedDailyQuestDay)){
            g.questStreak++;g.lastCompletedDailyQuestDay=java.time.LocalDate.now(zone()).toString();
        }
        for(ServerPlayerEntity member:server.getPlayerManager().getPlayerList())
            if(g.contains(member.getUuid()))tell(member,"Your guild completed "+q.name+"! Claim rewards with /guild quest claim.");
        g.addAudit("Completed quest "+q.id);
    }
    public void listQuests(ServerPlayerEntity p){
        Guild g=mine(p);if(g==null){tell(p,"You are not in a guild.");return;}
        ensureQuests(g);
        tell(p,"Guild Quests (no HUD):");
        for(Guild.Quest q:g.quests.values()){
            if(!q.completed && q.expiresAt<System.currentTimeMillis())continue;
            if(q.completed && q.claimed.contains(p.getUuidAsString()))continue;
            tell(p,q.name+" ["+q.progress()+"/"+q.target+"] Yours: "+q.amount(p.getUuid())+"/"+q.minimum+" | "+(q.completed?"Completed":"In Progress"));
        }
    }
    public void claim(ServerPlayerEntity p){
        Guild g=mine(p);if(g==null){tell(p,"You are not in a guild.");return;}
        boolean paid=false;
        Item rare=Registries.ITEM.get(Identifier.of("cobblemon","rare_candy"));
        if(rare==net.minecraft.item.Items.AIR){tell(p,"Rare Candy item not found; rewards kept pending.");return;}
        for(Guild.Quest q:g.quests.values()){
            if(!q.eligible(p.getUuid()))continue;
            ItemStack candy=new ItemStack(rare,q.candies);
            if(!canFit(p,candy) || !p.getInventory().insertStack(candy)){
                tell(p,"Make room in your inventory before claiming rewards.");break;
            }
            // Claimed state recorded before the optional currency bonus.
            q.claimed.add(p.getUuidAsString());store.save();
            List<Map.Entry<String,Integer>> rank=q.contributions.entrySet().stream()
                .sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).toList();
            int place=-1;
            for(int i=0;i<rank.size();i++)if(rank.get(i).getKey().equals(p.getUuidAsString())){place=i;break;}
            int bonus=place==0?q.extraMoneyForFirst:place==1?q.extraMoneyForSecond:place==2?q.extraMoneyForThird:0;
            if(bonus>0)money.credit(p,BigInteger.valueOf(bonus));
            tell(p,"Claimed "+q.candies+" Rare Candies"+(bonus>0?" and $"+bonus+" top contributor bonus":"")+" for "+q.name+"!");
            paid=true;
        }
        if(!paid)tell(p,"No unclaimed quest rewards available.");
    }
    private static boolean canFit(ServerPlayerEntity p,ItemStack reward){
        int capacity=0;
        for(int i=0;i<p.getInventory().size();i++){
            ItemStack existing=p.getInventory().getStack(i);
            if(existing.isEmpty())capacity+=reward.getMaxCount();
            else if(ItemStack.areItemsAndComponentsEqual(existing,reward))capacity+=Math.max(0,Math.min(existing.getMaxCount(),p.getInventory().getMaxCountPerStack())-existing.getCount());
            if(capacity>=reward.getCount())return true;
        }
        return false;
    }
    public void onTick(){
        if(++ticks%20!=0)return;
        ZonedDateTime now=ZonedDateTime.now(zone());
        String day=now.toLocalDate().toString();
        if(!day.equals(lastTickDay)){
            lastTickDay=day;
            for(Guild g:store.all())if(!g.closed)ensureQuests(g);
            store.save();
        }
        if(!config.dailyFee)return;
        LocalTime local=now.toLocalTime();
        int minutesToMidnight=24*60-(local.getHour()*60+local.getMinute());
        if(store.all().stream().anyMatch(g->!g.closed && !day.equals(g.lastFeeDay))){
            boolean anyClosed=false;
            BigInteger fee=GuildMoney.amount(config.dailyFeePrice);
            for(Guild g:store.all()){
                if(g.closed || day.equals(g.lastFeeDay))continue;
                g.lastFeeDay=day;
                if(g.funds().compareTo(fee)>=0){g.bank=g.funds().subtract(fee).toString();g.addAudit("Gholdengo collected daily fee $"+fee);}
                else {g.closed=true;anyClosed=true;g.addAudit("Gholdengo disbanded guild; funds and chest archived for admin recovery");}
            }
            store.save();
            if(anyClosed)server.getPlayerManager().broadcast(Text.literal("§6Gholdengo has finished collecting the daily guild maintenance fees! All guilds that failed to pay have now been disbanded."),false);
            else server.getPlayerManager().broadcast(Text.literal("§6Gholdengo has finished collecting the daily guild maintenance fees! All active guilds have paid."),false);
        } else if(minutesToMidnight==10 || minutesToMidnight==5 || minutesToMidnight==1){
            String marker=day+":"+minutesToMidnight;
            if(!marker.equals(lastWarning)){
                lastWarning=marker;
                server.getPlayerManager().broadcast(Text.literal("§6Gholdengo is about to come around to collect the daily guild maintenance fees! Make sure you have the exact amount of CobbleDollars ready! ("+minutesToMidnight+" min remaining)"),false);
            }
        }
    }
    private String lastWarning="";
    public ZoneId zone(){try{return "system".equalsIgnoreCase(config.timeZone)?ZoneId.systemDefault():ZoneId.of(config.timeZone);}catch(Exception e){return ZoneId.systemDefault();}}
}
