package dev.silver.cobbleguilds.mixin;

import dev.silver.cobbleguilds.*;
import net.minecraft.network.message.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.server.PlayerManager;

/** Prefixes the DISPLAY NAME in signed chat, not the message body. No client dependency. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @ModifyVariable(method="broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",at=@At("HEAD"),argsOnly=true,index=3,require=0)
    private MessageType.Parameters cobbleguilds$prefixChatName(MessageType.Parameters params,net.minecraft.network.message.SignedMessage message,ServerPlayerEntity sender){
        if(sender==null)return params;
        GuildService service;
        try{service=CobbleGuilds.service();}catch(IllegalStateException e){return params;}
        Guild guild=service.store.member(sender.getUuid());if(guild==null)return params;
        char code=guild.color!=null && guild.color.matches("§[0-9a-f]")?guild.color.charAt(1):'7';
        Formatting formatting=Formatting.byCode(code);
        Text prefix=Text.literal("["+guild.tag+"] ").formatted(formatting==null?Formatting.GRAY:formatting);
        return new MessageType.Parameters(params.type(),prefix.copy().append(params.name()),params.targetName());
    }
}
