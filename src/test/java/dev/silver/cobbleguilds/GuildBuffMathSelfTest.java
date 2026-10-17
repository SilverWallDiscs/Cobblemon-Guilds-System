package dev.silver.cobbleguilds;
public final class GuildBuffMathSelfTest {
    public static void main(String[] args) {
        check(GuildBuffMath.scaled(100,2)==200,"XP x2");
        check(GuildBuffMath.scaled(2,2)==4,"EV x2");
        check(GuildBuffMath.scaled(Integer.MAX_VALUE,3)==Integer.MAX_VALUE,"XP overflow");
        check(GuildBuffMath.scaledCatch(100,1.5)==150F,"catch x1.5");
        check(GuildBuffMath.scaledCatch(220,2)==255F,"catch cap");
        check(GuildBuffMath.extraShinyChance(8192,3)>0,"shiny probability");
        double p=1.0/8192.0;
        double composed=p+(1-p)*GuildBuffMath.extraShinyChance(8192,3);
        check(Math.abs(composed-3*p)<1e-10,"shiny exact multiplier");
        check(GuildBuffMath.extraShinyChance(8192,1)==0,"no buff");
        System.out.println("PASS: guild buff math invariants");
    }
    static void check(boolean result,String msg){if(!result)throw new AssertionError(msg);}
}
