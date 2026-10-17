package dev.silver.cobbleguilds;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Never loads any net.minecraft.client class; installed only on a dedicated server. */
public final class CobbleGuilds implements ModInitializer {
    public static final String MOD_ID="cobbleguilds";
    public static final Logger LOG=LoggerFactory.getLogger("Cobblemon Guilds System");
    private static GuildService service;
    private static GuildChest chests;
    public static GuildService service(){
        if(service==null)throw new IllegalStateException("CobbleGuilds not started");
        return service;
    }
    @Override public void onInitialize(){
        // Signed-chat-safe decoration. Prefixes the MESSAGE, allowing other chat plugins to retain the sender/rank.
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE,(sender,message)->{
            if(sender==null || service==null)return message;
            Guild g=service.store.member(sender.getUuid());
            if(g==null)return message;
            char code=g.color!=null && g.color.matches("§[0-9a-f]")?g.color.charAt(1):'7';
            Formatting format=Formatting.byCode(code);
            return Text.literal("["+g.tag+"] ").formatted(format==null?Formatting.GRAY:format).append(message);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->GuildCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            service=new GuildService(server);
            chests=new GuildChest(server,service.store);
            GuildMenus.init(chests);
            LOG.info("CobbleGuilds loaded {} guilds - server only, no sidebar/HUD",service.store.all().size());
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{if(service!=null)service.onTick();});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            if(chests!=null)chests.saveAll();
            if(service!=null)service.store.save();
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL,event->{
            if(service==null || event.getPlayer()==null)return;
            UUID id=event.getPlayer().getUuid();
            service.server.execute(()->{
                if(service==null)return;
                var player=service.server.getPlayerManager().getPlayer(id);
                if(player!=null)service.captured(player);
            });
        });
    }
}
