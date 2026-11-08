package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public metadata-only image loader; deliberately independent of credentialed API transport. */
final class XboxArt {
    private static XboxArt instance;
    static synchronized XboxArt get(Context context) {
        if(instance==null)instance=new XboxArt(context.getApplicationContext());return instance;
    }
    private final File directory;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final LinkedHashMap<String,Bitmap> memory=new LinkedHashMap<>(16,.75f,true);
    private List<String> window=Collections.emptyList();
    private long generation;
    private boolean worker;
    private XboxArt(Context context) { directory=new File(context.getCacheDir(),"xbox-art.v1"); }
    void addListener(Runnable listener) {listeners.addIfAbsent(listener);}
    void removeListener(Runnable listener) {listeners.remove(listener);}
    synchronized Bitmap get(XboxData.Game game) {
        return game==null?null:memory.get(XboxData.coverUrl(game.cover));
    }
    synchronized void prefetch(List<XboxData.Game> visible) {
        List<String> latest=new ArrayList<>();
        if(visible!=null)for(XboxData.Game game:visible) {
            if(latest.size()>=12)break;
            String url=XboxData.coverUrl(game.cover);
            if(!url.isEmpty() && !latest.contains(url))latest.add(url);
        }
        if(window.equals(latest))return;
        window=Collections.unmodifiableList(latest);generation++;
        if(!worker && !latest.isEmpty()) { worker=true;io.execute(this::drain); }
    }
    synchronized void clear() {
        generation++;window=Collections.emptyList();memory.clear();
        final long token=generation;
        io.execute(()->{synchronized(XboxArt.this){if(token!=generation)return;File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();}});
    }
    private void drain() {
        for(;;) {
            final List<String> latest;final long token;
            synchronized(this) {latest=window;token=generation;}
            for(String url:latest) {
                synchronized(this) {if(token!=generation)break;if(memory.containsKey(url))continue;}
                Bitmap bitmap=load(url,token);
                if(bitmap==null)continue;
                synchronized(this) {
                    if(token!=generation)continue;
                    memory.put(url,bitmap);
                    while(memory.size()>12)memory.remove(memory.keySet().iterator().next());
                }
                main.post(()->{synchronized(XboxArt.this){if(token!=generation)return;}for(Runnable listener:listeners)listener.run();});
            }
            synchronized(this) { if(token==generation) {worker=false;return;} }
        }
    }
    private Bitmap load(String url,long token) {
        try {
            File file=new File(directory,digest(url));byte[] data=null;
            if(file.isFile())try(FileInputStream in=new FileInputStream(file)){data=XboxRequestPolicy.read(in,2*1024*1024);file.setLastModified(System.currentTimeMillis());}
            if(data==null) {
                if(!url.equals(XboxData.coverUrl(url)) || !url.startsWith("https://"))return null;
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
                try {
                    connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
                    // Never set or obtain any API/user headers on artwork connections.
                    connection.setRequestProperty("Accept-Encoding","identity");
                    if(connection.getResponseCode()!=200 || connection.getContentLengthLong()>2*1024*1024)return null;
                    try(InputStream in=connection.getInputStream()){data=XboxRequestPolicy.read(in,2*1024*1024);}
                } finally {connection.disconnect();}
                synchronized(this) {
                    if(token!=generation)return null;
                    directory.mkdirs();AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
                    try {out=atomic.startWrite();out.write(data);atomic.finishWrite(out);}
                    catch(Exception e){if(out!=null)atomic.failWrite(out);}
                    trimDisk();
                }
            }
            synchronized(this){if(token!=generation)return null;}
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>32768 || bounds.outHeight>32768)return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>320 || bounds.outHeight/options.inSampleSize>440)options.inSampleSize*=2;
            options.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap decoded=BitmapFactory.decodeByteArray(data,0,data.length,options);
            if(decoded==null)return null;
            float scale=Math.min(1f,Math.min(160f/decoded.getWidth(),220f/decoded.getHeight()));
            if(scale<1f) {
                Bitmap small=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true);
                if(small!=decoded)decoded.recycle();return small;
            }
            return decoded;
        } catch(Exception ignored) {return null;}
    }
    private void trimDisk() {
        File[] files=directory.listFiles();if(files==null || files.length<=24)return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(int i=24;i<files.length;i++)files[i].delete();
    }
    private static String digest(String url) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.US,"%02x",b&255));return name.toString();
    }
}
