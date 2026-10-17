package dev.silver.cobbleguilds;

import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.math.BigInteger;
import java.util.UUID;

/** All monetary operations happen on the main server thread and use BigInteger. */
public final class GuildMoney {
    private final MinecraftServer server;
    public GuildMoney(MinecraftServer server){this.server=server;}
    public BigInteger balance(ServerPlayerEntity p){return PlayerExtensionKt.getCobbleDollars(p);}
    public boolean debit(ServerPlayerEntity p,BigInteger amount){
        if(amount.signum()<=0 || amount.bitLength()>256) return false;
        BigInteger current=balance(p);
        if(current.compareTo(amount)<0) return false;
        PlayerExtensionKt.setCobbleDollars(p,current.subtract(amount));
        PlayerExtensionKt.updateCobbleDollarsAccount(p);
        return true;
    }
    public void credit(ServerPlayerEntity p,BigInteger amount){
        if(amount.signum()<0||amount.bitLength()>256) throw new IllegalArgumentException("Invalid credit");
        PlayerExtensionKt.setCobbleDollars(p,balance(p).add(amount));
        PlayerExtensionKt.updateCobbleDollarsAccount(p);
    }
    public static BigInteger amount(String s){
        if(s==null || !s.matches("[1-9][0-9]{0,64}")) throw new IllegalArgumentException("Use a positive amount (up to 65 digits).");
        return new BigInteger(s);
    }
}
