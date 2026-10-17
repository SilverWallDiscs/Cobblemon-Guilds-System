package dev.silver.cobbleguilds;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import java.math.BigInteger;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Extended guild gameplay, all permissions verified server-side at execution. */
public final class GuildExtras {
    private GuildExtras() {}
    private static GuildService svc(){return CobbleGuilds.service();}
    private static void tell(ServerPlayerEntity p,String text){GuildService.tell(p,text);}
    private static Guild mine(ServerPlayerEntity p){Guild g=svc().mine(p);if(g==null)tell(p,"You are not in a guild.");return g;}
    private static boolean allowed(ServerPlayerEntity p,Guild g,String permission){
        if(g==null || !g.allowed(p.getUuid(),permission)){tell(p,"No guild permission: "+permission);return false;}
        return true;
    }
    public static void attach(LiteralArgumentBuilder<ServerCommandSource> root){
        root.then(literal("stats").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            tell(p,g.name+" ["+g.tag+"] | Level "+g.level+" | XP "+g.experience+" | Members "+g.members.size()+"/"+g.capacity(svc().config.maxGuildMembers)+" | Bank $"+g.bank);
            tell(p,"Daily quest streak: "+g.questStreak+" | Chest: "+(g.chestRows*9)+" slots | Fee $"+svc().config.dailyFeePrice);return 1;
        }));
        root.then(literal("announcement").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            tell(p,g.announcement.isEmpty()?"No guild announcement is set.":"Announcement: "+g.announcement);return 1;
        }).then(literal("set").then(argument("message",StringArgumentType.greedyString()).executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(!allowed(p,g,"manage_settings"))return 0;
            if(!svc().config.announcementsEnabled)return 0;
            String message=StringArgumentType.getString(c,"message").replace('§',' ').trim();
            if(message.length()>220){tell(p,"Announcement limit is 220 characters.");return 0;}
            g.announcement=message;g.addAudit(p.getUuid()+" changed announcement");svc().store.save();
            for(var member:svc().server.getPlayerManager().getPlayerList())if(g.contains(member.getUuid()))tell(member,"Guild Announcement: "+message);
            return 1;
        }))));
        root.then(literal("sethome").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(!allowed(p,g,"manage_home"))return 0;
            if(!svc().config.guildHomesEnabled){tell(p,"Guild homes are disabled.");return 0;}
            g.homeWorld=p.getServerWorld().getRegistryKey().getValue().toString();
            g.homeX=p.getX();g.homeY=p.getY();g.homeZ=p.getZ();g.homeYaw=p.getYaw();g.homePitch=p.getPitch();
            g.addAudit(p.getUuid()+" set guild home in "+g.homeWorld);svc().store.save();tell(p,"Guild home saved.");return 1;
        }));
        root.then(literal("home").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            if(!svc().config.guildHomesEnabled || g.homeWorld.isBlank()){tell(p,"Your guild has not set a home.");return 0;}
            ServerWorld world=svc().server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(g.homeWorld)));
            if(world==null){tell(p,"Guild home world is unavailable.");return 0;}
            long now=System.currentTimeMillis();Long last=lastHomeUse.get(p.getUuid());
            if(last!=null && now-last<(long)svc().config.guildHomeCooldownSeconds*1000){tell(p,"Guild home cooldown: "+((svc().config.guildHomeCooldownSeconds*1000-(now-last))/1000)+" seconds.");return 0;}
            if(!world.getWorldBorder().contains(net.minecraft.util.math.BlockPos.ofFloored(g.homeX,g.homeY,g.homeZ))){tell(p,"Guild home is outside the world border.");return 0;}
            p.teleport(world,g.homeX,g.homeY,g.homeZ,g.homeYaw,g.homePitch);
            lastHomeUse.put(p.getUuid(),now);tell(p,"Teleported to the guild home.");return 1;
        }));
        root.then(literal("upgrade").then(literal("members").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(!allowed(p,g,"manage_upgrades"))return 0;
            GuildConfig config=svc().config;if(!config.upgradesEnabled){tell(p,"Upgrades are disabled.");return 0;}
            if(g.capacity(config.maxGuildMembers)>=100){tell(p,"Guild is at the 100-member maximum.");return 0;}
            BigInteger cost=BigInteger.valueOf(config.guildMemberUpgradeCost);
            if(cost.signum()<=0 || g.funds().compareTo(cost)<0){tell(p,"Guild bank needs $"+cost+" to upgrade member capacity.");return 0;}
            g.bank=g.funds().subtract(cost).toString();
            g.memberSlotsBonus=Math.min(100,g.memberSlotsBonus+Math.max(1,config.guildMemberUpgradeIncrement));
            g.addAudit(p.getUuid()+" purchased member capacity +"+config.guildMemberUpgradeIncrement+" for $"+cost);
            svc().store.save();tell(p,"Member capacity increased to "+g.capacity(config.maxGuildMembers));return 1;
        })));
        root.then(literal("leader")
            .then(literal("transfer").then(argument("player",EntityArgumentType.player()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();var target=EntityArgumentType.getPlayer(c,"player");Guild g=mine(p);
                if(g==null || !g.isLeader(p.getUuid()) || !g.contains(target.getUuid()) || p.getUuid().equals(target.getUuid())){
                    tell(p,"Only the leader can nominate a different guild member.");return 0;
                }
                g.pendingLeader=target.getUuidAsString();g.leadershipTokenExpiresAt=System.currentTimeMillis()+60000L;
                svc().store.save();tell(p,"Leadership transfer requested to "+target.getName().getString()+". Confirm within 60 seconds using /guild leader confirm ImSure.");return 1;
            })))
            .then(literal("confirm").then(argument("phrase",StringArgumentType.word()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);
                if(g==null || !g.isLeader(p.getUuid()) || !"ImSure".equals(StringArgumentType.getString(c,"phrase")) || g.leadershipTokenExpiresAt<System.currentTimeMillis()){
                    tell(p,"Transfer was not confirmed. Use /guild leader transfer <player> first.");return 0;
                }
                try {
                    UUID newLeader=UUID.fromString(g.pendingLeader);
                    if(!g.contains(newLeader) || p.getUuid().equals(newLeader))return 0;
                    g.members.put(p.getUuidAsString(),g.defaultRank());g.members.put(newLeader.toString(),"leader");g.leader=newLeader.toString();
                    g.pendingLeader="";g.leadershipTokenExpiresAt=0L;
                    g.addAudit(p.getUuid()+" transferred leadership to "+newLeader);svc().store.save();
                    tell(p,"Guild leadership transferred successfully.");return 1;
                }catch(IllegalArgumentException ex){tell(p,"Transfer destination is invalid.");return 0;}
            }))));
        root.then(literal("settings")
            .then(literal("color").then(argument("code",StringArgumentType.word()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(!allowed(p,g,"manage_settings"))return 0;
                String code=StringArgumentType.getString(c,"code").toLowerCase(Locale.ROOT).replace("&","").replace("§","");
                if(!code.matches("[0-9a-f]")){tell(p,"Use a valid Minecraft color code such as b or c.");return 0;}
                g.color="§"+code;g.addAudit(p.getUuid()+" changed guild color to "+code.replace('§','&'));svc().store.save();tell(p,"Guild color updated.");return 1;
            })))
            .then(literal("icon").executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(!allowed(p,g,"manage_settings"))return 0;
                ItemStack item=p.getMainHandStack();if(item.isEmpty()){tell(p,"Hold an item to set the guild icon. The item will not be consumed.");return 0;}
                g.icon=Registries.ITEM.getId(item.getItem()).toString();g.addAudit(p.getUuid()+" changed icon to "+g.icon);svc().store.save();tell(p,"Guild icon updated. Item kept.");return 1;
            })));
        root.then(literal("alliances").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            tell(p,"Allied guilds: "+g.allies.size()+"/5");
            for(String id:g.allies){Guild ally=svc().store.byId(id);
                if(ally!=null && !ally.closed && ally.allies.contains(g.id))tell(p,ally.name+" ["+ally.tag+"]");}
            tell(p,"Incoming requests: "+g.incomingAlliances.size());
            for(String id:g.incomingAlliances){Guild requested=svc().store.byId(id);
                if(requested!=null && !requested.closed)tell(p,"Pending: "+requested.name);}
            return 1;
        }));
        root.then(literal("ally")
            .then(literal("request").then(argument("guild",StringArgumentType.greedyString()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null || !g.isLeader(p.getUuid())){tell(p,"Only a guild leader can request an alliance.");return 0;}
                Guild other=svc().store.named(StringArgumentType.getString(c,"guild"));
                if(other==null || other==g || g.allies.size()>=5 || other.allies.size()>=5 || other.allies.contains(g.id)){
                    tell(p,"Invalid alliance target or alliance limit reached.");return 0;
                }
                other.incomingAlliances.add(g.id);
                other.addAudit("Alliance request from "+g.id);svc().store.save();tell(p,"Alliance request sent to "+other.name);return 1;
            })))
            .then(literal("accept").then(argument("guild",StringArgumentType.greedyString()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null || !g.isLeader(p.getUuid()))return 0;
                Guild other=svc().store.named(StringArgumentType.getString(c,"guild"));
                if(other==null || !g.incomingAlliances.contains(other.id) || g.allies.size()>=5 || other.allies.size()>=5){
                    tell(p,"No valid pending alliance request from that guild.");return 0;
                }
                g.incomingAlliances.remove(other.id);g.allies.add(other.id);other.allies.add(g.id);
                g.addAudit("Alliance with "+other.id);other.addAudit("Alliance with "+g.id);
                svc().store.save();tell(p,"Alliance formed with "+other.name+". Use /ga <message> to talk.");return 1;
            })))
            .then(literal("remove").then(argument("guild",StringArgumentType.greedyString()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null || !g.isLeader(p.getUuid()))return 0;
                Guild other=svc().store.named(StringArgumentType.getString(c,"guild"));
                if(other==null || !g.allies.remove(other.id)){tell(p,"Not an allied guild.");return 0;}
                other.allies.remove(g.id);g.addAudit("Removed alliance with "+other.id);
                other.addAudit("Alliance removed by "+g.id);svc().store.save();tell(p,"Alliance removed.");return 1;
            }))));
        root.then(literal("achievements").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            tell(p,"Guild Achievements: "+g.achievements.size());
            for(String achievement:g.achievements)tell(p,"★ "+achievement);
            if(g.achievements.isEmpty())tell(p,"No achievements unlocked yet.");return 1;
        }));
        root.then(literal("challenge").executes(c->{
            var p=c.getSource().getPlayerOrThrow();String week=svc().currentWeek();
            tell(p,"Weekly Guild Capture Challenge ("+week+"):");
            var ranking=svc().store.all().stream().filter(g->!g.closed)
                .sorted((a,b)->Long.compare(b.weeklyCapturePeriod.equals(week)?b.weeklyCaptures:0,a.weeklyCapturePeriod.equals(week)?a.weeklyCaptures:0))
                .limit(10).toList();
            int position=1;
            for(Guild g:ranking)tell(p,(position++)+". "+g.name+" - "+(week.equals(g.weeklyCapturePeriod)?g.weeklyCaptures:0)+" captures");
            return 1;
        }));
        root.then(literal("milestones").executes(c->{
            var p=c.getSource().getPlayerOrThrow();Guild g=mine(p);if(g==null)return 0;
            long next=g.totalCaptures<100?100:g.totalCaptures<1000?1000:g.totalCaptures<10000?10000:100000;
            tell(p,"Lifetime Pokémon Captures: "+g.totalCaptures+" | Next milestone: "+next);
            tell(p,"Quests completed: "+g.questsCompleted+" | Guild level: "+g.level);
            return 1;
        }));
        // Administrative operational tools. Each subcommand verifies its own LuckPerms node.
        root.then(literal("admin").then(literal("restore").then(argument("id",StringArgumentType.word()).executes(c->{
            var p=c.getSource().getPlayerOrThrow();if(!svc().isAdmin(p,"cobbleguilds.admin.manage")){tell(p,"Access denied.");return 0;}
            String id=StringArgumentType.getString(c,"id");
            Guild g=svc().store.byId(id);
            if(g==null || !g.closed){tell(p,"Use the archived guild ID. Only disbanded guilds can be restored.");return 0;}
            g.closed=false;g.lastFeeDay=java.time.LocalDate.now(svc().zone()).toString();
            g.addAudit("ADMIN "+p.getUuid()+" restored guild, treasury and chest preserved");svc().store.save();
            tell(p,"Guild restored: "+g.name+" ["+g.id+"]");return 1;
        }))));
        root.then(literal("admin")
            .then(literal("reason").then(argument("guild",StringArgumentType.word()).then(argument("uuid",StringArgumentType.word()).then(argument("reason",StringArgumentType.greedyString()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();if(!svc().isAdmin(p,"cobbleguilds.admin.moderation")){tell(p,"Access denied.");return 0;}
                Guild g=svc().store.named(StringArgumentType.getString(c,"guild"));if(g==null)return 0;
                String id=StringArgumentType.getString(c,"uuid");try{UUID.fromString(id);}catch(IllegalArgumentException e){tell(p,"Use a valid UUID.");return 0;}
                if(!g.banned.contains(id)){tell(p,"That player is not banned from this guild.");return 0;}
                String reason=StringArgumentType.getString(c,"reason").replace('§',' ').trim();
                if(reason.isEmpty()||reason.length()>160){tell(p,"Reason must be 1-160 characters.");return 0;}
                g.banReasons.put(id,reason);g.addAudit("ADMIN "+p.getUuid()+" updated ban reason for "+id);
                svc().store.save();tell(p,"Guild ban reason recorded.");return 1;
            })))))
            .then(literal("bank").then(argument("guild",StringArgumentType.word()).then(argument("amount",StringArgumentType.word()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();if(!svc().isAdmin(p,"cobbleguilds.admin.bank")){tell(p,"Access denied.");return 0;}
                Guild g=svc().store.named(StringArgumentType.getString(c,"guild"));if(g==null)return 0;
                try{String amount=StringArgumentType.getString(c,"amount");boolean negative=amount.startsWith("-");
                    BigInteger delta=GuildMoney.amount(negative?amount.substring(1):amount);
                    if(negative)delta=delta.negate();BigInteger next=g.funds().add(delta);
                    if(next.signum()<0 || next.bitLength()>256){tell(p,"Invalid resulting guild balance.");return 0;}
                    String prev=g.bank;g.bank=next.toString();g.addAudit("ADMIN "+p.getUuid()+" changed bank $"+prev+" -> $"+g.bank);
                    svc().store.save();tell(p,"Guild bank updated to $"+g.bank);return 1;
                }catch(IllegalArgumentException ex){tell(p,"Invalid monetary amount.");return 0;}
            }))))
            .then(literal("invite").then(argument("guild",StringArgumentType.word()).then(argument("player",EntityArgumentType.player()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();if(!svc().isAdmin(p,"cobbleguilds.admin.manage")){tell(p,"Access denied.");return 0;}
                Guild g=svc().store.named(StringArgumentType.getString(c,"guild"));var target=EntityArgumentType.getPlayer(c,"player");
                if(g==null || svc().mine(target)!=null || g.banned.contains(target.getUuidAsString()) || g.members.size()>=g.capacity(svc().config.maxGuildMembers)){
                    tell(p,"Cannot add this player to the guild.");return 0;
                }
                g.pending.remove(target.getUuidAsString());g.ordinaryInvites.remove(target.getUuidAsString());g.invitations.remove(target.getUuidAsString());
                g.members.put(target.getUuidAsString(),g.defaultRank());g.addAudit("ADMIN "+p.getUuid()+" directly added "+target.getUuid());
                svc().store.save();tell(p,"Player added successfully.");tell(target,"An administrator added you to "+g.name);return 1;
            }))))
            .then(literal("bans").then(argument("guild",StringArgumentType.word()).executes(c->{
                var p=c.getSource().getPlayerOrThrow();if(!svc().isAdmin(p,"cobbleguilds.admin.moderation")){tell(p,"Access denied.");return 0;}
                Guild g=svc().store.named(StringArgumentType.getString(c,"guild"));if(g==null)return 0;
                tell(p,"Guild bans for "+g.name+":");for(String id:g.banned)tell(p,id+" - "+g.banReasons.getOrDefault(id,"No reason recorded"));return 1;
            }))));
    }
    private static final Map<UUID,Long> lastHomeUse=new HashMap<>();
}
