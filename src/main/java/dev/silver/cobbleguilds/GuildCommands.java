package dev.silver.cobbleguilds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import static net.minecraft.server.command.CommandManager.*;

/** Few memorable player commands; all other gameplay runs in vanilla chest menus. */
public final class GuildCommands {
    private GuildCommands(){}
    private static GuildService service(){return CobbleGuilds.service();}
    private static ServerPlayerEntity player(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        return ctx.getSource().getPlayerOrThrow();
    }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher){
        dispatcher.register(literal("guilds").executes(ctx->{GuildGui.start(player(ctx),service());return 1;}));
        dispatcher.register(literal("g").then(argument("message",StringArgumentType.greedyString()).executes(ctx->{
            service().sendGuildChat(player(ctx),StringArgumentType.getString(ctx,"message"));return 1;
        })));
        dispatcher.register(literal("ga").then(argument("message",StringArgumentType.greedyString()).executes(ctx->{
            service().sendAllianceChat(player(ctx),StringArgumentType.getString(ctx,"message"));return 1;
        })));
        var root=literal("guild").executes(ctx->{GuildGui.start(player(ctx),service());return 1;});
        root.then(literal("home").executes(ctx->{GuildExtras.teleportHome(player(ctx));return 1;}));
        root.then(literal("effects").executes(ctx->{GuildBuffs.effects(service(),player(ctx));return 1;}));
        root.then(literal("buffs").executes(ctx->{GuildGui.buffsMenu(player(ctx),service());return 1;}));
        root.then(literal("quests").executes(ctx->{GuildGui.questsMenu(player(ctx),service());return 1;}));
        root.then(literal("announcement").executes(ctx->{GuildGui.announcementMenu(player(ctx),service());return 1;}));
        root.then(literal("chatspy").executes(ctx->{service().chatSpy(player(ctx));return 1;}));
        root.then(literal("admin").executes(ctx->{
            ServerPlayerEntity p=player(ctx);
            if(!service().isAdmin(p,"cobbleguilds.admin.gui")){GuildService.tell(p,"Access denied.");return 0;}
            GuildGui.browse(p,service(),0,true);return 1;
        }));
        // Staff may retain emergency commands; ordinary players do not see dozens of subcommands.
        GuildAdminCommands.attach(root);
        dispatcher.register(root);
    }
}
