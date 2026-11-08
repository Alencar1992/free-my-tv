package io.freemytv;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class ChannelRefTest {
    private static int n;
    private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
    public static void main(String[] args) {
        String id="UCabcdefghijklmnopqrstuv";
        check(ChannelRef.normalize("https://www.youtube.com/channel/"+id+"/videos").equals("https://www.youtube.com/channel/"+id),"Channel page suffix is normalized");
        check(ChannelRef.normalize("@Example_Channel").equals("https://www.youtube.com/@Example_Channel"),"Remote-friendly handles are accepted");
        check(ChannelRef.key("@EXample").equals(ChannelRef.key("https://m.youtube.com/@example/videos")),"Handle casing and mobile hosts cannot create duplicates");
        check(ChannelRef.channelId("https://www.youtube.com/channel/"+id).equals(id),"Channel IDs remain case-sensitive and exact");
        check(ChannelRef.channelId("@Example")==null,"Handles require metadata resolution, not an invented ID");
        for(String bad:new String[]{"http://www.youtube.com/@example","https://evil.test/@example","https://youtube.com.evil.test/@example","https://user:pass@youtube.com/@example","https://youtube.com:8443/@example","https://youtube.com/watch?v=abcdefghijk","https://youtube.com/@example/../../evil","https://youtube.com/channel/invalid"}){
            boolean rejected=false;try{ChannelRef.normalize(bad);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"Unsafe or unsupported source must be rejected: "+bad);
        }
        String json="{\"favorites\":[\"@Example\",\"https://www.youtube.com/@EXAMPLE/videos\"]}";
        String encoded=Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        check(ChannelRef.decodeImport(encoded).size()==1,"Reviewed import deduplicates equivalent channels");
        for(String bad:new String[]{"bad base64",Base64.getEncoder().encodeToString("{\"favorites\":[],\"mode\":\"free\"}".getBytes(StandardCharsets.UTF_8))}){
            boolean rejected=false;try{ChannelRef.decodeImport(bad);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"Import cannot carry mode/settings changes or malformed encoding");
        }
        System.out.println("ChannelRefTest: "+n+" assertions passed");
    }
}
