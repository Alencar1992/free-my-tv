package io.freemytv;
import java.util.*;
public final class HomeWallpaperCycleTest {
    private static int checks;
    private static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        HomeWallpaperCycle c=new HomeWallpaperCycle();
        check(c.request(0)==null,"A paused home never starts artwork I/O");
        c.resume(100);
        HomeWallpaperCycle.Request first=c.request(100);
        check(first!=null && first.asset.equals(GameGallery.HOME),"The first visit starts with the bundled home photo");
        check(c.request(101)==null,"Only one decode can be in flight per home");
        check(c.complete(first,true,200),"A visible home accepts its current decode");
        check(c.request(15199)==null,"A photo remains for the entire15-second interval");
        HomeWallpaperCycle.Request next=c.request(15200);
        check(next!=null&&!next.asset.equals(first.asset),"The next different gallery photo is requested exactly after15 seconds");
        c.pause();
        check(!c.current(next)&&!c.complete(next,true,15201),"Pausing rejects late bitmap completions");
        c.resume(50000);
        check(c.request(64999)==null,"Resuming keeps the current image and restarts its visible interval");
        HomeWallpaperCycle.Request resumed=c.request(65000);
        check(!c.complete(next,true,65000),"A decode from a previous visible visit cannot overwrite a newer request after resume");
        check(resumed!=null&&c.complete(resumed,false,65010),"A failed file can be recorded without dropping the visible photo");
        HomeWallpaperCycle.Request skipped=c.request(65010);
        check(skipped!=null&&!skipped.asset.equals(resumed.asset),"A failed file is skipped immediately");
        check(!c.complete(resumed,true,65011),"An obsolete completion cannot replace the current request");
        c.complete(skipped,true,65020);
        HomeWallpaperCycle all=new HomeWallpaperCycle();all.resume(0);
        Set<String> seen=new HashSet<>();
        for(int i=0;i<GameGallery.entries().size();i++){
            HomeWallpaperCycle.Request r=all.request(0);
            check(r!=null&&seen.add(r.asset),"Failure walk visits each bundled image only once");
            all.complete(r,false,0);
        }
        check(all.request(0)==null && all.delay(0)<0,"All failed photos stop retries and permit graphite fallback");
        HomeWallpaperCycle sole=new HomeWallpaperCycle();sole.resume(0);
        HomeWallpaperCycle.Request survivor=sole.request(0);sole.complete(survivor,true,0);
        long now=15000;
        for(int i=1;i<GameGallery.entries().size();i++){
            HomeWallpaperCycle.Request r=sole.request(now);check(r!=null,"Remaining candidate can be attempted");sole.complete(r,false,now);
        }
        check(sole.request(now)==null&&sole.delay(now)<0,"A sole surviving photo is held without repeated decode or retry timers");
        HomeWallpaperCycle interrupted=new HomeWallpaperCycle();interrupted.resume(0);
        HomeWallpaperCycle.Request abandoned=interrupted.request(0);interrupted.pause();interrupted.resume(10);
        HomeWallpaperCycle.Request replacement=interrupted.request(10);
        check(replacement!=null&&replacement.asset.equals(abandoned.asset),"An interrupted first decode can be retried on a new visit");
        check(!interrupted.complete(abandoned,true,11)&&interrupted.complete(replacement,true,12),"Only the new visit can publish its first image");
        System.out.println("HomeWallpaperCycleTest: "+checks+" assertions passed");
    }
}
