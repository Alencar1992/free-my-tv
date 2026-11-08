package io.freemytv;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Offline fake provider drives real source state and HTTP handling; never uses a live API key. */
public final class XboxSourceTest {
    private static final AtomicInteger calls=new AtomicInteger();
    private static final CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
    private static volatile boolean blockSecond,rateLimited;
    public static void main(String[] args) {
        try { run();System.out.println("XboxSourceTest OK");System.exit(0); }
        catch(Throwable error) {error.printStackTrace();System.exit(1);}
    }
    private static void run() throws Exception {
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){return new Fake(url);}
        }:null);
        XboxSource source=XboxSource.get(new Context(new File(argsDirectory())));
        Thread.sleep(30);check(calls.get()==0,"no request before explicit connection");
        source.connect("fixture-key-first");await(()->source.snapshot().configured&&!source.snapshot().loading);
        XboxSource.Snapshot first=source.snapshot();
        check(first.games.size()==2 && "101".equals(first.selectedId),"validated history supplies selected catalog title");
        check(first.summary!=null && first.summary.unlocked==1 && first.summary.total==2,"achievement continuation is consumed");
        check("2h05min".equals(first.summary.time()),"native stats shape reaches summary");
        int before=calls.get();source.select("../outside");source.select("999");
        check(calls.get()==before && "101".equals(source.snapshot().selectedId),"only catalog title IDs may be selected");
        source.select("102");await(()->!source.snapshot().loading);
        source.select("101");await(()->!source.snapshot().loading);
        check(source.snapshot().summary!=null&&"2h05min".equals(source.snapshot().summary.time()),"browsing A to B to A retains earlier detailed playtime within its freshness window");
        rateLimited=true;source.refresh(true);await(()->!source.snapshot().loading);
        check(source.snapshot().stale && source.snapshot().summary.total==2,"429 retains safe selected cache");
        String retainedMessage=source.snapshot().message;
        source.refresh(false);Thread.sleep(40);
        check(source.snapshot().message.equals(retainedMessage),"automatic tick after429 does not queue another failed attempt before15minutes");
        rateLimited=false;
        // A fresh source has an independent retry budget for the cross-account generation test.
        java.lang.reflect.Field instance=XboxSource.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,null);
        XboxSource replacement=XboxSource.get(new Context(new File(argsDirectory()+"-second")));
        replacement.connect("fixture-key-second");await(()->replacement.snapshot().configured&&!replacement.snapshot().loading);
        blockSecond=true;replacement.select("102");check(blocked.await(3,TimeUnit.SECONDS),"selected detail request in flight");
        check("102".equals(replacement.snapshot().summary.game.id),"selection immediately replaces previous summary");
        replacement.disconnect();check(!replacement.snapshot().configured && replacement.snapshot().games.isEmpty(),"disconnect immediately removes private view state");
        replacement.connect("fixture-key-third");
        check(replacement.snapshot().games.isEmpty() && replacement.snapshot().summary==null,"new connection never displays previous private cache");
        release.countDown();await(()->replacement.snapshot().configured&&!replacement.snapshot().loading);
        check("101".equals(replacement.snapshot().selectedId) && "101".equals(replacement.snapshot().summary.game.id),"late prior-account response rejected");
        replacement.disconnect();source.disconnect();
    }
    private static String argsDirectory(){return System.getProperty("xbox.test.dir");}
    private static void await(BooleanSupplier ready) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!ready.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(5);
        check(ready.getAsBoolean(),"async source settled");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class Fake extends HttpURLConnection {
        Fake(URL url){super(url);}
        public void disconnect() { }
        public boolean usingProxy(){return false;}
        public void connect(){ }
        public int getResponseCode() {
            calls.incrementAndGet();
            check("xbl.io".equals(url.getHost()) && url.getPath().startsWith("/api/v2/"),"fixed authenticated host");
            check("GET".equals(getRequestMethod()) && !getInstanceFollowRedirects(),"read-only and no credentialed redirect");
            check(getRequestProperty("X-Authorization").startsWith("fixture-key-"),"explicit fixture key only on API");
            return rateLimited?429:200;
        }
        public String getHeaderField(String name){return "Retry-After".equals(name)?"60":null;}
        public InputStream getInputStream() {
            String path=url.getPath(),json;
            if(path.endsWith("/account"))json="{\"xuid\":\""+(getRequestProperty("X-Authorization").endsWith("third")?"7003":"7001")+"\",\"gamertag\":\"Fixture User\"}";
            else if(path.endsWith("/player/titleHistory"))json="{\"titles\":[{\"titleId\":\"101\",\"name\":\"Fixture One\"},{\"titleId\":\"102\",\"name\":\"Fixture Two\"}]}";
            else if(path.endsWith("/stats/101"))json="{\"statlistscollection\":[{\"stats\":[{\"name\":\"MinutesPlayed\",\"value\":\"125\"}]}]}";
            else if(path.endsWith("/stats/102")) {
                if(blockSecond) {blocked.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
                json="{\"stats\":[{\"name\":\"MinutesPlayed\",\"value\":\"999\"}]}";
            }
            else if(path.endsWith("/title/101/next"))json="{\"achievements\":[{\"id\":\"b\",\"progressState\":\"NotStarted\"}]}";
            else if(path.endsWith("/title/101"))json="{\"achievements\":[{\"id\":\"a\",\"progressState\":\"Achieved\"}],\"pagingInfo\":{\"continuationToken\":\"next\"}}";
            else json="{\"achievements\":[{\"id\":\"c\",\"progressState\":\"NotStarted\"}]}";
            return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
