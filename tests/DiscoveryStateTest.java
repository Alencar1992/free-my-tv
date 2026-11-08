package io.freemytv;
import java.util.*;
public final class DiscoveryStateTest {
    private static int n;
    private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        PublicVideos.Source a=new PublicVideos.Source("https://www.youtube.com/channel/"+PublicVideosTest.CID,"A","",false,false);
        PublicVideos.Source b=new PublicVideos.Source("@ExampleB","B","",false,false);
        DiscoveryState state=new DiscoveryState();
        long generation=state.configure(Arrays.asList(a,b));
        state.begin(generation);
        String xml=PublicVideosTest.feed(PublicVideosTest.entry("abcdefghijk","Film Trailer",PublicVideosTest.CID,"2026-01-01T00:00:00Z"));
        check(state.accept(generation,a.ref,PublicVideosTest.CID,xml,1000),"Current generation accepts valid feed");
        check(state.generation()==generation,"An arriving video cannot change source identity or reset browsing pages");
        String contentRevision=state.revision();
        state.accept(generation,a.ref,PublicVideosTest.CID,xml.replace("Film Trailer","Updated Film Trailer"),1001);
        check(state.generation()==generation && !state.revision().equals(contentRevision),"Updated metadata rebuilds cards without changing the source identity");
        state.fail(generation,b.ref);
        check(state.videos().size()==1 && state.failed()==1 && state.completed()==2,"Partial failure preserves other successful source");
        state.finish(generation);
        long next=state.configure(Collections.singletonList(b));
        check(state.videos().isEmpty(),"Removing source removes its videos immediately");
        check(!state.accept(generation,a.ref,PublicVideosTest.CID,xml,2000) && state.videos().isEmpty(),"Late old generation cannot republish removed source");
        state.configure(Collections.singletonList(a));
        long third=state.generation();
        state.restore(third,a.ref,PublicVideosTest.CID,xml,1000);
        state.begin(third);state.fail(third,a.ref);state.finish(third);
        check(state.videos().size()==1 && state.stale(1000000),"Failed refresh retains valid private cache with stale status");
        DiscoveryState identity=new DiscoveryState();long identityGeneration=identity.configure(Collections.singletonList(a));
        check(!identity.restore(identityGeneration,a.ref,"UCzyxwvutsrqponmlkjihgfe",xml.replace(PublicVideosTest.CID,"UCzyxwvutsrqponmlkjihgfe"),2000),"Cached channel identity must agree with an explicit source channel ID");
        String before=state.revision();
        state.configure(Collections.singletonList(new PublicVideos.Source(a.ref,"Cinema","tv.example.app",true,true)));
        check(state.videos().size()==1 && state.videos().get(0).trailer && !state.videos().get(0).favorite,"Same cached feed is recategorized when source purpose changes");
        check(!state.revision().equals(before),"Revision changes when presentation/source categorization changes");
        check(state.configure(state.sources())==state.generation(),"Unchanged source configuration does not cancel in-flight refresh");
        System.out.println("DiscoveryStateTest: "+n+" assertions passed");
    }
}
