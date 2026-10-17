package dev.silver.cobbleguilds;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.function.BiConsumer;

/** One-shot server-side chat prompts. The submitted answer is never broadcast. */
public final class GuildChatInput {
    private GuildChatInput() {}
    private record Prompt(String label, long expiration, BiConsumer<ServerPlayerEntity,String> answer) {}
    private static final Map<UUID,Prompt> pending = new HashMap<>();
    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,sender,params)->{
            Prompt prompt = pending.remove(sender.getUuid());
            if(prompt==null)return true;
            String input=message.getSignedContent().trim();
            // Post next tick to avoid opening a chest while chat broadcast is being processed.
            sender.getServer().execute(()->{
                if(System.currentTimeMillis()>prompt.expiration){GuildService.tell(sender,"Input expired. Open the menu again.");return;}
                if(input.equalsIgnoreCase("cancel")){GuildService.tell(sender,"Input cancelled.");return;}
                try{prompt.answer.accept(sender,input);}catch(Exception ex){
                    CobbleGuilds.LOG.error("Guild chat input failed",ex);
                    GuildService.tell(sender,"That input could not be processed. Please try again.");
                }
            });
            return false;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->pending.remove(handler.player.getUuid()));
    }
    public static void ask(ServerPlayerEntity player,String prompt,BiConsumer<ServerPlayerEntity,String> answer){
        pending.put(player.getUuid(),new Prompt(prompt,System.currentTimeMillis()+120_000L,answer));
        player.closeHandledScreen();
        GuildService.tell(player,"Type " + prompt + " in chat. Your response will be PRIVATE (not broadcast). Type 'cancel' to cancel.");
    }
}
