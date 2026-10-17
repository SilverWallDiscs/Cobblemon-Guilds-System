package dev.silver.cobbleguilds;

import com.mojang.authlib.GameProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.math.BigInteger;
import java.util.*;

/** Fully vanilla dedicated-server chest menus; chat input is intercepted privately. */
public final class GuildGui {
    private GuildGui(){}
    private static final Map<UUID,Draft> drafts=new HashMap<>();
    public static Draft draft(ServerPlayerEntity p){return drafts.computeIfAbsent(p.getUuid(),id->new Draft());}
    public static final class Draft {
        public String name="",tag="",color="§7",icon="minecraft:name_tag",description="";
        public boolean publicGuild=true;
    }
    public static ItemStack item(Item material,String title,String... lines){
        ItemStack stack=new ItemStack(material);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(title));
        if(lines.length>0)stack.set(DataComponentTypes.LORE,new LoreComponent(Arrays.stream(lines).<Text>map(Text::literal).toList()));
        return stack;
    }
    private static ItemStack head(Guild guild,String id,GuildService svc){
        UUID uuid=UUID.fromString(id);
        ServerPlayerEntity online=svc.server.getPlayerManager().getPlayer(uuid);
        String name=online==null?guild.memberNames.getOrDefault(id,"Unknown Player"):online.getGameProfile().getName();
        ItemStack stack=item(Items.PLAYER_HEAD,"§b"+name,"§7Rank: "+guild.ranks.getOrDefault(guild.members.get(id),new Guild.Rank("Unknown",0,false)).name,
                online==null?"§7CobbleDollars: offline":"§6CobbleDollars: $"+svc.money.balance(online),"§eClick to manage member");
        // A real, signed online GameProfile provides a player skin to vanilla clients.
        stack.set(DataComponentTypes.PROFILE,new ProfileComponent(online!=null?online.getGameProfile():new GameProfile(uuid,name)));
        return stack;
    }
    private static Item material(String id){try{Item i=Registries.ITEM.get(Identifier.of(id));return i==Items.AIR?Items.NAME_TAG:i;}catch(Exception e){return Items.NAME_TAG;}}
    public static ItemStack guildIcon(Guild g){return item(material(g.icon),g.color+g.name,"§7["+g.tag+"]","§7Leader: "+g.memberNames.getOrDefault(g.leader,"Unknown"),"§7Members: "+g.members.size(),"§7"+g.description,"§eClick for details");}
    public static void start(ServerPlayerEntity player,GuildService svc){
        Guild own=svc.mine(player);if(own!=null){guild(player,svc,own,false);return;}
        Menu menu=new Menu(1);
        menu.slot(2,item(Items.COMPASS,"§bBrowse Guilds","§7Choose a guild to join or apply"),(p,c)->browse(p,svc,0,false));
        menu.slot(6,item(Items.EMERALD,"§aCreate Guild","§7Price: $"+svc.config.guildCreatePrice),(p,c)->create(p,svc));
        menu.open(player,"CobbleGuilds");
    }
    public static void create(ServerPlayerEntity player,GuildService svc){
        if(svc.mine(player)!=null){start(player,svc);return;}
        Draft d=draft(player);Menu m=new Menu(4);
        m.slot(10,item(Items.PAPER,"§eName: "+d.name,"§7Click and enter name privately in chat","§7Length: 3-24 characters"),(p,c)->ask(p,"a guild name (3-24 characters)",(pl,value)->{
            if(!value.matches("[A-Za-z0-9 _-]{3,24}")){GuildService.tell(pl,"Invalid name (3-24 letters, digits, spaces, _ or -).");create(pl,svc);return;}
            d.name=value;create(pl,svc);
        }));
        m.slot(11,item(Items.NAME_TAG,"§eTag: "+d.tag,"§7Click and enter 2-4 letters/digits"),(p,c)->ask(p,"a guild tag (2-4 letters/digits)",(pl,value)->{
            if(!value.matches("[A-Za-z0-9]{2,4}")){GuildService.tell(pl,"Invalid guild tag.");create(pl,svc);return;}
            d.tag=value;create(pl,svc);
        }));
        m.slot(13,item(material(d.icon),"§eGuild Icon","§7Pick up an item on your cursor","§7Click here to copy its appearance","§7Your item will not be consumed"),(p,c)->{
            if(c.isEmpty()){GuildService.tell(p,"Hold any item on the cursor first.");return;}
            d.icon=Registries.ITEM.getId(c.getItem()).toString();create(p,svc);
        });
        m.slot(15,item(Items.REDSTONE,"§eGuild Color: "+d.color+"Example","§7Click to choose a color"),(p,c)->colors(p,svc,null,false));
        m.slot(16,item(d.publicGuild?Items.OAK_DOOR:Items.IRON_DOOR,d.publicGuild?"§aPublic Guild":"§eApplications Required","§7Click to toggle"),(p,c)->{d.publicGuild=!d.publicGuild;create(p,svc);});
        m.slot(22,item(Items.WRITABLE_BOOK,"§eDescription","§7Click and type in chat (up to 220 chars)","§7"+d.description),(p,c)->ask(p,"a guild description (max 220 characters)",(pl,value)->{
            if(value.length()>220){GuildService.tell(pl,"Description is too long.");create(pl,svc);return;}
            d.description=value;create(pl,svc);
        }));
        m.slot(31,item(Items.EMERALD_BLOCK,"§aCreate Guild","§7Costs $"+svc.config.guildCreatePrice,"§7No charge if validation fails"),(p,c)->{
            if(svc.create(p,d.name,d.tag,d.color,d.publicGuild,d.description)){
                Guild g=svc.mine(p);g.icon=d.icon;svc.store.save();drafts.remove(p.getUuid());guild(p,svc,g,false);
            }else create(p,svc);
        });
        m.slot(35,item(Items.ARROW,"§7Back"),(p,c)->start(p,svc));m.open(player,"Create Guild");
    }
    private static void colors(ServerPlayerEntity player,GuildService svc,Guild guild,boolean admin){
        String[] codes={"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        Item[] wool={Items.BLACK_WOOL,Items.BLUE_WOOL,Items.GREEN_WOOL,Items.CYAN_TERRACOTTA,Items.RED_WOOL,Items.PURPLE_WOOL,Items.ORANGE_WOOL,Items.LIGHT_GRAY_WOOL,Items.GRAY_WOOL,Items.LIGHT_BLUE_WOOL,Items.LIME_WOOL,Items.CYAN_WOOL,Items.RED_CONCRETE,Items.MAGENTA_WOOL,Items.YELLOW_WOOL,Items.WHITE_WOOL};
        Menu m=new Menu(3);
        for(int i=0;i<16;i++){
            String code="§"+codes[i];m.slot(i,item(wool[i],code+"Color &"+codes[i]),(p,c)->{
                if(guild==null){draft(p).color=code;create(p,svc);}else if(can(p,svc,guild,"manage_settings",admin)){
                    guild.color=code;guild.addAudit(p.getUuid()+" changed guild color");svc.store.save();settings(p,svc,guild,admin);
                }
            });
        }
        m.slot(22,item(Items.ARROW,"§7Back"),(p,c)->{if(guild==null)create(p,svc);else settings(p,svc,guild,admin);});
        m.open(player,"Choose Guild Color");
    }
    private static void ask(ServerPlayerEntity player,String question,java.util.function.BiConsumer<ServerPlayerEntity,String> action){GuildChatInput.ask(player,question,action);}
    private static boolean can(ServerPlayerEntity p,GuildService svc,Guild g,String permission,boolean admin){
        if(admin && svc.isAdmin(p,"cobbleguilds.admin.manage"))return true;
        if(g.allowed(p.getUuid(),permission))return true;
        GuildService.tell(p,"You do not have permission to perform this action.");return false;
    }
    public static void browse(ServerPlayerEntity player,GuildService svc,int page,boolean admin){
        if(admin&&!svc.isAdmin(player,"cobbleguilds.admin.gui")){GuildService.tell(player,"Access denied.");return;}
        List<Guild> list=svc.store.all().stream().filter(g->admin||!g.closed).sorted(Comparator.comparing(g->g.name.toLowerCase(Locale.ROOT))).toList();
        Menu m=new Menu(6);int from=Math.max(0,page)*45;
        for(int i=from;i<Math.min(list.size(),from+45);i++){
            Guild g=list.get(i);m.slot(i-from,guildIcon(g),(p,c)->{if(admin)guild(p,svc,g,true);else preview(p,svc,g);});
        }
        if(page>0)m.slot(45,item(Items.ARROW,"Previous Page"),(p,c)->browse(p,svc,page-1,admin));
        if(from+45<list.size())m.slot(53,item(Items.ARROW,"Next Page"),(p,c)->browse(p,svc,page+1,admin));
        m.slot(49,item(Items.BARRIER,"§7Back"),(p,c)->start(p,svc));
        m.open(player,admin?"Admin · All Guilds":"Browse Guilds");
    }
    private static void preview(ServerPlayerEntity p,GuildService svc,Guild g){
        Menu m=new Menu(3);
        m.slot(11,guildIcon(g),(pl,c)->{});
        m.slot(15,item(g.publicGuild?Items.EMERALD:Items.PAPER,g.publicGuild?"§aJoin Guild":"§eApply to Guild","§7Click to confirm"),(pl,c)->{
            svc.join(pl,g.name);if(svc.mine(pl)!=null)guild(pl,svc,svc.mine(pl),false);else start(pl,svc);
        });
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->browse(pl,svc,0,false));m.open(p,"Guild · "+g.name);
    }
    public static void guild(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(g==null){start(p,svc);return;}
        if(admin){if(!svc.isAdmin(p,"cobbleguilds.admin.gui")){GuildService.tell(p,"Access denied.");return;}}
        else if(!g.contains(p.getUuid())){GuildService.tell(p,"Not a guild member.");return;}
        Menu m=new Menu(6);
        m.slot(4,item(material(g.icon),g.color+g.name,"§7Leader: §e"+g.memberNames.getOrDefault(g.leader,"Unknown"),"§7Tag: "+g.tag,"§7Level: "+g.level,"§7Members: "+g.members.size(),"§7"+g.description), (pl,c)->settings(pl,svc,g,admin));
        m.slot(10,item(Items.PLAYER_HEAD,"§bMembers","§7Manage members and roles"),(pl,c)->members(pl,svc,g,admin,0));
        m.slot(11,item(Items.GOLD_INGOT,"§6Guild Bank","§7Balance: $"+g.bank,"§7Click to deposit/withdraw"),(pl,c)->bank(pl,svc,g,admin));
        m.slot(12,item(Items.CHEST,"§6Guild Chest","§7"+(g.chestRows*9)+" slots"),(pl,c)->GuildMenus.openChest(pl,svc,g,admin));
        m.slot(13,item(Items.ENCHANTED_BOOK,"§dGuild Buffs","§7Shop and active effects"),(pl,c)->buffs(pl,svc,g,admin));
        m.slot(14,item(Items.BOOK,"§aGuild Quests","§7Daily and weekly shared challenges"),(pl,c)->quests(pl,svc,g,admin,0));
        m.slot(15,item(Items.IRON_SWORD,"§fGuild Ranks","§7Configure roles and permissions"),(pl,c)->ranks(pl,svc,g,admin));
        m.slot(16,item(Items.WRITABLE_BOOK,"§eApplications","§7Pending: "+g.pending.size()),(pl,c)->pending(pl,svc,g,admin));
        m.slot(19,item(Items.EXPERIENCE_BOTTLE,"§aLevel "+g.level,"§7XP: "+g.experience+" / "+GuildLevels.threshold(Math.min(10_000,g.level+1)),"§7Increasing XP per level"),(pl,c)->experience(pl,svc,g,admin));
        m.slot(20,item(Items.EMERALD_BLOCK,"§aGuild Upgrades","§7Chest and member capacity"),(pl,c)->upgrades(pl,svc,g,admin));
        m.slot(21,item(Items.COMPASS,"§bGuild Home","§7Click to teleport or set home"),(pl,c)->home(pl,svc,g,admin));
        m.slot(22,item(Items.WRITABLE_BOOK,"§eGuild Announcement","§7"+(g.announcement.isBlank()?"No announcement":g.announcement),"§7Click to read or edit"),(pl,c)->announcement(pl,svc,g,admin));
        m.slot(23,item(Items.MAP,"§dGuild Bingo","§7Weekly cooperative 3x3 board"),(pl,c)->bingo(pl,svc,g,admin));
        m.slot(24,item(Items.TOTEM_OF_UNDYING,"§bAlliances","§7Allied guilds: "+g.allies.size()),(pl,c)->alliances(pl,svc,g,admin));
        m.slot(25,item(Items.DIAMOND,"§eWeekly Guild Challenge","§7Competition scoreboard"),(pl,c)->challenge(pl,svc,g,admin));
        m.slot(28,item(Items.NETHER_STAR,"§6Guild Achievements","§7Unlocked: "+g.achievements.size()),(pl,c)->achievements(pl,svc,g,admin));
        m.slot(29,item(Items.AMETHYST_SHARD,"§dGuild Milestones","§7Long-term objectives"),(pl,c)->milestones(pl,svc,g,admin));
        m.slot(30,item(Items.PAPER,"§aGuild Bounties","§7Capture species for quests"),(pl,c)->bounties(pl,svc,g,admin));
        m.slot(31,item(Items.CLOCK,"§fGuild Activity Logs","§7Recent activity"),(pl,c)->logs(pl,svc,g,admin));
        m.slot(32,item(Items.NAME_TAG,"§eGuild Settings","§7Customize name, tag, description"),(pl,c)->settings(pl,svc,g,admin));
        m.slot(33,item(Items.BELL,"§bInvite a Player","§7Click and type their online username"),(pl,c)->invite(pl,svc,g,admin));
        if(!admin){
            m.slot(38,item(Items.WRITABLE_BOOK,"§aSend Guild Chat","§7Click and type a private guild message"),(pl,c)->ask(pl,"a guild chat message",(a,value)->{
                if(!value.isBlank()&&value.length()<=250)svc.sendGuildChat(a,value);guild(a,svc,g,false);
            }));
            m.slot(39,item(Items.WRITABLE_BOOK,"§bSend Alliance Chat","§7Click and type an alliance message"),(pl,c)->ask(pl,"an alliance chat message",(a,value)->{
                if(!value.isBlank()&&value.length()<=250)svc.sendAllianceChat(a,value);guild(a,svc,g,false);
            }));
            if(!g.isLeader(p.getUuid()))m.slot(41,item(Items.BARRIER,"§cLeave Guild","§7Requires confirmation"),(pl,c)->confirm(pl,"Leave "+g.name+"?",()->{svc.leave(pl);start(pl,svc);},()->guild(pl,svc,g,false)));
            else m.slot(41,item(Items.BARRIER,"§cDisband Guild","§7This archives guild funds and items","§cIrreversible without administrator"),(pl,c)->confirm(pl,"Archive and disband "+g.name+"?",()->{
                if(g.isLeader(pl.getUuid()))svc.disband(pl,false,g);start(pl,svc);
            },()->guild(pl,svc,g,false)));
        }
        if(admin){
            m.slot(48,item(Items.BARRIER,"§cAdmin View","§7Guild: "+g.name,"§7Leader: "+g.memberNames.getOrDefault(g.leader,"Unknown")),(pl,c)->{});
            if(g.closed)m.slot(50,item(Items.EMERALD,"§aRestore Archived Guild"),(pl,c)->confirm(pl,"Restore "+g.name+"?",()->{
                if(!svc.isAdmin(pl,"cobbleguilds.admin.manage"))return;
                g.closed=false;g.lastFeeDay=java.time.LocalDate.now(svc.zone()).toString();g.addAudit("ADMIN restored guild");svc.store.save();guild(pl,svc,g,true);
            },()->guild(pl,svc,g,true)));
            m.slot(53,item(Items.ARROW,"§7All Guilds"),(pl,c)->browse(pl,svc,0,true));
        }else m.slot(53,item(Items.ARROW,"§7Close"),(pl,c)->pl.closeHandledScreen());
        m.open(p,(admin?"ADMIN · ":"")+g.name);
    }
    public static void buffsMenu(ServerPlayerEntity p,GuildService svc){Guild g=svc.mine(p);if(g==null)start(p,svc);else buffs(p,svc,g,false);}
    public static void questsMenu(ServerPlayerEntity p,GuildService svc){Guild g=svc.mine(p);if(g==null)start(p,svc);else quests(p,svc,g,false,0);}
    public static void announcementMenu(ServerPlayerEntity p,GuildService svc){Guild g=svc.mine(p);if(g==null)start(p,svc);else announcement(p,svc,g,false);}
    private static void settings(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!can(p,svc,g,"manage_settings",admin))return;
        Menu m=new Menu(4);
        m.slot(10,item(Items.PAPER,"§eName: "+g.name,"§7Click and type in chat"),(pl,c)->ask(pl,"a new guild name",(a,value)->{
            if(!can(a,svc,g,"manage_settings",admin))return;
            if(!value.matches("[A-Za-z0-9 _-]{3,24}")||svc.store.all().stream().anyMatch(other->other!=g&&!other.closed&&(other.name.equalsIgnoreCase(value)||other.tag.equalsIgnoreCase(value))))GuildService.tell(a,"Invalid or duplicate guild name.");
            else{g.name=value;g.addAudit(a.getUuid()+" updated name");svc.store.save();}settings(a,svc,g,admin);
        }));
        m.slot(11,item(Items.NAME_TAG,"§eTag: "+g.tag,"§7Click and type in chat"),(pl,c)->ask(pl,"a new guild tag",(a,value)->{
            if(!can(a,svc,g,"manage_settings",admin))return;
            if(!value.matches("[A-Za-z0-9]{2,4}")||svc.store.all().stream().anyMatch(other->other!=g&&!other.closed&&(other.tag.equalsIgnoreCase(value)||other.name.equalsIgnoreCase(value))))GuildService.tell(a,"Invalid or duplicate tag.");
            else{g.tag=value;g.addAudit(a.getUuid()+" updated tag");svc.store.save();}settings(a,svc,g,admin);
        }));
        m.slot(12,item(Items.REDSTONE,"§eColor: "+g.color+"Example"),(pl,c)->colors(pl,svc,g,admin));
        m.slot(13,item(g.publicGuild?Items.OAK_DOOR:Items.IRON_DOOR,g.publicGuild?"§aPublic":"§eApplications Required","§7Click to toggle"),(pl,c)->{
            if(!can(pl,svc,g,"manage_settings",admin))return;
            g.publicGuild=!g.publicGuild;svc.store.save();settings(pl,svc,g,admin);
        });
        m.slot(14,item(material(g.icon),"§eGuild Icon","§7Hold any item on cursor and click","§7Your item is not consumed"),(pl,c)->{
            if(!can(pl,svc,g,"manage_settings",admin))return;
            if(c.isEmpty()){GuildService.tell(pl,"Pick up an item with your cursor first.");return;}
            g.icon=Registries.ITEM.getId(c.getItem()).toString();svc.store.save();settings(pl,svc,g,admin);
        });
        m.slot(15,item(Items.WRITABLE_BOOK,"§eDescription","§7"+g.description,"§7Click to edit in chat"),(pl,c)->ask(pl,"a description (max 220 characters)",(a,value)->{
            if(!can(a,svc,g,"manage_settings",admin))return;
            if(value.length()>220)GuildService.tell(a,"Description too long.");else{g.description=value;svc.store.save();}settings(a,svc,g,admin);
        }));
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Settings");
    }
    private static void bank(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!g.contains(p.getUuid()) && !(admin && svc.isAdmin(p,"cobbleguilds.admin.bank")))return;
        Menu m=new Menu(4);m.slot(4,item(Items.GOLD_BLOCK,"§6Bank: $"+g.bank,"§7Maintenance: $"+svc.config.dailyFeePrice),(pl,c)->{});
        int[] sums={1000,10000,100000,1000000};
        for(int i=0;i<sums.length;i++){
            BigInteger amount=BigInteger.valueOf(sums[i]);
            m.slot(10+i,item(Items.EMERALD,"§aDeposit $"+amount),(pl,c)->{if(g.contains(pl.getUuid()))svc.bankDeposit(pl,amount);bank(pl,svc,g,admin);});
            m.slot(19+i,item(Items.GOLD_INGOT,"§eWithdraw $"+amount),(pl,c)->{svc.bankWithdraw(pl,amount,admin,g);bank(pl,svc,g,admin);});
        }
        m.slot(16,item(Items.WRITABLE_BOOK,"§aCustom Deposit","§7Click and type amount"),(pl,c)->ask(pl,"a positive deposit amount",(a,value)->{
            try{if(g.contains(a.getUuid()))svc.bankDeposit(a,GuildMoney.amount(value));}catch(Exception ex){GuildService.tell(a,"Invalid amount.");}bank(a,svc,g,admin);
        }));
        m.slot(25,item(Items.WRITABLE_BOOK,"§eCustom Withdrawal","§7Click and type amount"),(pl,c)->ask(pl,"a positive withdrawal amount",(a,value)->{
            try{svc.bankWithdraw(a,GuildMoney.amount(value),admin,g);}catch(Exception ex){GuildService.tell(a,"Invalid amount.");}bank(a,svc,g,admin);
        }));
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Bank");
    }
    private static void buffs(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);long now=System.currentTimeMillis();int i=0;
        for(GuildBuffs.Offer offer:GuildBuffs.OFFERS){
            Guild.Effect fx=g.effects.get(offer.id());boolean active=GuildBuffs.active(fx,now);
            m.slot(i++,item(active?Items.ENCHANTED_BOOK:Items.BOOK,"§d"+offer.label(),"§7Price: $"+offer.price(),"§7Duration: "+(offer.minutes()==0?"Permanent":offer.minutes()+" minutes"),
                    "§7Status: "+(active?GuildBuffs.durationText(fx,now):"Not active"),"§eClick to purchase for guild"),(pl,c)->{
                if(admin){GuildService.tell(pl,"Admin cannot purchase buffs on another guild's behalf.");return;}
                if(!can(pl,svc,g,"purchase_buffs",false))return;
                confirm(pl,"Buy "+offer.label()+" for $"+offer.price()+"?",()->{GuildBuffs.buy(svc,pl,offer.id());buffs(pl,svc,g,false);},()->buffs(pl,svc,g,false));
            });
        }
        int slot=18;
        for(var unsupported:GuildBuffs.UNSUPPORTED.entrySet())m.slot(slot++,item(Items.BARRIER,"§8"+unsupported.getKey(),"§7Unavailable: "+unsupported.getValue()),(pl,c)->{});
        m.slot(49,item(Items.CLOCK,"§aActive Effects","§7Click to view timers"),(pl,c)->effects(pl,svc,g,admin));
        m.slot(53,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Buff Shop");
    }
    private static void effects(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);int i=0;long now=System.currentTimeMillis();
        for(GuildBuffs.Offer o:GuildBuffs.OFFERS){Guild.Effect fx=g.effects.get(o.id());if(!GuildBuffs.active(fx,now))continue;
            m.slot(i++,item(Items.ENCHANTED_BOOK,"§d"+o.label(),"§7Remaining: "+GuildBuffs.durationText(fx,now)),(pl,c)->{});
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->buffs(pl,svc,g,admin));m.open(p,"Active Guild Effects");
    }
    private static void upgrades(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(3);int tier=g.chestRows-1;
        String next=tier<svc.config.chestUpgradePrices.length?svc.config.chestUpgradePrices[tier]:"MAX";
        m.slot(11,item(Items.CHEST,"§eChest: "+(g.chestRows*9)+" slots","§7Next: $"+next,"§eClick to upgrade"),(pl,c)->{
            if(!can(pl,svc,g,"upgrade_chest",admin))return;
            if(admin){GuildService.tell(pl,"Chest upgrades must be purchased by guild members.");return;}
            confirm(pl,"Upgrade chest for $"+next+"?",()->{svc.upgradeChest(pl);upgrades(pl,svc,g,admin);},()->upgrades(pl,svc,g,admin));
        });
        m.slot(15,item(Items.PLAYER_HEAD,"§bMembers: "+g.capacity(svc.config.maxGuildMembers),"§7Next upgrade: $"+svc.config.guildMemberUpgradeCost),(pl,c)->{
            if(!can(pl,svc,g,"manage_upgrades",admin))return;
            if(g.capacity(svc.config.maxGuildMembers)>=100)return;
            BigInteger cost=BigInteger.valueOf(svc.config.guildMemberUpgradeCost);
            confirm(pl,"Increase member capacity for $"+cost+"?",()->{
                if(!can(pl,svc,g,"manage_upgrades",admin))return;
                if(g.funds().compareTo(cost)<0){GuildService.tell(pl,"Insufficient guild funds.");upgrades(pl,svc,g,admin);return;}
                g.bank=g.funds().subtract(cost).toString();g.memberSlotsBonus=Math.min(100,g.memberSlotsBonus+Math.max(1,svc.config.guildMemberUpgradeIncrement));svc.store.save();upgrades(pl,svc,g,admin);
            },()->upgrades(pl,svc,g,admin));
        });
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Upgrades");
    }
    private static void home(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(3);m.slot(11,item(Items.ENDER_PEARL,"§bTeleport to Guild Home","§7"+(g.homeWorld.isBlank()?"Not set":g.homeWorld)),(pl,c)->{
            if(!g.contains(pl.getUuid())){GuildService.tell(pl,"Only members can teleport to their home.");return;}
            GuildExtras.teleportHome(pl);
        });
        m.slot(15,item(Items.COMPASS,"§eSet Guild Home","§7Requires Manage Home permission"),(pl,c)->{
            if(!can(pl,svc,g,"manage_home",admin))return;
            if(!g.contains(pl.getUuid())){GuildService.tell(pl,"Enter the guild before setting its home.");return;}
            confirm(pl,"Set home at your current location?",()->{GuildExtras.setHome(pl);home(pl,svc,g,admin);},()->home(pl,svc,g,admin));
        });
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Home");
    }
    private static void announcement(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(3);m.slot(11,item(Items.BOOK,"§eCurrent Announcement","§7"+(g.announcement.isBlank()?"No announcement":g.announcement)),(pl,c)->{});
        if(canSilent(p,svc,g,"manage_settings",admin))m.slot(15,item(Items.WRITABLE_BOOK,"§aEdit Announcement","§7Click and type your announcement"),(pl,c)->ask(pl,"an announcement (up to 220 characters)",(a,value)->{
            if(!can(a,svc,g,"manage_settings",admin))return;
            if(value.length()>220){GuildService.tell(a,"Announcement is too long.");announcement(a,svc,g,admin);return;}
            g.announcement=value.replace('§',' ');g.addAudit(a.getUuid()+" changed announcement");svc.store.save();
            for(ServerPlayerEntity member:svc.server.getPlayerManager().getPlayerList())if(g.contains(member.getUuid()))GuildService.tell(member,"Guild Announcement: "+g.announcement);
            announcement(a,svc,g,admin);
        }));
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Announcement");
    }
    private static boolean canSilent(ServerPlayerEntity p,GuildService s,Guild g,String perm,boolean admin){return admin&&s.isAdmin(p,"cobbleguilds.admin.manage")||g.allowed(p.getUuid(),perm);}
    private static void invite(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!g.contains(p.getUuid())&&!canSilent(p,svc,g,"manage_applications",admin)){GuildService.tell(p,"Admin invitations require Manage Guild permission.");return;}
        ask(p,"the exact username of an ONLINE player",(pl,value)->{
            if(!g.contains(pl.getUuid())&&!canSilent(pl,svc,g,"manage_applications",admin))return;
            ServerPlayerEntity target=svc.server.getPlayerManager().getPlayer(value);
            if(target==null){GuildService.tell(pl,"Player must be online.");guild(pl,svc,g,admin);return;}
            if(admin){if(svc.mine(target)!=null||g.banned.contains(target.getUuidAsString()))GuildService.tell(pl,"Target is unavailable.");
                else{g.invitations.add(target.getUuidAsString());svc.store.save();GuildService.tell(target,"Admin invited you to "+g.name+". Open /guilds to accept.");}}
            else svc.invite(pl,target);
            guild(pl,svc,g,admin);
        });
    }
    private static void pending(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!can(p,svc,g,"manage_applications",admin))return;
        Menu m=new Menu(6);int i=0;
        for(String id:List.copyOf(g.pending)){
            if(i>=45)break;
            ServerPlayerEntity online=svc.server.getPlayerManager().getPlayer(UUID.fromString(id));
            m.slot(i++,item(Items.PLAYER_HEAD,"§e"+(online==null?id:online.getName().getString()),"§aClick to approve this application"),(pl,c)->{
                if(!can(pl,svc,g,"manage_applications",admin))return;
                if(!g.pending.contains(id))return;
                if(c.isEmpty()){} // action uses registered button, see Menu.onSlotClick
                if(svc.store.member(UUID.fromString(id))!=null||g.members.size()>=g.capacity(svc.config.maxGuildMembers)){GuildService.tell(pl,"Applicant unavailable or guild full.");return;}
                g.pending.remove(id);g.members.put(id,g.defaultRank());if(online!=null)g.memberNames.put(id,online.getName().getString());
                g.addAudit(pl.getUuid()+" accepted "+id);svc.store.save();pending(pl,svc,g,admin);
            });
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Applications");
    }
    private static void members(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,int page){
        Menu m=new Menu(6);List<String> ids=new ArrayList<>(g.members.keySet());int from=page*45;
        for(int i=from;i<Math.min(ids.size(),from+45);i++){
            String id=ids.get(i);m.slot(i-from,head(g,id,svc),(pl,c)->member(pl,svc,g,admin,id,page));
        }
        if(page>0)m.slot(45,item(Items.ARROW,"Previous"),(pl,c)->members(pl,svc,g,admin,page-1));
        if(from+45<ids.size())m.slot(53,item(Items.ARROW,"Next"),(pl,c)->members(pl,svc,g,admin,page+1));
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Members");
    }
    private static void member(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,String id,int page){
        if(!g.members.containsKey(id)){members(p,svc,g,admin,page);return;}
        UUID uuid=UUID.fromString(id);Guild.Rank targetRole=g.rank(uuid), ownRole=g.rank(p.getUuid());
        int actorPriority=admin&&svc.isAdmin(p,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:(ownRole==null?-1:ownRole.priority);
        boolean manageable=!uuid.equals(p.getUuid()) && !g.isLeader(uuid) && targetRole!=null&&targetRole.priority<actorPriority;
        Menu m=new Menu(3);m.slot(4,head(g,id,svc),(pl,c)->{});
        if(manageable&&canSilent(p,svc,g,"manage_ranks",admin))m.slot(11,item(Items.NAME_TAG,"§eAssign Rank","§7Click to select a rank"),(pl,c)->assignRank(pl,svc,g,admin,id,page));
        if(manageable&&canSilent(p,svc,g,"kick",admin))m.slot(15,item(Items.BARRIER,"§cKick Member","§7Confirm before removing"),(pl,c)->confirm(pl,"Kick member "+g.memberNames.getOrDefault(id,id)+"?",()->{
            if(!can(pl,svc,g,"kick",admin))return;
            Guild.Rank current=g.rank(uuid);Guild.Rank self=g.rank(pl.getUuid());
            int priority=admin&&svc.isAdmin(pl,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:(self==null?-1:self.priority);
            if(uuid.equals(pl.getUuid())||g.isLeader(uuid)||current==null||current.priority>=priority)return;
            g.members.remove(id);g.addAudit(pl.getUuid()+" kicked "+id);svc.store.save();members(pl,svc,g,admin,page);
        },()->member(pl,svc,g,admin,id,page)));
        if(g.isLeader(p.getUuid()) && !g.isLeader(uuid))m.slot(13,item(Items.NETHER_STAR,"§6Transfer Guild Leadership","§7Click to confirm transfer","§cYou will become a regular member"),(pl,c)->confirm(pl,"Transfer leadership to "+g.memberNames.getOrDefault(id,id)+"?",()->{
            if(!g.isLeader(pl.getUuid()) || !g.contains(uuid) || pl.getUuid().equals(uuid))return;
            g.members.put(pl.getUuidAsString(),g.defaultRank());g.members.put(id,"leader");g.leader=id;
            g.addAudit(pl.getUuid()+" transferred leadership to "+id);svc.store.save();guild(pl,svc,g,admin);
        },()->member(pl,svc,g,admin,id,page)));
        if(admin && svc.isAdmin(p,"cobbleguilds.admin.moderation") && !g.isLeader(uuid))m.slot(16,item(Items.REDSTONE_BLOCK,"§cBan From Guild","§7Prevent joining until unbanned"),(pl,c)->confirm(pl,"Guild-ban "+g.memberNames.getOrDefault(id,id)+"?",()->{
            if(!svc.isAdmin(pl,"cobbleguilds.admin.moderation"))return;
            g.members.remove(id);g.pending.remove(id);g.invitations.remove(id);g.ordinaryInvites.remove(id);g.banned.add(id);
            g.addAudit("ADMIN "+pl.getUuid()+" banned "+id);svc.store.save();members(pl,svc,g,admin,page);
        },()->member(pl,svc,g,admin,id,page)));
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->members(pl,svc,g,admin,page));m.open(p,"Guild Member");
    }
    private static void assignRank(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,String memberId,int page){
        if(!can(p,svc,g,"manage_ranks",admin))return;
        Guild.Rank actor=g.rank(p.getUuid());int priority=admin&&svc.isAdmin(p,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:actor.priority;
        Menu m=new Menu(4);int i=0;
        for(var e:g.ranks.entrySet()){
            if(i>=27)break;
            String id=e.getKey();Guild.Rank rank=e.getValue();if(id.equals("leader")||rank.priority>=priority)continue;
            m.slot(i++,item(Items.PAPER,"§e"+rank.name,"§7Priority: "+rank.priority),(pl,c)->{
                if(!can(pl,svc,g,"manage_ranks",admin))return;
                UUID target=UUID.fromString(memberId);Guild.Rank before=g.rank(target),own=g.rank(pl.getUuid());
                int max=admin&&svc.isAdmin(pl,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:own.priority;
                if(before==null||before.priority>=max||rank.priority>=max||g.isLeader(target)||pl.getUuid().equals(target))return;
                g.members.put(memberId,id);g.addAudit(pl.getUuid()+" assigned "+memberId+" to "+id);svc.store.save();member(pl,svc,g,admin,memberId,page);
            });
        }
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->member(pl,svc,g,admin,memberId,page));m.open(p,"Assign Member Rank");
    }
    private static void ranks(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(4);int i=0;
        for(var entry:g.ranks.entrySet()){
            String key=entry.getKey();Guild.Rank rank=entry.getValue();if(i>=27)break;
            m.slot(i++,item(Items.NAME_TAG,"§e"+rank.name,"§7Priority: "+(key.equals("leader")?"Leader":rank.priority),"§7Default: "+rank.isDefault,"§7Click to edit"),(pl,c)->rank(pl,svc,g,admin,key));
        }
        if(canSilent(p,svc,g,"manage_ranks",admin))m.slot(29,item(Items.EMERALD,"§aCreate Rank","§7Click and type a rank name"),(pl,c)->ask(pl,"a new rank name (1-12 characters)",(a,value)->{
            if(!can(a,svc,g,"manage_ranks",admin))return;
            String id=value.toLowerCase(Locale.ROOT).replace(' ','_');
            if(!value.matches("[A-Za-z0-9 _-]{1,12}")||!id.matches("[a-z0-9_-]{1,12}")||g.ranks.containsKey(id)||id.equals("leader"))GuildService.tell(a,"Invalid or duplicate rank name.");
            else{g.ranks.put(id,new Guild.Rank(value,1,false));g.addAudit(a.getUuid()+" created rank "+id);svc.store.save();}
            ranks(a,svc,g,admin);
        }));
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Ranks");
    }
    private static final String[] PERMISSIONS={"chest","bank_withdraw","manage_ranks","kick","manage_applications","manage_settings","purchase_buffs","upgrade_chest","manage_members","manage_home","manage_upgrades"};
    private static void rank(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,String id){
        Guild.Rank r=g.ranks.get(id);if(r==null){ranks(p,svc,g,admin);return;}
        Guild.Rank own=g.rank(p.getUuid());int myPriority=admin&&svc.isAdmin(p,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:(own==null?-1:own.priority);
        boolean editable=canSilent(p,svc,g,"manage_ranks",admin)&&(!id.equals("leader"))&&r.priority<myPriority;
        Menu m=new Menu(4);m.slot(4,item(Items.NAME_TAG,"§e"+r.name,"§7ID: "+id,"§7Priority: "+r.priority,"§7Default: "+r.isDefault),(pl,c)->{});
        if(canSilent(p,svc,g,"manage_ranks",admin))m.slot(10,item(Items.PAPER,"§eRename","§7Click to type new name"),(pl,c)->ask(pl,"a rank name (max 12 characters)",(a,value)->{
            if(!can(a,svc,g,"manage_ranks",admin))return;
            if(!value.matches("[A-Za-z0-9 _-]{1,12}"))GuildService.tell(a,"Invalid rank name.");
            else if(id.equals("leader")&&!g.isLeader(a.getUuid())&&!(admin&&svc.isAdmin(a,"cobbleguilds.admin.manage")))GuildService.tell(a,"Only the leader may rename Leader.");
            else if(!id.equals("leader")&&(!canSilent(a,svc,g,"manage_ranks",admin)||g.ranks.get(id).priority>=(admin?Integer.MAX_VALUE:g.rank(a.getUuid()).priority)))GuildService.tell(a,"Cannot edit this rank.");
            else {r.name=value;svc.store.save();}rank(a,svc,g,admin,id);
        }));
        if(editable){
            m.slot(11,item(Items.EXPERIENCE_BOTTLE,"§aPriority: "+r.priority,"§7Click and type number"),(pl,c)->ask(pl,"a rank priority from 0 to 999",(a,value)->{
                if(!can(a,svc,g,"manage_ranks",admin))return;
                try{int number=Integer.parseInt(value);int cap=admin&&svc.isAdmin(a,"cobbleguilds.admin.manage")?1000:g.rank(a.getUuid()).priority;
                    if(number<0||number>=cap||number>999)GuildService.tell(a,"Priority out of range.");
                    else{r.priority=number;svc.store.save();}
                }catch(NumberFormatException ex){GuildService.tell(a,"Enter a number.");}rank(a,svc,g,admin,id);
            }));
            m.slot(12,item(r.isDefault?Items.EMERALD:Items.REDSTONE,"§eDefault Rank: "+r.isDefault,"§7Click to set as default"),(pl,c)->{
                if(!can(pl,svc,g,"manage_ranks",admin))return;
                g.ranks.values().forEach(other->other.isDefault=false);r.isDefault=true;svc.store.save();rank(pl,svc,g,admin,id);
            });
            for(int i=0;i<PERMISSIONS.length;i++){
                String perm=PERMISSIONS[i];boolean on=r.permissions.contains(perm);
                m.slot(18+i,item(on?Items.LIME_DYE:Items.GRAY_DYE,(on?"§aEnabled: ":"§7Disabled: ")+perm,"§7Click to toggle permission"),(pl,c)->{
                    if(!can(pl,svc,g,"manage_ranks",admin))return;
                    Guild.Rank self=g.rank(pl.getUuid());int pri=admin&&svc.isAdmin(pl,"cobbleguilds.admin.manage")?Integer.MAX_VALUE:self.priority;
                    if(r.priority>=pri||(!admin&&!g.isLeader(pl.getUuid())&&!g.allowed(pl.getUuid(),perm)))return;
                    if(!r.permissions.add(perm))r.permissions.remove(perm);
                    g.addAudit(pl.getUuid()+" toggled "+perm+" for "+id);svc.store.save();rank(pl,svc,g,admin,id);
                });
            }
        }
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->ranks(pl,svc,g,admin));m.open(p,"Rank · "+r.name);
    }
    public static void bingo(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!svc.config.bingoEnabled||!svc.config.questsEnabled){GuildService.tell(p,"Bingo is disabled.");return;}
        svc.ensureQuests(g);Menu m=new Menu(6);int[] places={20,21,22,29,30,31,38,39,40};String key="bingo-"+svc.currentWeek()+"-";
        for(int i=0;i<9;i++){
            Guild.Quest q=g.quests.get(key+i);if(q==null)continue;
            m.slot(places[i],questItem(p,q),(pl,c)->questDetail(pl,svc,g,admin,q,()->bingo(pl,svc,g,admin)));
        }
        m.slot(4,item(Items.MAP,"§dWeekly Guild Bingo","§7Complete lines of three","§7Bonus XP per line: "+Math.min(150,svc.config.bingoLineExperience)),(pl,c)->{});
        if(!admin)m.slot(49,item(Items.CHEST,"§aClaim Rewards"),(pl,c)->{svc.claim(pl);bingo(pl,svc,g,admin);});
        m.slot(53,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Bingo");
    }
    private static String questGoal(Guild.Quest q){return switch(q.kind){
        case "deposit" -> "Deposit "+q.target+" new CobbleDollars to bank";
        case "capture" -> "Catch "+q.target+" wild Pokémon together";
        case "shiny_capture" -> "Catch "+q.target+" shiny Pokémon";
        case "type_capture" -> "Catch "+q.target+" "+q.qualifier+"-type Pokémon";
        case "unique_capture" -> "Catch "+q.target+" distinct species";
        case "species_capture" -> "Catch "+q.target+" "+q.qualifier;
        default -> "Complete "+q.target+" "+q.kind.replace('_',' ') +" actions";
    };}
    private static ItemStack questItem(ServerPlayerEntity p,Guild.Quest q){
        return item(q.completed?Items.EMERALD:Items.PAPER,"§d"+q.name,"§7Objective: "+questGoal(q),"§7Guild progress: "+q.progress()+"/"+q.target,
                "§7Your contribution: "+q.amount(p.getUuid())+"/"+q.minimum,"§7Reward: "+q.candies+" Rare Candies",
                "§7Status: "+(q.completed?"Completed":q.expiresAt<System.currentTimeMillis()?"Expired":"Active"),"§eClick for details");
    }
    private static void quests(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,int page){
        svc.ensureQuests(g);List<Guild.Quest> list=g.quests.values().stream().filter(q->!q.id.startsWith("bingo-") && (q.completed||q.expiresAt>System.currentTimeMillis())).toList();
        Menu m=new Menu(6);int from=page*45;
        for(int i=from;i<Math.min(list.size(),from+45);i++){
            Guild.Quest q=list.get(i);m.slot(i-from,questItem(p,q),(pl,c)->questDetail(pl,svc,g,admin,q,()->quests(pl,svc,g,admin,page)));
        }
        if(page>0)m.slot(45,item(Items.ARROW,"Previous"),(pl,c)->quests(pl,svc,g,admin,page-1));
        if(from+45<list.size())m.slot(53,item(Items.ARROW,"Next"),(pl,c)->quests(pl,svc,g,admin,page+1));
        if(!admin)m.slot(49,item(Items.CHEST,"§aClaim Pending Rewards"),(pl,c)->{svc.claim(pl);quests(pl,svc,g,admin,page);});
        m.slot(52,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Daily & Weekly Quests");
    }
    private static void questDetail(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,Guild.Quest q,Runnable back){
        Menu m=new Menu(6);m.slot(4,questItem(p,q),(pl,c)->{});int slot=9;
        for(var e:q.contributions.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).limit(36).toList()){
            String name=g.memberNames.getOrDefault(e.getKey(),e.getKey());
            m.slot(slot++,item(Items.PAPER,"§e"+name,"§7Contributed: "+e.getValue()),(pl,c)->{});
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->back.run());m.open(p,"Quest · "+q.name);
    }
    private static void achievements(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);String[][] goals={{"First 100 Captures","100 total captures"},{"Capture Veterans","1,000 total captures"},{"Quest Adventurers","10 completed quests"},{"Quest Masters","100 completed quests"}};
        for(int i=0;i<goals.length;i++){
            String[] a=goals[i];boolean unlocked=g.achievements.contains(a[0]);
            m.slot(10+i,item(unlocked?Items.EMERALD:Items.COAL,(unlocked?"§a":"§7")+a[0],"§7"+a[1],unlocked?"§aUnlocked":"§eLocked"),(pl,c)->{});
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Achievements");
    }
    private static void milestones(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(4);int[] targets={100,500,1000,5000,10000};
        for(int i=0;i<targets.length;i++){
            int t=targets[i];m.slot(10+i,item(g.totalCaptures>=t?Items.EMERALD:Items.ENDER_PEARL,"§eCapture "+t+" Pokémon","§7Progress: "+Math.min(t,g.totalCaptures)+"/"+t),(pl,c)->{});
        }
        int[] quests={10,50,100,500};
        for(int i=0;i<quests.length;i++){
            int t=quests[i];m.slot(19+i,item(g.questsCompleted>=t?Items.EMERALD:Items.BOOK,"§eComplete "+t+" Quests","§7Progress: "+Math.min(t,g.questsCompleted)+"/"+t),(pl,c)->{});
        }
        m.slot(31,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Milestones");
    }
    private static void challenge(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);String week=svc.currentWeek();int i=0;
        for(Guild candidate:svc.store.all().stream().filter(x->!x.closed).sorted((a,b)->Long.compare(b.weeklyCaptures,a.weeklyCaptures)).limit(45).toList()){
            long count=week.equals(candidate.weeklyCapturePeriod)?candidate.weeklyCaptures:0;
            m.slot(i++,item(material(candidate.icon),"§e#"+i+" "+candidate.name,"§7Weekly captures: "+count,"§7Leader: "+candidate.memberNames.getOrDefault(candidate.leader,"Unknown")),(pl,c)->{});
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Weekly Guild Challenge");
    }
    private static void bounties(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        svc.ensureQuests(g);Menu m=new Menu(6);int i=0;
        for(Guild.Quest q:g.quests.values()){
            if(!q.kind.equals("species_capture")||q.expiresAt<System.currentTimeMillis()&&!q.completed)continue;
            if(i>=45)break;m.slot(i++,questItem(p,q),(pl,c)->questDetail(pl,svc,g,admin,q,()->bounties(pl,svc,g,admin)));
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Bounties");
    }
    private static void experience(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(3);m.slot(11,item(Items.EXPERIENCE_BOTTLE,"§aGuild Level: "+g.level,"§7Total XP: "+g.experience,
                "§7Next level: "+GuildLevels.threshold(g.level+1),"§7Quest XP: 25 to 150","§7Bingo line XP: up to 150"),(pl,c)->{});
        m.slot(15,item(Items.BOOK,"§bHow to earn XP","§7Complete daily and weekly quests","§7Complete rows in Guild Bingo","§7Each new level requires more XP"),(pl,c)->{});
        m.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Experience");
    }
    private static void logs(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);List<String> recent=g.audit.size()>45?g.audit.subList(g.audit.size()-45,g.audit.size()):g.audit;
        for(int i=0;i<recent.size();i++){String entry=recent.get(recent.size()-i-1);
            m.slot(i,item(Items.PAPER,"§7Event "+(i+1),"§7"+entry),(pl,c)->{});
        }
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Activity Log");
    }
    private static void alliances(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu m=new Menu(6);int i=0;
        for(String id:List.copyOf(g.allies)){
            Guild other=svc.store.byId(id);if(other==null)continue;
            m.slot(i++,item(Items.EMERALD,"§a"+other.name,"§7["+other.tag+"] - Allied","§cClick to remove alliance"),(pl,c)->confirm(pl,"Remove alliance with "+other.name+"?",()->{
                if(!can(pl,svc,g,"manage_settings",admin)||!g.isLeader(pl.getUuid())&&!admin)return;
                g.allies.remove(other.id);other.allies.remove(g.id);svc.store.save();alliances(pl,svc,g,admin);
            },()->alliances(pl,svc,g,admin)));
        }
        for(String id:List.copyOf(g.incomingAlliances)){
            Guild other=svc.store.byId(id);if(other==null)continue;
            m.slot(i++,item(Items.PAPER,"§eRequest from "+other.name,"§aClick to accept"),(pl,c)->{
                if(!can(pl,svc,g,"manage_settings",admin)||!g.isLeader(pl.getUuid())&&!admin)return;
                if(g.allies.size()>=5||other.allies.size()>=5||other.closed){GuildService.tell(pl,"Alliance limit reached.");return;}
                g.incomingAlliances.remove(other.id);g.allies.add(other.id);other.allies.add(g.id);svc.store.save();alliances(pl,svc,g,admin);
            });
        }
        if(canSilent(p,svc,g,"manage_settings",admin))m.slot(49,item(Items.EMERALD_BLOCK,"§aRequest an Alliance","§7Choose a guild"),(pl,c)->allianceBrowse(pl,svc,g,admin,0));
        m.slot(53,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));m.open(p,"Guild Alliances");
    }
    private static void allianceBrowse(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin,int page){
        if(!can(p,svc,g,"manage_settings",admin)||!g.isLeader(p.getUuid())&&!admin)return;
        List<Guild> possible=svc.store.all().stream().filter(other->!other.closed&&other!=g&&!g.allies.contains(other.id)).toList();
        Menu m=new Menu(6);int from=45*page;
        for(int i=from;i<Math.min(possible.size(),from+45);i++){
            Guild other=possible.get(i);m.slot(i-from,guildIcon(other),(pl,c)->{
                if(!can(pl,svc,g,"manage_settings",admin)||!g.isLeader(pl.getUuid())&&!admin)return;
                if(g.allies.size()>=5||other.allies.size()>=5){GuildService.tell(pl,"Alliance limit reached.");return;}
                other.incomingAlliances.add(g.id);g.addAudit(pl.getUuid()+" requested alliance with "+other.id);svc.store.save();alliances(pl,svc,g,admin);
            });
        }
        if(page>0)m.slot(45,item(Items.ARROW,"Previous"),(pl,c)->allianceBrowse(pl,svc,g,admin,page-1));
        if(from+45<possible.size())m.slot(53,item(Items.ARROW,"Next"),(pl,c)->allianceBrowse(pl,svc,g,admin,page+1));
        m.slot(49,item(Items.ARROW,"Back"),(pl,c)->alliances(pl,svc,g,admin));m.open(p,"Request Guild Alliance");
    }
    private static void confirm(ServerPlayerEntity p,String question,Runnable yes,Runnable no){
        Menu m=new Menu(3);m.slot(4,item(Items.PAPER,"§e"+question),(pl,c)->{});
        m.slot(11,item(Items.EMERALD,"§aConfirm"),(pl,c)->yes.run());
        m.slot(15,item(Items.BARRIER,"§cCancel"),(pl,c)->no.run());m.open(p,"Confirm Action");
    }
    @FunctionalInterface public interface Click {void run(ServerPlayerEntity player,ItemStack cursor);}
    private static final class Menu {
        final int rows;final SimpleInventory inventory;final Map<Integer,Click> actions=new HashMap<>();
        Menu(int rows){this.rows=rows;inventory=new SimpleInventory(rows*9);}
        void slot(int slot,ItemStack stack,Click action){inventory.setStack(slot,stack);actions.put(slot,action);}
        void open(ServerPlayerEntity p,String title){
            p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,inv,player)->new LockedMenu(rows,syncId,inv,inventory,actions),Text.literal(title)));
        }
    }
    private static final class LockedMenu extends GenericContainerScreenHandler {
        private final int size;private final Map<Integer,Click> callbacks;
        LockedMenu(int rows,int id,PlayerInventory playerInv,Inventory inventory,Map<Integer,Click> actions){
            super(switch(rows){case 1->ScreenHandlerType.GENERIC_9X1;case 2->ScreenHandlerType.GENERIC_9X2;case 3->ScreenHandlerType.GENERIC_9X3;case 4->ScreenHandlerType.GENERIC_9X4;case 5->ScreenHandlerType.GENERIC_9X5;default->ScreenHandlerType.GENERIC_9X6;},id,playerInv,inventory,rows);
            this.size=rows*9;this.callbacks=actions;
        }
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity player){
            if(slot<0||slot>=size||button!=0||type!=SlotActionType.PICKUP||!(player instanceof ServerPlayerEntity p))return;
            Click action=callbacks.get(slot);if(action!=null)action.run(p,getCursorStack());
            // Never call super: every icon is read-only. Held cursor items stay untouched.
        }
        @Override public ItemStack quickMove(PlayerEntity player,int slot){return ItemStack.EMPTY;}
    }
}
