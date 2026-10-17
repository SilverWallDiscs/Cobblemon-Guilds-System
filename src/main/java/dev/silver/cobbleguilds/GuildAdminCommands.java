package dev.silver.cobbleguilds;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import java.time.LocalDate;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Additional operator/LuckPerms controls, separate from guild membership and ranks. */
public final class GuildAdminCommands {
    private GuildAdminCommands() {}
    private static GuildService svc(){return CobbleGuilds.service();}
    private static void tell(ServerPlayerEntity player,String message){GuildService.tell(player,message);}
    private static Guild find(ServerPlayerEntity actor,String name){
        Guild guild=svc().store.named(name);
        if(guild==null)tell(actor,"Active guild not found. Disbanded guilds cannot be restored; use the Admin Archive menu.");
        return guild;
    }
    private static boolean can(ServerPlayerEntity actor,String permission){
        if(svc().isAdmin(actor,permission))return true;
        tell(actor,"Access denied: "+permission);
        return false;
    }
    private static boolean unique(Guild selected,String proposed){
        for(Guild other:svc().store.all()){
            if(other.closed || other==selected)continue;
            if(other.name.equalsIgnoreCase(proposed)||other.tag.equalsIgnoreCase(proposed))return false;
        }
        return true;
    }
    private static void log(Guild guild,ServerPlayerEntity actor,String action){
        guild.addAudit("ADMIN "+actor.getUuid()+" "+action);
        svc().store.save();
    }
    public static void attach(LiteralArgumentBuilder<ServerCommandSource> root){
        var edit=literal("edit");
        edit.then(argument("guild",StringArgumentType.word())
            .then(literal("name").then(argument("value",StringArgumentType.greedyString()).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                String value=StringArgumentType.getString(c,"value");
                if(!value.matches("[A-Za-z0-9 _-]{3,24}")||!unique(guild,value)){tell(actor,"Invalid or duplicate guild name (3-24 characters).");return 0;}
                String old=guild.name;guild.name=value;log(guild,actor,"changed name "+old+" -> "+value);tell(actor,"Guild name updated.");return 1;
            })))
            .then(literal("tag").then(argument("value",StringArgumentType.word()).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                String value=StringArgumentType.getString(c,"value");
                if(!value.matches("[A-Za-z0-9]{2,4}")||!unique(guild,value)){tell(actor,"Invalid or duplicate guild tag (2-4 characters).");return 0;}
                String old=guild.tag;guild.tag=value;log(guild,actor,"changed tag "+old+" -> "+value);tell(actor,"Guild tag updated.");return 1;
            })))
            .then(literal("description").then(argument("value",StringArgumentType.greedyString()).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                String value=StringArgumentType.getString(c,"value");
                if(value.length()>220){tell(actor,"Description limit: 220 characters.");return 0;}
                guild.description=value;log(guild,actor,"changed description");tell(actor,"Description updated.");return 1;
            })))
            .then(literal("color").then(argument("code",StringArgumentType.word()).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                String code=StringArgumentType.getString(c,"code").toLowerCase(Locale.ROOT).replace("&","").replace("§","");
                if(!code.matches("[0-9a-f]")){tell(actor,"Valid color codes: 0-9 or a-f.");return 0;}
                guild.color="§"+code;log(guild,actor,"changed color to &"+code);tell(actor,"Guild color updated.");return 1;
            })))
            .then(literal("public").executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                guild.publicGuild=!guild.publicGuild;log(guild,actor,"set guild public="+guild.publicGuild);
                tell(actor,"Guild visibility: "+(guild.publicGuild?"Public":"Application required"));return 1;
            }))
            .then(literal("icon").executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                if(actor.getMainHandStack().isEmpty()){tell(actor,"Hold an item to use as the icon.");return 0;}
                guild.icon=Registries.ITEM.getId(actor.getMainHandStack().getItem()).toString();
                log(guild,actor,"set icon to "+guild.icon);tell(actor,"Icon updated without consuming the item.");return 1;
            }))
            .then(literal("chestrows").then(argument("rows",IntegerArgumentType.integer(1,6)).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                int rows=IntegerArgumentType.getInteger(c,"rows");
                if(rows<guild.chestRows){tell(actor,"Shrinking a guild chest is blocked to prevent hidden/lost items.");return 0;}
                guild.chestRows=rows;log(guild,actor,"increased chest to "+rows+" rows");tell(actor,"Chest capacity updated.");return 1;
            })))
            .then(literal("waivefee").executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                guild.lastFeeDay=LocalDate.now(svc().zone()).toString();log(guild,actor,"waived today's guild maintenance fee");
                tell(actor,"Today's guild maintenance fee was waived.");return 1;
            }))
            .then(literal("rank").then(argument("player",EntityArgumentType.player()).then(argument("rank",StringArgumentType.word()).executes(c->{
                ServerPlayerEntity actor=c.getSource().getPlayerOrThrow();if(!can(actor,"cobbleguilds.admin.manage"))return 0;
                Guild guild=find(actor,StringArgumentType.getString(c,"guild"));if(guild==null)return 0;
                ServerPlayerEntity target=EntityArgumentType.getPlayer(c,"player");String rank=StringArgumentType.getString(c,"rank");
                if(!guild.contains(target.getUuid())||guild.isLeader(target.getUuid())||"leader".equals(rank)||!guild.ranks.containsKey(rank)){
                    tell(actor,"Target must be a non-leader guild member, and rank must already exist.");return 0;
                }
                guild.members.put(target.getUuidAsString(),rank);log(guild,actor,"assigned "+target.getUuid()+" to role "+rank);
                tell(actor,"Member rank updated.");return 1;
            }))))
        );
        root.then(literal("admin").then(edit));
    }
}
