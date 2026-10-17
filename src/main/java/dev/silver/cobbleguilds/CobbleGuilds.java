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
import kotlin.Unit;
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
        // Guild tag chat-name prefixes are handled by PlayerManagerMixin. The signed
        // message body is never modified; no sidebar or client packet is required.
        GuildChatInput.register();
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->GuildCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            service=new GuildService(server);
            GuildBuffs.subscribe(service);
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
            if(service==null || event.getPlayer()==null)return Unit.INSTANCE;
            UUID id=event.getPlayer().getUuid();
            // Snapshot values before scheduling to the main thread; never inspect player storage later.
            var pokemon=event.getPokemon();
            String species=pokemon.getSpecies().getName().toLowerCase(java.util.Locale.ROOT);
            boolean shiny=pokemon.getShiny();
            String firstType=pokemon.getPrimaryType().getName().toLowerCase(java.util.Locale.ROOT);
            String secondType=pokemon.getSecondaryType()==null?"":pokemon.getSecondaryType().getName().toLowerCase(java.util.Locale.ROOT);
            service.server.execute(()->{
                if(service==null)return;
                var player=service.server.getPlayerManager().getPlayer(id);
                if(player!=null)service.captured(player,species,shiny,firstType,secondType);
            });
            return Unit.INSTANCE;
        });
    }
}
