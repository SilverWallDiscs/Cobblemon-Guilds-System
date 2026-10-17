package dev.silver.cobbleguilds;

/** Pure numeric helpers: compilable/testable independently of Minecraft. */
public final class GuildBuffMath {
    private GuildBuffMath() {}
    public static int scaled(int base,double multiplier){
        if(base <= 0 || !Double.isFinite(multiplier) || multiplier<=1)return Math.max(0,base);
        return (int)Math.min(Integer.MAX_VALUE,Math.ceil((double)base*multiplier));
    }
    public static float scaledCatch(float base,double multiplier){
        if(!Float.isFinite(base) || !Double.isFinite(multiplier))return base;
        return (float)Math.max(0,Math.min(255.0,base*Math.max(1,multiplier)));
    }
    /** Conditional extra roll on an originally non-shiny wild spawn. */
    public static double extraShinyChance(double originalRate,double multiplier){
        if(!Double.isFinite(originalRate)||!Double.isFinite(multiplier)||originalRate<=1||multiplier<=1)return 0;
        return Math.min(1.0,(multiplier-1.0)/(originalRate-1.0));
    }
}
