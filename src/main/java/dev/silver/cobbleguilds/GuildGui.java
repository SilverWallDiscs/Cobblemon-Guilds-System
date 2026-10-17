package dev.silver.cobbleguilds;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.function.Consumer;

/** Chest-only menus: all slot interactions are handled on server; GUI icons cannot be extracted. */
public final class GuildGui {
    private GuildGui(){}
    private static final Map<UUID,Draft> drafts=new HashMap<>();
    public static Draft draft(ServerPlayerEntity p){return drafts.computeIfAbsent(p.getUuid(),id->new Draft());}
    public static final class Draft {
        public String name="";public String tag="";public String color="§7";
        public String icon="minecraft:name_tag";public String description="";public boolean publicGuild=true;
    }
    public static ItemStack item(Item item,String title,String... lore){
        ItemStack s=new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME,Text.literal(title));
        if(lore.length>0)s.set(DataComponentTypes.LORE,new LoreComponent(Arrays.stream(lore).<Text>map(Text::literal).toList()));
        return s;
    }
    public static ItemStack guildIcon(Guild g){
        Item icon=Registries.ITEM.get(Identifier.of(g.icon));
        if(icon==Items.AIR)icon=Items.NAME_TAG;
        return item(icon,g.color+g.name,"§7["+g.tag+"]","§7Members: "+g.members.size(),"§7"+(g.closed?"ARCHIVED - admin restore only":g.description),"§eClick to view");
    }
    public static void start(ServerPlayerEntity p,GuildService svc){
        Guild own=svc.mine(p);if(own!=null){guild(p,svc,own,false);return;}
        Menu menu=new Menu(1);
        menu.slot(2,item(Items.COMPASS,"§bBrowse Guilds","§7View public guild listings"),(player,cursor)->browse(player,svc,0,false));
        menu.slot(6,item(Items.EMERALD,"§aCreate Guild","§7Costs $"+svc.config.guildCreatePrice),(player,cursor)->create(player,svc));
        menu.open(p,"CobbleGuilds");
    }
    public static void create(ServerPlayerEntity p,GuildService svc){
        if(svc.mine(p)!=null){GuildService.tell(p,"You already belong to a guild.");return;}
        Draft d=draft(p);
        Menu menu=new Menu(4);
        menu.slot(10,item(Items.PAPER,"§eGuild Name: "+d.name,"§7Use /guild draft name <name>"),(pl,c)->GuildService.tell(pl,"Use /guild draft name <name>"));
        menu.slot(11,item(Items.NAME_TAG,"§eTag: "+d.tag,"§7Use /guild draft tag <tag>"),(pl,c)->GuildService.tell(pl,"Use /guild draft tag <tag>"));
        Item icon=Registries.ITEM.get(Identifier.of(d.icon));
        menu.slot(13,item(icon==Items.AIR?Items.NAME_TAG:icon,"§eGuild Icon","§7Hold an item on your cursor and click","§7Your item will NOT be consumed"),(pl,c)->{
            if(c.isEmpty()) {GuildService.tell(pl,"Hold an item on your cursor and click this slot.");return;}
            d.icon=Registries.ITEM.getId(c.getItem()).toString();create(pl,svc);
        });
        menu.slot(15,item(Items.REDSTONE,"§eColor: "+d.color+"Example", "§7Choose a color"),(pl,c)->colors(pl,svc));
        menu.slot(16,item(d.publicGuild?Items.OAK_DOOR:Items.IRON_DOOR,d.publicGuild?"§aPublic Guild":"§eApplication Required"),(pl,c)->{d.publicGuild=!d.publicGuild;create(pl,svc);});
        menu.slot(22,item(Items.WRITABLE_BOOK,"§eDescription","§7Use /guild draft description <text>","§7"+d.description),(pl,c)->GuildService.tell(pl,"Use /guild draft description <text>"));
        menu.slot(31,item(Items.EMERALD_BLOCK,"§aConfirm Guild Creation","§7Cost: $"+svc.config.guildCreatePrice),(pl,c)->{
            if(svc.create(pl,d.name,d.tag,d.color,d.publicGuild,d.description)){
                Guild g=svc.mine(pl);g.icon=d.icon;svc.store.save();drafts.remove(pl.getUuid());guild(pl,svc,g,false);
            }
        });
        menu.open(p,"Create Guild · $"+svc.config.guildCreatePrice);
    }
    private static void colors(ServerPlayerEntity p,GuildService svc){
        String[] codes={"§0","§1","§2","§3","§4","§5","§6","§7","§8","§9","§a","§b","§c","§d","§e","§f"};
        Item[] wool={Items.BLACK_WOOL,Items.BLUE_WOOL,Items.GREEN_WOOL,Items.CYAN_TERRACOTTA,Items.RED_WOOL,Items.PURPLE_WOOL,Items.ORANGE_WOOL,Items.LIGHT_GRAY_WOOL,Items.GRAY_WOOL,Items.LIGHT_BLUE_WOOL,Items.LIME_WOOL,Items.CYAN_WOOL,Items.RED_CONCRETE,Items.MAGENTA_WOOL,Items.YELLOW_WOOL,Items.WHITE_WOOL};
        Menu menu=new Menu(3);
        for(int i=0;i<16;i++){
            String code=codes[i];menu.slot(i,item(wool[i],code+"Color "+code.replace("§","&")),(pl,c)->{draft(pl).color=code;create(pl,svc);});
        }
        menu.open(p,"Guild Color");
    }
    public static void browse(ServerPlayerEntity p,GuildService svc,int page,boolean admin){
        List<Guild> gs=svc.store.all().stream().filter(g->admin || !g.closed).sorted(Comparator.comparing(g->g.name)).toList();
        Menu menu=new Menu(6);int from=Math.max(0,page)*45;
        for(int i=from;i<Math.min(gs.size(),from+45);i++){
            Guild g=gs.get(i);int slot=i-from;
            menu.slot(slot,guildIcon(g),(pl,c)->{
                if(admin){if(g.closed){GuildService.tell(pl,"Archived guild ID: "+g.id+". Restore with /guild admin restore "+g.id);}else guild(pl,svc,g,true);return;}
                if(svc.mine(pl)!=null){guild(pl,svc,svc.mine(pl),false);return;}
                if(g.publicGuild){svc.join(pl,g.name);if(svc.mine(pl)!=null)guild(pl,svc,svc.mine(pl),false);}
                else {svc.join(pl,g.name);GuildService.tell(pl,"This guild requires approval.");}
            });
        }
        if(page>0)menu.slot(45,item(Items.ARROW,"Previous"),(pl,c)->browse(pl,svc,page-1,admin));
        if(from+45<gs.size())menu.slot(53,item(Items.ARROW,"Next"),(pl,c)->browse(pl,svc,page+1,admin));
        menu.open(p,admin?"Guild Administration":"Browse Guilds");
    }
    public static void guild(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(g==null || g.closed){GuildService.tell(p,"Guild not available.");return;}
        if(!admin && !g.contains(p.getUuid())){GuildService.tell(p,"You are not a member of that guild.");return;}
        if(admin && !svc.isAdmin(p,"cobbleguilds.admin.gui")){GuildService.tell(p,"Access denied.");return;}
        Menu menu=new Menu(6);
        menu.slot(10,item(Items.NAME_TAG,g.color+g.name,"§7Tag: "+g.tag,"§7Leader: "+g.leader,"§7Level: "+g.level,"§7Description: "+g.description),(pl,c)->settings(pl,svc,g,admin));
        menu.slot(11,item(Items.PLAYER_HEAD,"§bMembers","§7Members: "+g.members.size()),(pl,c)->members(pl,svc,g,admin));
        menu.slot(12,item(Items.GOLD_INGOT,"§6Guild Bank","§7Balance: $"+g.bank,"§7/guild bank deposit <amount>","§7/guild bank withdraw <amount>"),(pl,c)->bank(pl,svc,g,admin));
        menu.slot(13,item(Items.CHEST,"§eGuild Chest","§7"+(g.chestRows*9)+" slots"),(pl,c)->GuildMenus.openChest(pl,svc,g,admin));
        menu.slot(14,item(Items.ENCHANTED_BOOK,"§dGuild Buffs","§7/guild effects","§7Real boosts are not enabled yet"),(pl,c)->GuildService.tell(pl,"Buff purchases are disabled until real Cobblemon hooks are verified."));
        menu.slot(15,item(Items.BOOK,"§aQuests","§7/guild quest","§7/guild quest claim"),(pl,c)->quests(pl,svc,g,admin));
        menu.slot(16,item(Items.IRON_SWORD,"§fRanks","§7/guild role ..."),(pl,c)->ranks(pl,svc,g,admin));
        menu.slot(20,item(Items.WRITABLE_BOOK,"§eApplications","§7Pending: "+g.pending.size()),(pl,c)->pending(pl,svc,g,admin));
        menu.slot(22,item(Items.EXPERIENCE_BOTTLE,"§aGuild Level "+g.level,"§7XP: "+g.experience),(pl,c)->GuildService.tell(pl,"Level "+g.level+", XP "+g.experience));
        menu.slot(24,item(Items.EMERALD_BLOCK,"§aUpgrade Chest","§7Next: "+(g.chestRows<6?"$"+svc.config.chestUpgradePrices[Math.min(g.chestRows-1,svc.config.chestUpgradePrices.length-1)]:"MAX")),(pl,c)->{
            if(!admin)svc.upgradeChest(pl);
            else GuildService.tell(pl,"Use a guild member with the upgrade permission; admin upgrades are planned.");
            guild(pl,svc,g,admin);
        });
        menu.slot(28,item(Items.COMPASS,"§bGuild Home","§7/guild home","§7/guild sethome"),(pl,c)->GuildService.tell(pl,"Use /guild home or /guild sethome."));
        menu.slot(29,item(Items.WRITABLE_BOOK,"§eAnnouncement","§7/guild announcement","§7/guild announcement set <text>"),(pl,c)->GuildService.tell(pl,"Guild announcement: "+g.announcement));
        menu.slot(30,item(Items.EMERALD,"§aGuild Alliances","§7/guild alliances","§7/guild ally request <guild>","§7/ga <message>"),(pl,c)->GuildService.tell(pl,"Use /guild alliances to view your alliances."));
        menu.slot(31,item(Items.MAP,"§dWeekly Guild Bingo","§7Cooperative 3x3 board","§7/guild bingo"),(pl,c)->bingo(pl,svc,g,admin));
        menu.slot(32,item(Items.GOLDEN_APPLE,"§6Guild Achievements","§7Unlocked: "+g.achievements.size(),"§7/guild achievements"),(pl,c)->GuildService.tell(pl,"Use /guild achievements."));
        menu.slot(33,item(Items.IRON_SWORD,"§bWeekly Guild Challenge","§7/guild challenge","§7This week: "+g.weeklyCaptures+" captures"),(pl,c)->GuildService.tell(pl,"Use /guild challenge to view the weekly standings."));
        menu.slot(34,item(Items.ENDER_EYE,"§dMilestones","§7Lifetime captures: "+g.totalCaptures,"§7/guild milestones"),(pl,c)->GuildService.tell(pl,"Use /guild milestones."));
        menu.slot(40,item(Items.PAPER,"§eGuild Help","§7/guild invite <player>","§7/g <message>","§7/guild bank balance"),(pl,c)->GuildService.tell(pl,"Commands: /g, /guild bank, /guild quest, /guild effects"));
        if(admin)menu.slot(49,item(Items.BARRIER,"§cADMIN MODE","§7Viewing without joining"),(pl,c)->browse(pl,svc,0,true));
        menu.open(p,g.color+g.name+" ["+g.tag+"]");
    }
    private static void settings(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!svc.authorized(p,g,"manage_settings",admin)){
            GuildService.tell(p,"You cannot change guild settings.");return;
        }
        Menu menu=new Menu(3);
        menu.slot(10,item(Items.PAPER,"§eGuild Name: "+g.name,"§7/guild settings name <name>"),(pl,c)->GuildService.tell(pl,"Use /guild settings name <name>"));
        menu.slot(11,item(Items.NAME_TAG,"§eTag: "+g.tag,"§7/guild settings tag <tag>"),(pl,c)->GuildService.tell(pl,"Use /guild settings tag <tag>"));
        menu.slot(12,item(Items.REDSTONE,"§eGuild Color","§7Current: "+g.color+"Example"),(pl,c)->settingColors(pl,svc,g,admin));
        menu.slot(13,item(g.publicGuild?Items.OAK_DOOR:Items.IRON_DOOR,g.publicGuild?"§aPublic Guild":"§eApplication Only","§7Click to toggle"),(pl,c)->{
            if(!svc.authorized(pl,g,"manage_settings",admin))return;
            g.publicGuild=!g.publicGuild;g.addAudit(pl.getUuid()+" changed guild privacy");svc.store.save();settings(pl,svc,g,admin);
        });
        Item icon=Registries.ITEM.get(Identifier.of(g.icon));
        menu.slot(14,item(icon==Items.AIR?Items.NAME_TAG:icon,"§eGuild Icon","§7Hold an item on the cursor and click","§7The item will not be consumed"),(pl,c)->{
            if(!svc.authorized(pl,g,"manage_settings",admin))return;
            if(c.isEmpty()){GuildService.tell(pl,"Hold an item on the cursor first.");return;}
            g.icon=Registries.ITEM.getId(c.getItem()).toString();g.addAudit(pl.getUuid()+" changed icon");svc.store.save();settings(pl,svc,g,admin);
        });
        menu.slot(15,item(Items.WRITABLE_BOOK,"§eGuild Description","§7/guild settings description <text>"),(pl,c)->GuildService.tell(pl,"Use /guild settings description <text>"));
        menu.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));
        menu.open(p,"Guild Settings");
    }
    private static void settingColors(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!svc.authorized(p,g,"manage_settings",admin))return;
        String[] codes={"§0","§1","§2","§3","§4","§5","§6","§7","§8","§9","§a","§b","§c","§d","§e","§f"};
        Item[] palette={Items.BLACK_WOOL,Items.BLUE_WOOL,Items.GREEN_WOOL,Items.CYAN_TERRACOTTA,
            Items.RED_WOOL,Items.PURPLE_WOOL,Items.ORANGE_WOOL,Items.LIGHT_GRAY_WOOL,
            Items.GRAY_WOOL,Items.LIGHT_BLUE_WOOL,Items.LIME_WOOL,Items.CYAN_WOOL,
            Items.RED_CONCRETE,Items.MAGENTA_WOOL,Items.YELLOW_WOOL,Items.WHITE_WOOL};
        Menu menu=new Menu(3);
        for(int i=0;i<16;i++){
            String code=codes[i];menu.slot(i,item(palette[i],code+"Color: "+code.replace('§','&')),(pl,c)->{
                if(!svc.authorized(pl,g,"manage_settings",admin))return;
                g.color=code;g.addAudit(pl.getUuid()+" changed color");svc.store.save();settings(pl,svc,g,admin);
            });
        }
        menu.slot(22,item(Items.ARROW,"Back"),(pl,c)->settings(pl,svc,g,admin));
        menu.open(p,"Guild Colors");
    }
    private static void bank(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!g.contains(p.getUuid()) && !(admin && (svc.isAdmin(p,"cobbleguilds.admin.bank") || svc.isAdmin(p,"cobbleguilds.admin.manage")))){
            GuildService.tell(p,"You cannot access this guild bank.");return;
        }
        Menu menu=new Menu(4);
        menu.slot(4,item(Items.GOLD_BLOCK,"§6Guild Bank: $"+g.bank,"§7Daily maintenance: $"+svc.config.dailyFeePrice), (pl,c)->{});
        int[] slotsDeposit={10,11,12,13}, slotsWithdraw={19,20,21,22};
        int[] amount={1000,10000,100000,1000000};
        for(int i=0;i<4;i++){
            java.math.BigInteger sum=java.math.BigInteger.valueOf(amount[i]);
            menu.slot(slotsDeposit[i],item(Items.EMERALD,"§aDeposit $"+amount[i]),(pl,c)->{
                if(!g.contains(pl.getUuid())){GuildService.tell(pl,"Deposit from your own guild only.");return;}
                svc.bankDeposit(pl,sum);bank(pl,svc,g,admin);
            });
            menu.slot(slotsWithdraw[i],item(Items.GOLD_INGOT,"§eWithdraw $"+amount[i]),(pl,c)->{
                svc.bankWithdraw(pl,sum,admin,g);bank(pl,svc,g,admin);
            });
        }
        menu.slot(31,item(Items.ARROW,"§7Back"),(pl,c)->guild(pl,svc,g,admin));
        menu.open(p,"Guild Bank");
    }
    public static void bingo(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(g==null || (!admin && !g.contains(p.getUuid()))){GuildService.tell(p,"Not a guild member.");return;}
        if(admin && !svc.isAdmin(p,"cobbleguilds.admin.gui"))return;
        if(!svc.config.bingoEnabled || !svc.config.questsEnabled){GuildService.tell(p,"Guild Bingo is disabled.");return;}
        svc.ensureQuests(g);
        String key="bingo-"+svc.currentWeek()+"-";
        Menu menu=new Menu(6);
        int[] positions={20,21,22,29,30,31,38,39,40};
        for(int i=0;i<9;i++){
            Guild.Quest q=g.quests.get(key+i);if(q==null)continue;
            menu.slot(positions[i],item(q.completed?Items.EMERALD:Items.PAPER,
                "§d"+q.name,"§7Progress: "+q.progress()+"/"+q.target,
                "§7Your Contribution: "+q.amount(p.getUuid())+"/"+q.minimum,
                "§7Reward: "+q.candies+" Rare Candies",
                "§7Status: "+(q.completed?"Completed":"In Progress")),
                (pl,c)->GuildService.tell(pl,q.name+" - "+q.progress()+"/"+q.target));
        }
        menu.slot(4,item(Items.MAP,"§dGuild Bingo","§7Complete 3 tiles in a line","§7Each completed line awards "+svc.config.bingoLineExperience+" Guild XP"),(pl,c)->{});
        if(!admin)menu.slot(49,item(Items.CHEST,"§aClaim Pending Rewards","§7Applies to all eligible guild quests"),(pl,c)->{svc.claim(pl);bingo(pl,svc,g,admin);});
        menu.slot(53,item(Items.ARROW,"§7Back"),(pl,c)->guild(pl,svc,g,admin));
        menu.open(p,"Guild Bingo · "+svc.currentWeek());
    }
    private static void members(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu menu=new Menu(6);int i=0;
        for(var entry:g.members.entrySet()){
            if(i>=45)break;
            String role=entry.getValue();String id=entry.getKey();
            ServerPlayerEntity online=svc.server.getPlayerManager().getPlayer(UUID.fromString(id));
            String display=online==null?id:online.getName().getString();
            String currency=online==null?"§7CobbleDollars: Offline": "§6CobbleDollars: $"+svc.money.balance(online);
            menu.slot(i++,item(Items.PLAYER_HEAD,"§b"+display,"§7Rank: "+role,currency,"§7UUID: "+id),(pl,c)->GuildService.tell(pl,"Member: "+display+" | Rank: "+role));
        }
        menu.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));menu.open(p,"Guild Members");
    }
    private static void pending(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        if(!svc.authorized(p,g,"manage_applications",admin)){GuildService.tell(p,"No permission to review applications.");return;}
        Menu menu=new Menu(6);int i=0;
        for(String id:g.pending){if(i>=45)break;menu.slot(i++,item(Items.PAPER,"§e"+id,"§7Use /guild accept <online player>"),(pl,c)->GuildService.tell(pl,"Applicant UUID: "+id));}
        menu.slot(49,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));menu.open(p,"Guild Applications");
    }
    private static void ranks(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu menu=new Menu(3);int i=0;
        for(var entry:g.ranks.entrySet()){if(i>=18)break;Guild.Rank r=entry.getValue();
            menu.slot(i++,item(Items.PAPER,"§e"+r.name,"§7ID: "+entry.getKey(),"§7Priority: "+r.priority,"§7Permissions: "+r.permissions.size()),(pl,c)->GuildService.tell(pl,"Use /guild role ... commands to manage ranks."));}
        menu.slot(22,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));menu.open(p,"Guild Ranks");
    }
    private static void quests(ServerPlayerEntity p,GuildService svc,Guild g,boolean admin){
        Menu menu=new Menu(6);int i=0;
        for(Guild.Quest q:g.quests.values()){
            if(i>=45)break;
            if(!q.completed&&q.expiresAt<System.currentTimeMillis())continue;
            menu.slot(i++,item(q.completed?Items.EMERALD:Items.BOOK,"§b"+q.name,"§7Progress: "+q.progress()+"/"+q.target,"§7Your Contribution: "+q.amount(p.getUuid())+"/"+q.minimum,"§7Reward: "+q.candies+" Rare Candies","§7Status: "+(q.completed?"Completed":"In Progress")),(pl,c)->svc.listQuests(pl));
        }
        if(!admin)menu.slot(49,item(Items.CHEST,"§aClaim Rewards"),(pl,c)->{svc.claim(pl);quests(pl,svc,g,admin);});
        menu.slot(53,item(Items.ARROW,"Back"),(pl,c)->guild(pl,svc,g,admin));menu.open(p,"Guild Quests");
    }
    @FunctionalInterface public interface Click { void run(ServerPlayerEntity p,ItemStack cursor); }
    private static final class Menu {
        final int rows;
        final SimpleInventory inv;
        final Map<Integer,Click> actions=new HashMap<>();
        Menu(int rows){this.rows=rows;this.inv=new SimpleInventory(rows*9);}
        void slot(int at,ItemStack icon,Click click){inv.setStack(at,icon);actions.put(at,click);}
        void open(ServerPlayerEntity p,String title){
            p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,playerInv,player)->
                new LockedMenuHandler(rows,syncId,playerInv,inv,actions),Text.literal(title)));
        }
    }
    private static final class LockedMenuHandler extends GenericContainerScreenHandler {
        private final int guiSize;
        private final Map<Integer,Click> actions;
        LockedMenuHandler(int rows,int syncId,PlayerInventory player,Inventory inventory,Map<Integer,Click> actions){
            super(type(rows),syncId,player,inventory,rows);this.guiSize=rows*9;this.actions=actions;
        }
        private static ScreenHandlerType<?> type(int rows){return switch(rows){
            case 1->ScreenHandlerType.GENERIC_9X1;case 2->ScreenHandlerType.GENERIC_9X2;
            case 3->ScreenHandlerType.GENERIC_9X3;case 4->ScreenHandlerType.GENERIC_9X4;
            case 5->ScreenHandlerType.GENERIC_9X5;default->ScreenHandlerType.GENERIC_9X6;
        };}
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player){
            if(slot>=0&&slot<guiSize&&button==0&&action==SlotActionType.PICKUP && player instanceof ServerPlayerEntity p){
                Click cb=actions.get(slot);if(cb!=null)cb.run(p,getCursorStack());
            }
            // Ignore all drag, swap, shift-click, clone, drop, cursor and player inventory actions.
        }
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
    }
}
