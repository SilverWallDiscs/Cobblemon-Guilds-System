package dev.silver.cobbleguilds;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.spawning.SpawnBucket;
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.BattleEvSource;
import net.minecraft.server.network.ServerPlayerEntity;
import kotlin.Unit;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dedicated-server-only guild buffs using Cobblemon's own 1.7.x events. No mixins,
 * packets, scoreboards or client mod. Unsupported effects are never sold.
 */
public final class GuildBuffs {
    private GuildBuffs() {}
    public record Offer(String id,String label,String price,int minutes,double multiplier,Kind kind) {}
    public enum Kind { SHINY, RARE, CATCH, EXPERIENCE, EV, FRIENDSHIP }
    public static final List<Offer> OFFERS = List.of(
        new Offer("shiny_x2","Shiny Luck x2","1000000",60,2.0,Kind.SHINY),
        new Offer("shiny_x3","Shiny Luck x3","3000000",60,3.0,Kind.SHINY),
        new Offer("rare_spawn_x2","Rare Spawn Bucket Boost x2","1500000",60,2.0,Kind.RARE),
        new Offer("catch_x15","Catch Rate x1.5 (Permanent)","500000",0,1.5,Kind.CATCH),
        new Offer("catch_x2","Catch Rate x2","10000000",30,2.0,Kind.CATCH),
        new Offer("exp_x2","Pokémon EXP x2","750000",60,2.0,Kind.EXPERIENCE),
        new Offer("ev_x2","EV Training x2","800000",60,2.0,Kind.EV),
        new Offer("friendship_x2","Friendship Gain x2","400000",60,2.0,Kind.FRIENDSHIP)
    );
    // A spawn *frequency* increase, hidden abilities and Alpha require additional
    // spawn table / form-specific integrations. Never charge for these until implemented.
    public static final Map<String,String> UNSUPPORTED=Map.of(
        "hidden_ability_x2", "Needs species-specific ability selection integration",
        "alpha_spawn_x2", "Alpha is not a standard Cobblemon 1.7 spawn attribute"
    );
    private static final long MAX_DURATION_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    public static Offer byId(String id){return OFFERS.stream().filter(o->o.id().equalsIgnoreCase(id)).findFirst().orElse(null);}
    public static boolean enabled(GuildConfig config,Offer offer){return config.buffPurchasesEnabled && !config.disabledBuffs.contains(offer.id());}
    public static String price(GuildConfig config,Offer offer){return config.buffPrices.getOrDefault(offer.id(),offer.price());}
    public static String unsupportedReason(String id){return UNSUPPORTED.get(id.toLowerCase(Locale.ROOT));}
    public static boolean active(Guild.Effect effect,long now){return effect!=null && (effect.permanent || effect.expiresAt>now);}
    public static double multiplier(Guild g,Kind kind,long now){
        if(g==null||g.closed||g.effects==null)return 1.0;
        double best=1.0;
        for(Offer offer:OFFERS){
            if(offer.kind()==kind && active(g.effects.get(offer.id()),now))best=Math.max(best,offer.multiplier());
        }
        return best; // Same-kind tiers don't multiply one another; catch 1.5x and 2x never add.
    }
    public static double multiplier(GuildService svc,ServerPlayerEntity player,Kind kind){
        return player==null?1.0:multiplier(svc.store.member(player.getUuid()),kind,System.currentTimeMillis());
    }
    public static int scaled(int base,double factor){return GuildBuffMath.scaled(base,factor);}
    public static float scaledCatch(float base,double factor){return GuildBuffMath.scaledCatch(base,factor);}
    /** Roll only the additional probability conditional on the normal wild roll failing. */
    public static double extraShinyChance(double originalRate,double multiplier){
        if(originalRate<=1 || multiplier<=1)return 0;
        return GuildBuffMath.extraShinyChance(originalRate,multiplier);
    }
    public static String durationText(Guild.Effect effect,long now){
        if(effect.permanent)return "Permanent";
        long seconds=Math.max(0,(effect.expiresAt-now+999)/1000);
        return String.format(Locale.ROOT,"%dh %02dm %02ds",seconds/3600,(seconds/60)%60,seconds%60);
    }
    public static boolean buy(GuildService svc,ServerPlayerEntity player,String id){
        Guild g=svc.mine(player);
        if(g==null){GuildService.tell(player,"You are not in a guild.");return false;}
        if(!g.allowed(player.getUuid(),"purchase_buffs")){GuildService.tell(player,"You cannot purchase guild buffs.");return false;}
        if(!svc.config.buffPurchasesEnabled){GuildService.tell(player,"Guild buff purchases are disabled by administrators.");return false;}
        Offer offer=byId(id);
        if(offer==null){
            String reason=unsupportedReason(id);
            GuildService.tell(player,reason==null?"Unknown buff. Use /guild buffs to see available boosts.":"Not for sale: "+reason);
            return false;
        }
        if(!enabled(svc.config,offer)){GuildService.tell(player,"This buff is not available in the Guild Buff Shop.");return false;}
        long now=System.currentTimeMillis();
        Guild.Effect old=g.effects.get(offer.id());
        if(offer.minutes()==0 && active(old,now)){
            GuildService.tell(player,"This permanent buff has already been purchased.");return false;
        }
        long addition=offer.minutes()*60_000L;
        long remaining=old!=null && !old.permanent?Math.max(0,old.expiresAt-now):0;
        if(addition>0 && remaining+addition>MAX_DURATION_MILLIS){
            GuildService.tell(player,"Maximum duration is 30 days per buff tier.");return false;
        }
        BigInteger cost=new BigInteger(price(svc.config,offer));
        BigInteger before=g.funds();
        if(before.compareTo(cost)<0){GuildService.tell(player,"Guild bank needs $"+cost+" CobbleDollars.");return false;}
        Guild.Effect next=new Guild.Effect(offer.id(),addition==0?0:now+remaining+addition,addition==0);
        try{
            g.bank=before.subtract(cost).toString();
            g.effects.put(offer.id(),next);
            g.addAudit(player.getName().getString()+" bought "+offer.label()+" for $"+cost);
            svc.store.save();
        }catch(RuntimeException error){
            g.bank=before.toString();
            if(old==null)g.effects.remove(offer.id());else g.effects.put(offer.id(),old);
            CobbleGuilds.LOG.error("Failed to purchase guild buff",error);
            GuildService.tell(player,"Purchase failed. No guild bank funds were deducted from saved data.");return false;
        }
        GuildService.tell(player,"Purchased "+offer.label()+" for the entire guild. "+durationText(next,now)+" active.");
        return true;
    }
    public static void list(GuildService svc,ServerPlayerEntity player){
        Guild g=svc.mine(player);
        if(g==null){GuildService.tell(player,"You are not in a guild.");return;}
        GuildService.tell(player,"Guild Buff Shop (paid from the guild bank):");
        for(Offer o:OFFERS)if(enabled(svc.config,o))GuildService.tell(player,o.label()+" - $"+price(svc.config,o)+" - "+(o.minutes()==0?"Permanent":o.minutes()+" minutes")+" ["+o.id()+"]");
        GuildService.tell(player,svc.config.buffPurchasesEnabled?"Buy: /guild buff buy <id>":"Purchases currently disabled by server configuration.");
    }
    public static void effects(GuildService svc,ServerPlayerEntity player){
        Guild g=svc.mine(player);
        if(g==null){GuildService.tell(player,"You are not in a guild.");return;}
        long now=System.currentTimeMillis();int count=0;
        GuildService.tell(player,"Active Guild Buffs:");
        for(Offer o:OFFERS){Guild.Effect fx=g.effects.get(o.id());if(active(fx,now)){
            GuildService.tell(player,o.label()+" - "+durationText(fx,now));count++;
        }}
        GuildService.tell(player,"Effective catch bonus: x"+multiplier(g,Kind.CATCH,now)+" to the target's base catch rate. Poké Ball item tooltip multipliers remain unchanged.");
        if(count==0)GuildService.tell(player,"None. Use /guild buffs to see available boosts.");
    }
    public static void subscribe(GuildService svc){
        // Natural wild Pokémon spawn event: only BestSpawner wild entities, not
        // party Pokémon sent out, trade recipients or admin-given Pokémon.
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL,event->{
            var entity=event.getEntity();
            var pokemon=entity.getPokemon();
            if(pokemon.getShiny())return Unit.INSTANCE;
            ServerPlayerEntity nearest=null;double bestDistance=48.0*48.0;
            for(ServerPlayerEntity p:svc.server.getPlayerManager().getPlayerList()){
                double distance=p.squaredDistanceTo(entity);
                if(distance<bestDistance){nearest=p;bestDistance=distance;}
            }
            double factor=multiplier(svc,nearest,Kind.SHINY);
            if(factor>1){
                float rate=Cobblemon.INSTANCE.getConfig().getShinyRate();
                if(ThreadLocalRandom.current().nextDouble()<extraShinyChance(rate,factor))pokemon.setShiny(true);
            }
            return Unit.INSTANCE;
        });
        // Spawn bucket weight adjustment for player-caused Cobblemon BestSpawner attempts.
        // This does NOT multiply the number of spawns or alter any other mod's spawn pools.
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(Priority.NORMAL,event->{
            if(!(event.getSpawnCause().getEntity() instanceof ServerPlayerEntity p))return Unit.INSTANCE;
            double factor=multiplier(svc,p,Kind.RARE);
            if(factor<=1)return Unit.INSTANCE;
            double total=0;
            for(var weighted:event.getBucketWeights().entrySet()){
                if(weighted.getValue()!=null && weighted.getValue()>0 && Float.isFinite(weighted.getValue()))
                    total+=weighted.getValue()*((weighted.getKey().getName().equalsIgnoreCase("rare") || weighted.getKey().getName().equalsIgnoreCase("ultra-rare"))?factor:1.0);
            }
            if(total<=0)return Unit.INSTANCE;
            double draw=ThreadLocalRandom.current().nextDouble(total);
            SpawnBucket last=null;
            for(var weighted:event.getBucketWeights().entrySet()){
                if(weighted.getValue()==null || !Float.isFinite(weighted.getValue()) || weighted.getValue()<=0)continue;
                last=weighted.getKey();
                draw-=weighted.getValue()*((last.getName().equalsIgnoreCase("rare") || last.getName().equalsIgnoreCase("ultra-rare"))?factor:1.0);
                if(draw<0){event.setBucket(last);break;}
            }
            return Unit.INSTANCE;
        });
        // This event changes a wild Pokemon's species catch rate during the real
        // Cobblemon capture calculation. It does NOT change the Poké Ball's own
        // multiplier displayed in client tooltips (1.0x/5.0x for ball type).
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.NORMAL,event->{
            if(event.getThrower() instanceof ServerPlayerEntity p){
                double factor=multiplier(svc,p,Kind.CATCH);
                if(factor>1)event.setCatchRate(scaledCatch(event.getCatchRate(),factor));
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL,event->{
            if(event.getSource() instanceof BattleExperienceSource && event.getPokemon().getOwnerEntity() instanceof ServerPlayerEntity p){
                double factor=multiplier(svc,p,Kind.EXPERIENCE);
                if(factor>1)event.setExperience(scaled(event.getExperience(),factor));
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe(Priority.NORMAL,event->{
            if(event.getSource() instanceof BattleEvSource && event.getPokemon().getOwnerEntity() instanceof ServerPlayerEntity p){
                double factor=multiplier(svc,p,Kind.EV);
                if(factor>1)event.setAmount(scaled(event.getAmount(),factor));
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(Priority.NORMAL,event->{
            if(event.getPokemon().getOwnerEntity() instanceof ServerPlayerEntity p){
                double factor=multiplier(svc,p,Kind.FRIENDSHIP);
                int current=event.getPokemon().getFriendship();
                int proposed=event.getNewFriendship();
                if(factor>1 && proposed>current)
                    event.setNewFriendship(scaled(proposed-current,factor)+current);
            }
            return Unit.INSTANCE;
        });
    }
}
