package io.freemytv;
import java.util.*;
public final class PublicVideosTest {
    private static int n;
    static final String CID="UCabcdefghijklmnopqrstuv";
    private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
    private static void reject(Runnable r,String m){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}check(bad,m);}
    static String feed(String... entries){return "<?xml version='1.0'?><feed xmlns='http://www.w3.org/2005/Atom' xmlns:yt='http://www.youtube.com/xml/schemas/2015'><yt:channelId>"+CID+"</yt:channelId><author><name>Example Channel</name></author>"+String.join("",entries)+"</feed>";}
    static String entry(String id,String title,String channel,String published){return "<entry><yt:videoId>"+id+"</yt:videoId><yt:channelId>"+channel+"</yt:channelId><title>"+title+"</title><published>"+published+"</published><link href='https://evil.invalid/video'/></entry>";}
    public static void main(String[] args){
        PublicVideos.Source favorite=new PublicVideos.Source("https://www.youtube.com/channel/"+CID,"Example","",false,false);
        PublicVideos.Source trailer=new PublicVideos.Source(favorite.ref,"Cinema","tv.example.app",true,true);
        String xml=feed(entry("abcdefghijk","A film — Trailer oficial",CID,"2026-01-01T12:00:00Z"),entry("zyxwvutsrqp","Making of",CID,"2026-01-02T12:00:00Z"));
        List<PublicVideos.Video> videos=PublicVideos.parse(xml,CID,favorite);
        check(videos.size()==2 && videos.get(0).id.equals("zyxwvutsrqp"),"Recent uploads sort by published date");
        check(videos.get(0).url.equals("https://www.youtube.com/watch?v=zyxwvutsrqp"),"Playback URLs are built from validated video ID");
        check(videos.get(0).thumbnail.equals("https://i.ytimg.com/vi/zyxwvutsrqp/hqdefault.jpg"),"Untrusted feed URLs never become thumbnail endpoints");
        List<PublicVideos.Video> trailers=PublicVideos.parse(xml,CID,trailer);
        check(trailers.size()==1 && trailers.get(0).trailer && !trailers.get(0).favorite,"Only promotional titles enter trailer source");
        check(PublicVideos.isTrailer("PRÉVIA: novo filme") && PublicVideos.isTrailer("TEASER") && !PublicVideos.isTrailer("Entrevista"),"Trailer title filtering is case/accent insensitive");
        List<List<PublicVideos.Video>> groups=Arrays.asList(videos,trailers);
        List<PublicVideos.Video> merged=PublicVideos.merge(groups);
        check(merged.size()==2 && merged.get(1).favorite && merged.get(1).trailer,"Duplicate video retains both categories without duplicating Recentes");
        check(PublicVideos.parse(feed(entry("bad","Invalid",CID,"2026-01-01T00:00:00Z"),entry("abcdefghijk","Wrong channel","UCzyxwvutsrqponmlkjihgfe","2026-01-01T00:00:00Z")),CID,favorite).isEmpty(),"Invalid videos and another channel's entries are skipped");
        String[] many=new String[10]; for(int i=0;i<10;i++) many[i]=entry("abcdefghij"+i,"Video "+i,CID,"2026-01-01T00:00:00Z");
        check(PublicVideos.parse(feed(many),CID,favorite).size()==5,"Per-source maximum is five videos");
        reject(()->PublicVideos.parse("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><feed>&x;</feed>",CID,favorite),"DTD and external entity input is rejected");
        reject(()->PublicVideos.parse(feed().replace(CID,"UCzyxwvutsrqponmlkjihgfe"),CID,favorite),"Feed must identify requested channel");
        reject(()->PublicVideos.parse("<feed>",CID,favorite),"Malformed XML fails safely");
        check(PublicVideos.resolveChannel("<meta property='og:url' content='https://www.youtube.com/channel/"+CID+"'>").equals(CID),"Handle resolution uses canonical channel metadata");
        check(PublicVideos.resolveChannel("{\"channelMetadataRenderer\":{\"externalId\":\""+CID+"\"}}").equals(CID),"YouTube channel metadata renderer is supported");
        reject(()->PublicVideos.resolveChannel("<a href='/channel/"+CID+"'>unrelated channel</a>"),"Related channel links are not mistaken for current channel");
        reject(()->PublicVideos.resolveChannel("<meta property='og:url' content='https://evil.test/channel/"+CID+"'>"),"Only official canonical metadata is accepted");
        check(PublicVideos.safeUrl("https://www.youtube.com/feeds/videos.xml?channel_id="+CID),"Official feed endpoint accepted");
        for(String bad:new String[]{"http://www.youtube.com/feeds/videos.xml","https://www.youtube.com.evil.test/", "https://www.youtube.com:444/", "https://person@www.youtube.com/", "https://accounts.google.com/", "https://www.youtube.com/watch?v=abcdefghijk"}) check(!PublicVideos.safeUrl(bad),"Off-origin or unsupported redirects rejected");
        System.out.println("PublicVideosTest: "+n+" assertions passed");
    }
}
