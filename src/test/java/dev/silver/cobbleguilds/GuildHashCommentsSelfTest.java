package dev.silver.cobbleguilds;

public final class GuildHashCommentsSelfTest{
    static void check(String input,String expected){
        String actual=GuildHashComments.strip(input);
        if(!actual.equals(expected))throw new AssertionError("Expected <"+expected+"> got <"+actual+">");
    }
    public static void main(String[] args){
        check("# heading\n{\"fee\": true} # note\n","\n{\"fee\": true} \n");
        check("{\"name\":\"Mimikyu #1\"} # note","{\"name\":\"Mimikyu #1\"} ");
        check("# heading\r\n{\"fee\":true}\r\n","\r\n{\"fee\":true}\r\n");
        check("{\"name\":\"escaped \\\" # inside\"}","{\"name\":\"escaped \\\" # inside\"}");
        System.out.println("GuildHashCommentsSelfTest PASS");
    }
}
