package io.freemytv;

import java.util.HashSet;
import java.util.Set;

/** Visible-home slideshow clock and decode generation gate. Bitmaps remain owned by the View. */
final class HomeWallpaperCycle {
    private static final long INTERVAL=15_000L;
    static final class Request {
        final String asset;
        final long token;
        private Request(String asset,long token){this.asset=asset;this.token=token;}
    }
    private final Set<String> failed=new HashSet<>();
    private String shown;
    private boolean active;
    private long generation,nextAt=-1;
    private Request pending;
    synchronized void resume(long now){
        if(active)return;
        active=true;generation++;pending=null;nextAt=shown==null?now:now+INTERVAL;
    }
    synchronized void pause(){active=false;generation++;pending=null;nextAt=-1;}
    synchronized Request request(long now){
        if(!active||pending!=null||nextAt<0||now<nextAt)return null;
        GameGallery.Entry next=GameGallery.next(shown,failed);
        if(next==null||next.asset.equals(shown)){nextAt=-1;return null;}
        pending=new Request(next.asset,++generation);
        return pending;
    }
    synchronized boolean current(Request request){return active&&request!=null&&request==pending&&request.token==generation;}
    synchronized boolean complete(Request request,boolean success,long now){
        if(!current(request))return false;
        pending=null;
        if(success){shown=request.asset;nextAt=now+INTERVAL;}
        else{failed.add(request.asset);nextAt=now;}
        return true;
    }
    synchronized long delay(long now){return !active||pending!=null||nextAt<0?-1:Math.max(0,nextAt-now);}
}
