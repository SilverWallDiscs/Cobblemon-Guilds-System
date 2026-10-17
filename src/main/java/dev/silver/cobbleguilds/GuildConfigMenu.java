package dev.silver.cobbleguilds;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import java.math.BigInteger;
import java.util.*;

/** Server-side OP4/LuckPerms configuration editor, no client dependency. */
public final class GuildConfigMenu {
    private GuildConfigMenu(){}
    private static boolean guard(ServerPlayerEntity p,GuildService svc){
        if(svc.isAdmin(p,"cobbleguilds.admin.config"))return true;
        GuildService.tell(p,"You need cobbleguilds.admin.config or OP level 4.");return false;
    }
    private static ItemStack button(Item i,String label,String... lore){return GuildGui.item(i,label,lore);}
    private static void prompt(ServerPlayerEntity p,String label,java.util.function.BiConsumer<ServerPlayerEntity,String> then){GuildChatInput.ask(p,label,then);}
    private static void error(ServerPlayerEntity p,String message){GuildService.tell(p,"§c"+message);}
    private static String money(String amount){
        if(amount.length()>40||!amount.matches("[0-9]+")||new BigInteger(amount).signum()<=0)throw new IllegalArgumentException("Enter a positive number (up to 40 digits)");
        return new BigInteger(amount).toString();
    }
    public static void main(ServerPlayerEntity p,GuildService svc){
        if(!guard(p,svc))return;
        GuildGui.Menu m=new GuildGui.Menu(6);
        m.slot(11,button(Items.BOOK,"§aDaily & Weekly Quests","§7Edit objectives, minimums and modded item rewards"),(pl,c)->quests(pl,svc,0));
        m.slot(13,button(Items.MAP,"§bBingo Settings","§7Enabled: "+svc.config.bingoEnabled,"§7Custom rewards and line XP"),(pl,c)->bingo(pl,svc));
        m.slot(15,button(Items.ENCHANTED_BOOK,"§dGuild Buff Settings","§7Purchases: "+svc.config.buffPurchasesEnabled,"§7Enable/disable individual buffs and edit prices"),(pl,c)->buffs(pl,svc));
        m.slot(19,button(Items.BOOK,"§eDaily Quests: "+svc.config.dailyQuestCount,"§7Click to set 1-5 simultaneous daily quests"),(pl,c)->prompt(pl,"daily quest slots (1-5)",(a,v)->{
            try{int count=Integer.parseInt(v);if(count<1||count>5)throw new IllegalArgumentException();svc.config.dailyQuestCount=count;svc.config.save();}
            catch(Exception ex){error(a,"Enter a number from 1 to 5.");}main(a,svc);
        }));
        m.slot(20,button(Items.CLOCK,"§eWeekly Quests: "+svc.config.weeklyQuestCount,"§7Click to set 1-3 simultaneous weekly quests"),(pl,c)->prompt(pl,"weekly quest slots (1-3)",(a,v)->{
            try{int count=Integer.parseInt(v);if(count<1||count>3)throw new IllegalArgumentException();svc.config.weeklyQuestCount=count;svc.config.save();}
            catch(Exception ex){error(a,"Enter a number from 1 to 3.");}main(a,svc);
        }));
        m.slot(28,button(Items.GOLD_INGOT,"§eDaily Maintenance: $"+svc.config.dailyFeePrice,"§7Click to change the maintenance fee"),(pl,c)->prompt(pl,"the daily maintenance fee in CobbleDollars",(a,v)->{
            if(!guard(a,svc))return;try{svc.config.dailyFeePrice=money(v);svc.config.save();}catch(Exception ex){error(a,ex.getMessage());}main(a,svc);
        }));
        m.slot(29,button(Items.LEVER,"§eDaily Fee: "+svc.config.dailyFee,"§7Click to toggle"),(pl,c)->{
            if(!guard(pl,svc))return;svc.config.dailyFee=!svc.config.dailyFee;svc.config.save();main(pl,svc);
        });
        m.slot(30,button(Items.PAPER,"§eGuild Creation Price: $"+svc.config.guildCreatePrice),(pl,c)->prompt(pl,"the new guild creation price",(a,v)->{
            if(!guard(a,svc))return;try{svc.config.guildCreatePrice=money(v);svc.config.save();}catch(Exception ex){error(a,ex.getMessage());}main(a,svc);
        }));
        m.slot(32,button(Items.CLOCK,"§bReload Server Config","§7Reload cobbleguilds.json only","§7Keeps live guilds and quests unchanged"),(pl,c)->{
            if(!guard(pl,svc))return;try{svc.config.reload();GuildService.tell(pl,"Server config reloaded successfully.");}catch(Exception ex){error(pl,ex.getMessage());}main(pl,svc);
        });
        m.slot(33,button(Items.KNOWLEDGE_BOOK,"§aReload Quest Templates","§7Reload cobbleguilds-quests.json only","§7Existing quests retain their original reward"),(pl,c)->{
            if(!guard(pl,svc))return;try{GuildQuestConfig.reload();GuildService.tell(pl,"Quest templates reloaded successfully.");}catch(Exception ex){error(pl,ex.getMessage());}main(pl,svc);
        });
        m.slot(49,button(Items.ARROW,"§7Back"),(pl,c)->GuildGui.adminHub(pl,svc));m.open(p,"Guilds · Configuration");
    }
    private static void quests(ServerPlayerEntity p,GuildService svc,int page){
        if(!guard(p,svc))return;
        List<GuildQuestConfig.Entry> quests=GuildQuestConfig.current().quests;
        GuildGui.Menu m=new GuildGui.Menu(6);int from=Math.max(0,page)*45;
        for(int i=from;i<Math.min(quests.size(),from+45);i++){
            int index=i;GuildQuestConfig.Entry q=quests.get(i);
            m.slot(i-from,button(q.enabled?Items.WRITABLE_BOOK:Items.BARRIER,(q.enabled?"§a":"§8")+q.name,
                "§7Cadence: "+q.cadence,"§7Objective: "+q.kind,"§7Target: "+q.target+" | Minimum: "+q.minimum,
                "§7Item: "+q.rewardItemId+" x"+q.rewardCount,"§eClick to edit"),(pl,c)->quest(pl,svc,index,page));
        }
        if(page>0)m.slot(45,button(Items.ARROW,"Previous"),(pl,c)->quests(pl,svc,page-1));
        if(from+45<quests.size())m.slot(53,button(Items.ARROW,"Next"),(pl,c)->quests(pl,svc,page+1));
        m.slot(48,button(Items.EMERALD,"§aAdd Quest","§7Creates a new capture quest"),(pl,c)->{
            if(!guard(pl,svc))return;
            GuildQuestConfig.Entry q=new GuildQuestConfig.Entry();q.name="New Quest "+(quests.size()+1);
            q.kind="capture";q.cadence="daily";q.target=100;q.minimum=5;q.candies=5;q.rewardCount=5;
            quests.add(q);saveQuests(pl,svc,()->quest(pl,svc,quests.size()-1,Math.max(0,(quests.size()-1)/45)));
        });
        m.slot(49,button(Items.ARROW,"§7Back"),(pl,c)->main(pl,svc));m.open(p,"Guilds · Quest Templates");
    }
    private static void saveQuests(ServerPlayerEntity p,GuildService svc,Runnable back){
        try{GuildQuestConfig.current().save();GuildService.tell(p,"Quest template saved. Active quests keep their original objectives/rewards.");}
        catch(Exception ex){error(p,ex.getMessage());try{GuildQuestConfig.reload();}catch(Exception ignored){}}
        back.run();
    }
    private static void quest(ServerPlayerEntity p,GuildService svc,int index,int page){
        if(!guard(p,svc))return;
        List<GuildQuestConfig.Entry> entries=GuildQuestConfig.current().quests;
        if(index<0||index>=entries.size()){quests(p,svc,page);return;}
        GuildQuestConfig.Entry q=entries.get(index);
        GuildGui.Menu m=new GuildGui.Menu(6);
        m.slot(4,button(Items.BOOK,"§e"+q.name,"§7"+q.kind+" · "+q.cadence,"§7Target "+q.target+" | Minimum "+q.minimum),(pl,c)->{});
        m.slot(10,button(Items.NAME_TAG,"§eName: "+q.name),(pl,c)->prompt(pl,"a quest name (max 48 characters)",(a,v)->{
            if(!guard(a,svc))return;if(v.isBlank()||v.length()>48)error(a,"Invalid name.");else q.name=v;
            saveQuests(a,svc,()->quest(a,svc,index,page));
        }));
        m.slot(11,button(Items.COMPASS,"§eObjective: "+q.kind,"§7Click to choose"),(pl,c)->kinds(pl,svc,index,page));
        m.slot(12,button(Items.PAPER,"§eTarget: "+q.target),(pl,c)->prompt(pl,"the objective target (positive integer)",(a,v)->{
            try{int value=Integer.parseInt(v);if(value<q.minimum||value>200000000)throw new IllegalArgumentException("Target below minimum or above limit");q.target=value;}catch(Exception ex){error(a,"Invalid target.");}
            saveQuests(a,svc,()->quest(a,svc,index,page));
        }));
        m.slot(13,button(Items.PAPER,"§eMinimum Contribution: "+q.minimum),(pl,c)->prompt(pl,"the minimum contribution (positive integer)",(a,v)->{
            try{int value=Integer.parseInt(v);if(value<1||value>q.target)throw new IllegalArgumentException();q.minimum=value;}catch(Exception ex){error(a,"Minimum must be 1 through target.");}
            saveQuests(a,svc,()->quest(a,svc,index,page));
        }));
        m.slot(14,button(Items.COMPASS,"§eQualifier: "+q.qualifier,"§7For type/species capture objectives"),(pl,c)->prompt(pl,"the lowercase type or species (3-32 letters)",(a,v)->{
            if(v.matches("[a-z]{3,32}"))q.qualifier=v;else error(a,"Invalid qualifier.");saveQuests(a,svc,()->quest(a,svc,index,page));
        }));
        m.slot(15,button(Items.CLOCK,"§eCadence: "+q.cadence,"§7Click to change daily/weekly"),(pl,c)->{
            q.cadence=q.cadence.equals("daily")?"weekly":"daily";saveQuests(pl,svc,()->quest(pl,svc,index,page));
        });
        m.slot(16,button(q.enabled?Items.LIME_DYE:Items.GRAY_DYE,"§eEnabled: "+q.enabled),(pl,c)->{
            q.enabled=!q.enabled;saveQuests(pl,svc,()->quest(pl,svc,index,page));
        });
        ItemStack preview;
        try{preview=GuildRewardCodec.decode(svc.server.getRegistryManager(),q.rewardItemData,q.rewardItemId,q.rewardCount);}catch(Exception e){preview=button(Items.BARRIER,"§cUnknown reward item");}
        final ItemStack shown=preview.copy();
        m.slot(22,button(Items.PAPER,"§aReward: "+q.rewardItemId,"§7Count: "+q.rewardCount,"§7Click with an item on the cursor, or hold it in your main hand","§7The item is copied, never removed"),(pl,c)->{
            if(!guard(pl,svc))return;
            ItemStack held=c.isEmpty()?pl.getMainHandStack():c;
            if(held.isEmpty()){error(pl,"Hold a reward item first.");return;}
            try{q.rewardItemData=GuildRewardCodec.encode(held,svc.server.getRegistryManager());q.rewardItemId=Registries.ITEM.getId(held.getItem()).toString();q.rewardCount=held.getCount();}
            catch(Exception ex){error(pl,ex.getMessage());}
            saveQuests(pl,svc,()->quest(pl,svc,index,page));
        });
        m.slot(23,shown,(pl,c)->{});
        m.slot(24,button(Items.WRITABLE_BOOK,"§eReward Count: "+q.rewardCount,"§7Sets amount for the saved item"),(pl,c)->prompt(pl,"a reward quantity (1-64)",(a,v)->{
            try{int count=Integer.parseInt(v);if(count<1||count>64)throw new IllegalArgumentException();
                ItemStack stack=GuildRewardCodec.decode(svc.server.getRegistryManager(),q.rewardItemData,q.rewardItemId,q.rewardCount);
                stack.setCount(count);q.rewardItemData=GuildRewardCodec.encode(stack,svc.server.getRegistryManager());q.rewardCount=count;
            }catch(Exception ex){error(a,"Invalid reward quantity or missing mod item.");}
            saveQuests(a,svc,()->quest(a,svc,index,page));
        }));
        m.slot(40,button(Items.BARRIER,"§cDelete Quest Template","§7Requires confirmation"),(pl,c)->GuildGui.confirm(pl,"Delete "+q.name+"?",()->{
            if(!guard(pl,svc))return; if(entries.size()<=2){error(pl,"Keep at least one daily and one weekly quest.");quest(pl,svc,index,page);return;}
            entries.remove(index);saveQuests(pl,svc,()->quests(pl,svc,0));
        },()->quest(pl,svc,index,page)));
        m.slot(49,button(Items.ARROW,"§7Back"),(pl,c)->quests(pl,svc,page));m.open(p,"Edit Quest · "+q.name);
    }
    private static void kinds(ServerPlayerEntity p,GuildService svc,int index,int page){
        String[] kinds={"capture","shiny_capture","type_capture","unique_capture","deposit","species_capture"};
        GuildGui.Menu m=new GuildGui.Menu(3);
        for(int i=0;i<kinds.length;i++){
            final String kind=kinds[i];m.slot(10+i,button(Items.PAPER,"§e"+kind),(pl,c)->{
                GuildQuestConfig.Entry q=GuildQuestConfig.current().quests.get(index);
                q.kind=kind;if((kind.equals("type_capture")||kind.equals("species_capture"))&&!q.qualifier.matches("[a-z]{3,32}"))q.qualifier=kind.equals("type_capture")?"water":"magikarp";
                saveQuests(pl,svc,()->quest(pl,svc,index,page));
            });
        }
        m.slot(22,button(Items.ARROW,"§7Back"),(pl,c)->quest(pl,svc,index,page));m.open(p,"Quest Objective");
    }
    private static void bingo(ServerPlayerEntity p,GuildService svc){
        if(!guard(p,svc))return;GuildGui.Menu m=new GuildGui.Menu(3);
        m.slot(10,button(Items.MAP,"§eBingo Enabled: "+svc.config.bingoEnabled),(pl,c)->{svc.config.bingoEnabled=!svc.config.bingoEnabled;svc.config.save();bingo(pl,svc);});
        m.slot(11,button(Items.EXPERIENCE_BOTTLE,"§eXP per Bingo Line: "+svc.config.bingoLineExperience),(pl,c)->prompt(pl,"XP per Bingo line (0-500)",(a,v)->{
            try{int value=Integer.parseInt(v);if(value<0||value>500)throw new IllegalArgumentException();svc.config.bingoLineExperience=value;svc.config.save();}
            catch(Exception ex){error(a,"Invalid XP amount.");}bingo(a,svc);
        }));
        m.slot(13,button(Items.WRITABLE_BOOK,"§aBingo Tile Reward: "+svc.config.bingoRewardItemId,"§7Hold a modded item or use cursor","§7Item is never consumed"),(pl,c)->{
            ItemStack held=c.isEmpty()?pl.getMainHandStack():c;if(held.isEmpty()){error(pl,"Hold an item.");return;}
            try{svc.config.bingoRewardItemData=GuildRewardCodec.encode(held,svc.server.getRegistryManager());
                svc.config.bingoRewardItemId=Registries.ITEM.getId(held.getItem()).toString();svc.config.bingoRewardCount=held.getCount();svc.config.save();}
            catch(Exception ex){error(pl,ex.getMessage());}bingo(pl,svc);
        });
        m.slot(14,button(Items.PAPER,"§eBingo Reward Count: "+svc.config.bingoRewardCount),(pl,c)->prompt(pl,"Bingo reward quantity (1-64)",(a,v)->{
            try{int count=Integer.parseInt(v);if(count<1||count>64)throw new IllegalArgumentException();
                ItemStack stack=GuildRewardCodec.decode(svc.server.getRegistryManager(),svc.config.bingoRewardItemData,svc.config.bingoRewardItemId,svc.config.bingoRewardCount);
                stack.setCount(count);svc.config.bingoRewardItemData=GuildRewardCodec.encode(stack,svc.server.getRegistryManager());svc.config.bingoRewardCount=count;svc.config.save();
            }catch(Exception ex){error(a,"Invalid amount.");}bingo(a,svc);
        }));
        m.slot(22,button(Items.ARROW,"§7Back"),(pl,c)->main(pl,svc));m.open(p,"Guild Bingo Config");
    }
    private static void buffs(ServerPlayerEntity p,GuildService svc){
        if(!guard(p,svc))return;GuildGui.Menu m=new GuildGui.Menu(6);int i=0;
        for(GuildBuffs.Offer offer:GuildBuffs.OFFERS){
            boolean enabled=GuildBuffs.enabled(svc.config,offer);
            m.slot(i++,button(enabled?Items.ENCHANTED_BOOK:Items.BARRIER,(enabled?"§a":"§8")+offer.label(),
                "§7Price: $"+GuildBuffs.price(svc.config,offer),"§7Status: "+(enabled?"Available":"Disabled"),"§eClick to configure"),(pl,c)->buff(pl,svc,offer));
        }
        m.slot(45,button(svc.config.buffPurchasesEnabled?Items.LEVER:Items.BARRIER,"§eGlobal Buff Shop: "+svc.config.buffPurchasesEnabled,
            "§7Toggle purchase availability"),(pl,c)->{svc.config.buffPurchasesEnabled=!svc.config.buffPurchasesEnabled;svc.config.save();buffs(pl,svc);});
        m.slot(49,button(Items.ARROW,"§7Back"),(pl,c)->main(pl,svc));m.open(p,"Guild Buff Configuration");
    }
    private static void buff(ServerPlayerEntity p,GuildService svc,GuildBuffs.Offer offer){
        if(!guard(p,svc))return;GuildGui.Menu m=new GuildGui.Menu(3);
        boolean available=!svc.config.disabledBuffs.contains(offer.id());
        m.slot(11,button(available?Items.LIME_DYE:Items.GRAY_DYE,"§eEnabled: "+available,"§7Disable to remove this buff from the shop"),(pl,c)->{
            if(!guard(pl,svc))return;
            if(available)svc.config.disabledBuffs.add(offer.id());else svc.config.disabledBuffs.remove(offer.id());
            svc.config.save();buff(pl,svc,offer);
        });
        m.slot(15,button(Items.GOLD_INGOT,"§ePrice: $"+GuildBuffs.price(svc.config,offer),"§7Click to edit"),(pl,c)->prompt(pl,"a new positive CobbleDollars buff price",(a,v)->{
            if(!guard(a,svc))return;
            try{svc.config.buffPrices.put(offer.id(),money(v));svc.config.save();}
            catch(Exception ex){error(a,ex.getMessage());}buff(a,svc,offer);
        }));
        m.slot(22,button(Items.ARROW,"§7Back"),(pl,c)->buffs(pl,svc));m.open(p,"Buff · "+offer.label());
    }
}
