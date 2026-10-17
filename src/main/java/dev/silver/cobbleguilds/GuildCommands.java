package dev.silver.cobbleguilds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import java.math.BigInteger;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Gameplay exposed via commands, no action bars, sidebars or bossbars. */
public final class GuildCommands {
    private GuildCommands(){}
    private static GuildService svc(){return CobbleGuilds.service();}
    private static ServerPlayerEntity p(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return ctx.getSource().getPlayerOrThrow();}
    private static int ok(){return 1;}
    public static void register(CommandDispatcher<ServerCommandSource> d){
        d.register(literal("guilds").executes(ctx->{GuildGui.start(p(ctx),svc());return ok();}));
        d.register(literal("g").then(argument("message",StringArgumentType.greedyString()).executes(ctx->{
            svc().sendGuildChat(p(ctx),StringArgumentType.getString(ctx,"message"));return ok();
        })));
        d.register(literal("ga").then(argument("message",StringArgumentType.greedyString()).executes(ctx->{
            svc().sendAllianceChat(p(ctx),StringArgumentType.getString(ctx,"message"));return ok();
        })));
        var root=literal("guild").executes(ctx->{GuildGui.start(p(ctx),svc());return ok();});
        root.then(literal("menu").executes(ctx->{GuildGui.start(p(ctx),svc());return ok();}));
        root.then(literal("browse").executes(ctx->{GuildGui.browse(p(ctx),svc(),0,false);return ok();}));
        root.then(literal("create").executes(ctx->{GuildGui.create(p(ctx),svc());return ok();}));
        root.then(literal("draft")
            .then(literal("name").then(argument("text",StringArgumentType.greedyString()).executes(ctx->{
                ServerPlayerEntity player=p(ctx);String s=StringArgumentType.getString(ctx,"text");
                if(s.length()>24){GuildService.tell(player,"Maximum name length: 24.");return 0;}
                GuildGui.draft(player).name=s;GuildGui.create(player,svc());return ok();
            })))
            .then(literal("tag").then(argument("tag",StringArgumentType.word()).executes(ctx->{
                ServerPlayerEntity player=p(ctx);String tag=StringArgumentType.getString(ctx,"tag");
                if(!tag.matches("[A-Za-z0-9]{2,4}")){GuildService.tell(player,"Tag must be 2-4 letters/numbers.");return 0;}
                GuildGui.draft(player).tag=tag;GuildGui.create(player,svc());return ok();
            })))
            .then(literal("description").then(argument("text",StringArgumentType.greedyString()).executes(ctx->{
                ServerPlayerEntity player=p(ctx);String desc=StringArgumentType.getString(ctx,"text");
                if(desc.length()>220){GuildService.tell(player,"Description maximum: 220 characters.");return 0;}
                GuildGui.draft(player).description=desc;GuildGui.create(player,svc());return ok();
            }))));
        root.then(literal("join").then(argument("guild",StringArgumentType.greedyString()).executes(ctx->{
            svc().join(p(ctx),StringArgumentType.getString(ctx,"guild"));return ok();
        })));
        root.then(literal("leave").executes(ctx->{svc().leave(p(ctx));return ok();}));
        root.then(literal("invite").then(argument("player",EntityArgumentType.player()).executes(ctx->{
            svc().invite(p(ctx),EntityArgumentType.getPlayer(ctx,"player"));return ok();
        })));
        root.then(literal("accept").then(argument("player",EntityArgumentType.player()).executes(ctx->{
            svc().accept(p(ctx),EntityArgumentType.getPlayer(ctx,"player"));return ok();
        })));
        root.then(literal("disband").then(literal("confirm").executes(ctx->{
            ServerPlayerEntity player=p(ctx);svc().disband(player,false,svc().mine(player));return ok();
        })));
        root.then(literal("bank")
            .then(literal("balance").executes(ctx->{
                ServerPlayerEntity player=p(ctx);Guild g=svc().mine(player);
                GuildService.tell(player,g==null?"You are not in a guild.":"Guild Bank: $"+g.bank);return ok();
            }))
            .then(literal("deposit").then(argument("amount",StringArgumentType.word()).executes(ctx->{
                ServerPlayerEntity player=p(ctx);try{svc().bankDeposit(player,GuildMoney.amount(StringArgumentType.getString(ctx,"amount")));}
                catch(IllegalArgumentException ex){GuildService.tell(player,ex.getMessage());}return ok();
            })))
            .then(literal("withdraw").then(argument("amount",StringArgumentType.word()).executes(ctx->{
                ServerPlayerEntity player=p(ctx);try{svc().bankWithdraw(player,GuildMoney.amount(StringArgumentType.getString(ctx,"amount")),false,svc().mine(player));}
                catch(IllegalArgumentException ex){GuildService.tell(player,ex.getMessage());}return ok();
            }))));
        root.then(literal("chest").executes(ctx->{
            ServerPlayerEntity player=p(ctx);Guild g=svc().mine(player);
            if(g==null)GuildService.tell(player,"You are not in a guild.");
            else GuildMenus.openChest(player,svc(),g,false);return ok();
        }).then(literal("upgrade").executes(ctx->{svc().upgradeChest(p(ctx));return ok();})));
        root.then(literal("quests").executes(ctx->{svc().listQuests(p(ctx));return ok();}));
        root.then(literal("bingo").executes(ctx->{ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(g==null){GuildService.tell(pl,"You are not in a guild.");return 0;}
            GuildGui.bingo(pl,svc(),g,false);return ok();}));
        root.then(literal("quest")
            .executes(ctx->{svc().listQuests(p(ctx));return ok();})
            .then(literal("claim").executes(ctx->{svc().claim(p(ctx));return ok();})));
        root.then(literal("effects").executes(ctx->{
            ServerPlayerEntity player=p(ctx);Guild g=svc().mine(player);
            if(g==null){GuildService.tell(player,"You are not in a guild.");return 0;}
            if(g.effects.isEmpty())GuildService.tell(player,"No active guild buffs. Buff purchases are not enabled in this alpha.");
            else for(Guild.Effect fx:g.effects.values())GuildService.tell(player,fx.id+": "+(fx.permanent?"Permanent":((fx.expiresAt-System.currentTimeMillis())/60000)+"m remaining"));
            return ok();
        }));
        root.then(literal("buff").then(literal("buy").then(argument("id",StringArgumentType.word()).executes(ctx->{
            GuildService.tell(p(ctx),"Buff purchases are temporarily disabled: no CobbleDollars charged. Cobblemon effect hooks require verification.");return 0;
        }))));
        root.then(literal("top").then(literal("captures").executes(ctx->{
            ServerPlayerEntity player=p(ctx);
            List<Guild> top=svc().store.all().stream().filter(g->!g.closed).sorted((a,b)->Long.compare(b.totalCaptures,a.totalCaptures)).limit(10).toList();
            GuildService.tell(player,"Guild Leaderboard (Captures):");int place=1;
            for(Guild g:top)GuildService.tell(player,(place++)+". "+g.name+" ["+g.tag+"] - "+g.totalCaptures+" captures");return ok();
        })).executes(ctx->{
            ServerPlayerEntity player=p(ctx);
            List<Guild> top=svc().store.all().stream().filter(g->!g.closed).sorted((a,b)->Long.compare(b.experience,a.experience)).limit(10).toList();
            GuildService.tell(player,"Guild Leaderboard (Experience):");int place=1;
            for(Guild g:top)GuildService.tell(player,(place++)+". "+g.name+" ["+g.tag+"] - "+g.experience+" XP");return ok();
        }));
        root.then(literal("settings")
            .then(literal("public").executes(ctx->{return togglePrivacy(p(ctx));}))
            .then(literal("name").then(argument("name",StringArgumentType.greedyString()).executes(ctx->{
                ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
                if(!canEditSettings(pl,g))return 0;
                String value=StringArgumentType.getString(ctx,"name");
                if(!value.matches("[A-Za-z0-9 _-]{3,24}")||(svc().store.named(value)!=null && svc().store.named(value)!=g)){GuildService.tell(pl,"Invalid or already used guild name.");return 0;}
                g.name=value;g.addAudit(pl.getName().getString()+" changed guild name to "+value);svc().store.save();GuildService.tell(pl,"Guild name updated.");return ok();
            })))
            .then(literal("tag").then(argument("tag",StringArgumentType.word()).executes(ctx->{
                ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);if(!canEditSettings(pl,g))return 0;
                String value=StringArgumentType.getString(ctx,"tag");
                if(!value.matches("[A-Za-z0-9]{2,4}")||(svc().store.named(value)!=null && svc().store.named(value)!=g)){GuildService.tell(pl,"Invalid or already used guild tag.");return 0;}
                g.tag=value;g.addAudit(pl.getName().getString()+" changed guild tag to "+value);svc().store.save();GuildService.tell(pl,"Guild tag updated.");return ok();
            })))
            .then(literal("description").then(argument("text",StringArgumentType.greedyString()).executes(ctx->{
                ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);if(!canEditSettings(pl,g))return 0;
                String value=StringArgumentType.getString(ctx,"text");
                if(value.length()>220){GuildService.tell(pl,"Description is too long (220 max).");return 0;}
                g.description=value;svc().store.save();GuildService.tell(pl,"Guild description updated.");return ok();
            }))));
        root.then(literal("kick").then(argument("player",EntityArgumentType.player()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx),target=EntityArgumentType.getPlayer(ctx,"player");Guild g=svc().mine(pl);
            if(g==null||!g.allowed(pl.getUuid(),"kick")){GuildService.tell(pl,"No kick permission.");return 0;}
            if(!g.contains(target.getUuid())||pl.getUuid().equals(target.getUuid())||g.rank(target.getUuid()).priority>=g.rank(pl.getUuid()).priority){
                GuildService.tell(pl,"Cannot kick someone with equal or higher priority.");return 0;
            }
            g.members.remove(target.getUuidAsString());g.addAudit(pl.getName().getString()+" kicked "+target.getName().getString());svc().store.save();
            GuildService.tell(pl,"Player removed from guild.");GuildService.tell(target,"You were removed from "+g.name);return ok();
        })));
        registerRoles(root);
        registerAdmin(root);
        GuildExtras.attach(root);
        GuildAdminCommands.attach(root);
        d.register(root);
    }
    private static boolean canEditSettings(ServerPlayerEntity pl,Guild g){
        if(g!=null && g.allowed(pl.getUuid(),"manage_settings"))return true;
        GuildService.tell(pl,"No permission to edit guild settings.");return false;
    }
    private static int togglePrivacy(ServerPlayerEntity pl){
        Guild g=svc().mine(pl);if(!canEditSettings(pl,g))return 0;
        g.publicGuild=!g.publicGuild;svc().store.save();
        GuildService.tell(pl,"Guild privacy is now "+(g.publicGuild?"PUBLIC":"APPLICATION ONLY"));return ok();
    }
    private static boolean mayManageRole(ServerPlayerEntity player,Guild g){return g!=null&&g.allowed(player.getUuid(),"manage_ranks");}
    private static void registerRoles(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root){
        var roles=literal("role");
        roles.then(literal("list").executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(g==null){GuildService.tell(pl,"You are not in a guild.");return 0;}
            for(var e:g.ranks.entrySet())GuildService.tell(pl,e.getKey()+" ("+e.getValue().name+") priority "+e.getValue().priority+(e.getValue().isDefault?" [default]":""));return ok();
        }));
        roles.then(literal("create").then(argument("id",StringArgumentType.word()).then(argument("priority",IntegerArgumentType.integer(0,1000)).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No permission to manage ranks.");return 0;}
            String id=StringArgumentType.getString(ctx,"id").toLowerCase(Locale.ROOT);
            int prio=IntegerArgumentType.getInteger(ctx,"priority");
            Guild.Rank mine=g.rank(pl.getUuid());
            if(!id.matches("[a-z0-9_]{1,12}")||g.ranks.containsKey(id)||prio>=mine.priority||id.equals("leader")){
                GuildService.tell(pl,"Invalid rank name, duplicate ID or priority.");return 0;
            }
            g.ranks.put(id,new Guild.Rank(id,prio,false));g.addAudit(pl.getName().getString()+" created role "+id);svc().store.save();
            GuildService.tell(pl,"Created role "+id+".");return ok();
        }))));
        roles.then(literal("rename").then(argument("id",StringArgumentType.word()).then(argument("name",StringArgumentType.greedyString()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No rank management permission.");return 0;}
            String id=StringArgumentType.getString(ctx,"id"),name=StringArgumentType.getString(ctx,"name");
            Guild.Rank target=g.ranks.get(id);
            if(target==null||!name.matches("[A-Za-z0-9 _-]{1,12}")||(!g.isLeader(pl.getUuid())&&(id.equals("leader")||target.priority>=g.rank(pl.getUuid()).priority))){
                GuildService.tell(pl,"Cannot rename that rank.");return 0;}
            target.name=name;svc().store.save();GuildService.tell(pl,"Rank renamed to "+name);return ok();
        }))));
        roles.then(literal("default").then(argument("id",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No permission to manage ranks.");return 0;}
            String id=StringArgumentType.getString(ctx,"id");Guild.Rank target=g.ranks.get(id);
            if(target==null||id.equals("leader")||target.priority>=g.rank(pl.getUuid()).priority){GuildService.tell(pl,"Invalid default role.");return 0;}
            g.ranks.values().forEach(r->r.isDefault=false);target.isDefault=true;svc().store.save();
            GuildService.tell(pl,"Default role is now "+id+".");return ok();
        })));
        roles.then(literal("grant").then(argument("id",StringArgumentType.word()).then(argument("permission",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No permission to manage ranks.");return 0;}
            String id=StringArgumentType.getString(ctx,"id"),permission=StringArgumentType.getString(ctx,"permission");
            Guild.Rank target=g.ranks.get(id);
            if(target==null||id.equals("leader")||target.priority>=g.rank(pl.getUuid()).priority||!PERMS.contains(permission) ||(!g.isLeader(pl.getUuid())&&!g.allowed(pl.getUuid(),permission))){
                GuildService.tell(pl,"Permission grant denied.");return 0;
            }
            target.permissions.add(permission);svc().store.save();GuildService.tell(pl,"Granted "+permission+" to "+id);return ok();
        }))));
        roles.then(literal("revoke").then(argument("id",StringArgumentType.word()).then(argument("permission",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No permission to manage ranks.");return 0;}
            String id=StringArgumentType.getString(ctx,"id"),permission=StringArgumentType.getString(ctx,"permission");
            Guild.Rank target=g.ranks.get(id);
            if(target==null||id.equals("leader")||target.priority>=g.rank(pl.getUuid()).priority){GuildService.tell(pl,"Permission revoke denied.");return 0;}
            target.permissions.remove(permission);svc().store.save();GuildService.tell(pl,"Revoked "+permission+" from "+id);return ok();
        }))));
        roles.then(literal("assign").then(argument("player",EntityArgumentType.player()).then(argument("id",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx),target=EntityArgumentType.getPlayer(ctx,"player");Guild g=svc().mine(pl);
            if(!mayManageRole(pl,g)){GuildService.tell(pl,"No permission to manage ranks.");return 0;}
            String id=StringArgumentType.getString(ctx,"id");Guild.Rank next=g.ranks.get(id);
            Guild.Rank actor=g.rank(pl.getUuid()), old=g.rank(target.getUuid());
            if(next==null||!g.contains(target.getUuid())||pl.getUuid().equals(target.getUuid())||old==null||id.equals("leader")||next.priority>=actor.priority||old.priority>=actor.priority){
                GuildService.tell(pl,"Cannot assign that role: priority or membership restriction.");return 0;
            }
            g.members.put(target.getUuidAsString(),id);g.addAudit(pl.getName().getString()+" assigned "+id+" to "+target.getName().getString());svc().store.save();
            GuildService.tell(target,"Your guild role is now "+next.name);return ok();
        }))));
        root.then(roles);
    }
    private static final Set<String> PERMS=Set.of("chest","bank_withdraw","manage_ranks","kick","manage_applications","manage_settings","purchase_buffs","upgrade_chest","manage_members","manage_home","manage_upgrades");
    private static void registerAdmin(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root){
        var admin=literal("admin").executes(ctx->{
            ServerPlayerEntity pl=p(ctx);
            if(!svc().isAdmin(pl,"cobbleguilds.admin.gui")){GuildService.tell(pl,"Access denied.");return 0;}
            GuildGui.browse(pl,svc(),0,true);return ok();
        });
        admin.then(literal("open").then(argument("guild",StringArgumentType.greedyString()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);
            if(!svc().isAdmin(pl,"cobbleguilds.admin.gui")){GuildService.tell(pl,"Access denied.");return 0;}
            Guild g=svc().store.named(StringArgumentType.getString(ctx,"guild"));
            if(g==null){GuildService.tell(pl,"Guild not found.");return 0;}
            GuildGui.guild(pl,svc(),g,true);return ok();
        })));
        admin.then(literal("logs").then(argument("guild",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);
            if(!svc().isAdmin(pl,"cobbleguilds.admin.logs")){GuildService.tell(pl,"Access denied.");return 0;}
            Guild g=svc().store.named(StringArgumentType.getString(ctx,"guild"));
            if(g==null){GuildService.tell(pl,"Guild not found.");return 0;}
            g.audit.stream().skip(Math.max(0,g.audit.size()-10)).forEach(s->GuildService.tell(pl,s));return ok();
        })));
        admin.then(literal("kick").then(argument("guild",StringArgumentType.word()).then(argument("player",EntityArgumentType.player()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx),target=EntityArgumentType.getPlayer(ctx,"player");
            if(!svc().isAdmin(pl,"cobbleguilds.admin.moderation")){GuildService.tell(pl,"Access denied.");return 0;}
            Guild g=svc().store.named(StringArgumentType.getString(ctx,"guild"));
            if(g==null||!g.contains(target.getUuid())){GuildService.tell(pl,"Player is not in that guild.");return 0;}
            if(g.isLeader(target.getUuid())){GuildService.tell(pl,"Transfer leadership before removing the leader.");return 0;}
            g.members.remove(target.getUuidAsString());g.addAudit(pl.getName().getString()+" admin-kicked "+target.getName().getString());svc().store.save();
            GuildService.tell(target,"You were removed from guild "+g.name);return ok();
        }))));
        admin.then(literal("ban").then(argument("guild",StringArgumentType.word()).then(argument("player",EntityArgumentType.player()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx),target=EntityArgumentType.getPlayer(ctx,"player");
            if(!svc().isAdmin(pl,"cobbleguilds.admin.moderation")){GuildService.tell(pl,"Access denied.");return 0;}
            Guild g=svc().store.named(StringArgumentType.getString(ctx,"guild"));if(g==null){GuildService.tell(pl,"Guild not found.");return 0;}
            if(g.isLeader(target.getUuid())){GuildService.tell(pl,"Transfer leadership first.");return 0;}
            g.members.remove(target.getUuidAsString());g.pending.remove(target.getUuidAsString());g.invitations.remove(target.getUuidAsString());g.ordinaryInvites.remove(target.getUuidAsString());
            g.banned.add(target.getUuidAsString());g.banReasons.put(target.getUuidAsString(),"Banned by admin without a reason");g.addAudit(pl.getName().getString()+" admin-banned "+target.getName().getString());svc().store.save();
            GuildService.tell(pl,"Player banned from "+g.name);return ok();
        }))));
        admin.then(literal("unban").then(argument("guild",StringArgumentType.word()).then(argument("uuid",StringArgumentType.word()).executes(ctx->{
            ServerPlayerEntity pl=p(ctx);
            if(!svc().isAdmin(pl,"cobbleguilds.admin.moderation")){GuildService.tell(pl,"Access denied.");return 0;}
            Guild g=svc().store.named(StringArgumentType.getString(ctx,"guild"));if(g==null){GuildService.tell(pl,"Guild not found.");return 0;}
            try{UUID uuid=UUID.fromString(StringArgumentType.getString(ctx,"uuid"));g.banned.remove(uuid.toString());g.banReasons.remove(uuid.toString());
                g.addAudit(pl.getName().getString()+" admin-unbanned "+uuid);svc().store.save();GuildService.tell(pl,"Guild ban removed.");}
            catch(IllegalArgumentException ex){GuildService.tell(pl,"Use the player's UUID.");}return ok();
        }))));
        root.then(admin);
        root.then(literal("chatspy").executes(ctx->{svc().chatSpy(p(ctx));return ok();}));
    }
}
