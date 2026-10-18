package dev.silver.cobbleguilds;

/** Strips # comments without touching '#' in quoted JSON text values. */
public final class GuildHashComments {
    private GuildHashComments(){}
    public static String strip(String input){
        StringBuilder output=new StringBuilder(input.length());
        boolean quoted=false,escaped=false,comment=false;
        for(int i=0;i<input.length();i++){
            char ch=input.charAt(i);
            if(comment){
                if(ch=='\n'||ch=='\r'){output.append(ch);comment=false;}
            }else if(quoted){
                output.append(ch);
                if(escaped)escaped=false;
                else if(ch=='\\')escaped=true;
                else if(ch=='"')quoted=false;
            }else if(ch=='#'){
                comment=true;
            }else{
                output.append(ch);
                if(ch=='"')quoted=true;
            }
        }
        return output.toString();
    }
}
