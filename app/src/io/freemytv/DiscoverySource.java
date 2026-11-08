package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cached public uploads, isolated from the optional legacy server. All disk/network I/O is background. */
final class DiscoverySource {
    static final class Status {
        final boolean loading, stale;
        final int failed, completed, total, count;
        final long updatedAt;
        Status(boolean loading, boolean stale, int failed, int completed, int total, long updatedAt, int count) {
            this.loading=loading;this.stale=stale;this.failed=failed;this.completed=completed;
            this.total=total;this.updatedAt=updatedAt;this.count=count;
        }
    }
    private static DiscoverySource instance;
    static synchronized DiscoverySource get(Context context) {
        if(instance==null)instance=new DiscoverySource(context.getApplicationContext());
        return instance;
    }
    private final DiscoveryState state=new DiscoveryState();
    private final File directory;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private long scheduled=-1, attemptedGeneration=-1, attemptedAt;
    private DiscoverySource(Context context){directory=new File(context.getFilesDir(),"public-video-cache");}
    void addListener(Runnable listener){listeners.addIfAbsent(listener);}
    void removeListener(Runnable listener){listeners.remove(listener);}
    List<PublicVideos.Video> videos(){return state.videos();}
    String revision(){return state.revision();}
    String configurationRevision(){return Long.toString(state.generation());}
    Status status(){
        synchronized(state){return new Status(state.loading(),state.stale(System.currentTimeMillis()),state.failed(),
                state.completed(),state.total(),state.updatedAt(),state.videos().size());}
    }

    /** Configuration changes immediately drop removed sources; old I/O cannot publish into a later generation. */
    synchronized void refresh(List<PublicVideos.Source> sources,boolean force){
        long previous=state.generation();
        long token=state.configure(sources);
        boolean changed=token!=previous;
        if(changed)notifyListeners();
        if(scheduled==token)return;
        long now=System.currentTimeMillis();
        if(!force && !changed && attemptedGeneration==token && now>=attemptedAt && now-attemptedAt<DiscoveryState.TTL)return;
        scheduled=token;attemptedGeneration=token;attemptedAt=now;
        state.begin(token);notifyListeners();
        io.execute(()->refresh(token,force));
    }
    private void refresh(long token,boolean force){
        try {
            if(!state.current(token))return;
            List<String> keys=state.refs();
            // Read every cached channel first, so the display needn't wait on the first network timeout.
            for(String key:keys){
                if(!state.current(token))return;
                restore(token,key);
            }
            notifyListeners();
            long deadline=System.nanoTime()+90_000_000_000L;
            for(String key:keys){
                if(!state.current(token))return;
                String ref=state.reference(key);
                if(ref==null)continue;
                if(!force && state.fresh(ref,System.currentTimeMillis())){
                    state.cached(token,ref);notifyListeners();continue;
                }
                String stage="metadata";
                try {
                    if(System.nanoTime()>deadline)throw new IOException("Refresh time budget exceeded");
                    String id=ChannelRef.channelId(ref);
                    if(id==null)id=PublicVideos.resolveChannel(request(ref,PublicVideos.HTML_LIMIT,deadline));
                    if(!state.current(token))return;
                    stage="feed";
                    String xml=request(PublicVideos.feedUrl(id),PublicVideos.FEED_LIMIT,deadline);
                    stage="parse";
                    long at=System.currentTimeMillis();
                    if(state.accept(token,ref,id,xml,at)) {
                        // Cache failure must not discard valid fresh network data.
                        try{save(key,id,xml,at);}catch(Exception ignored){}
                    }
                }catch(Exception error){
                    state.fail(token,ref);
                    // Log only stage, exception type and our own HTTP status text, never URLs,
                    // favorite channels, downloaded bodies or parser messages containing user data.
                    Throwable cause=error;
                    for(int depth=0;depth<8 && cause.getCause()!=null;depth++)cause=cause.getCause();
                    String status=error.getMessage();
                    if(status==null || !status.matches("Public source unavailable HTTP [0-9]{3}"))status="";
                    AppLog.w("public-video-fetch stage="+stage+" type="+error.getClass().getSimpleName()
                            +" cause="+cause.getClass().getSimpleName()+" "+status);
                }
                notifyListeners();
            }
            if(state.current(token))prune(new HashSet<>(keys));
        }finally{
            state.finish(token);
            synchronized(this){if(scheduled==token)scheduled=-1;}
            notifyListeners();
        }
    }
    private void restore(long token,String key){
        AtomicFile file=cache(key);
        try(InputStream input=file.openRead()){
            String body=read(input,PublicVideos.FEED_LIMIT*2+8192,Long.MAX_VALUE);
            JSONObject saved=new JSONObject(body);
            if(saved.optInt("version")!=1 || !key.equals(saved.getString("ref")))return;
            String xml=saved.getString("xml"),id=saved.getString("channelId");
            state.restore(token,key,id,xml,saved.getLong("at"));
        }catch(Exception ignored){}
    }
    private void save(String key,String id,String xml,long at)throws Exception{
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Cache unavailable");
        String body=new JSONObject().put("version",1).put("ref",key).put("channelId",id).put("at",at).put("xml",xml).toString();
        AtomicFile file=cache(key);
        FileOutputStream out=null;
        try{
            out=file.startWrite();out.write(body.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
        }catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    private AtomicFile cache(String key){return new AtomicFile(new File(directory,PublicVideos.digest(key)+".json"));}
    private void prune(Set<String> keys){
        Set<String> names=new HashSet<>();for(String key:keys)names.add(PublicVideos.digest(key)+".json");
        File[] files=directory.listFiles();if(files==null)return;
        for(File file:files){
            String name=file.getName();
            if(name.endsWith(".bak"))name=name.substring(0,name.length()-4);
            if(!names.contains(name))file.delete();
        }
    }
    /** No cookies/accounts, off-host redirects, permissive TLS, or arbitrary user-provided endpoints. */
    private static String request(String first,int limit,long deadline)throws IOException{
        String address=first;
        for(int redirect=0;redirect<=2;redirect++){
            if(!PublicVideos.safeUrl(address))throw new IOException("Unsupported public source URL");
            if(System.nanoTime()>deadline)throw new IOException("Refresh time budget exceeded");
            HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
            connection.setConnectTimeout(5000);connection.setReadTimeout(6000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept",address.contains("/feeds/")?"application/atom+xml, application/xml":"text/html");
            try{
                int code=connection.getResponseCode();
                if(code==301 || code==302 || code==303 || code==307 || code==308){
                    String location=connection.getHeaderField("Location");
                    if(location==null || redirect==2)throw new IOException("Redirect limit exceeded");
                    try{address=new URI(address).resolve(location).toString();}catch(Exception e){throw new IOException("Invalid redirect",e);}
                    continue;
                }
                if(code!=200)throw new IOException("Public source unavailable HTTP "+code);
                if(connection.getContentLengthLong()>limit)throw new IOException("Response exceeds limit");
                try(InputStream input=connection.getInputStream()){return read(input,limit,deadline);}
            }finally{connection.disconnect();}
        }
        throw new IOException("Public source unavailable");
    }
    private static String read(InputStream input,int limit,long deadline)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n,total=0;
        while((n=input.read(buffer))!=-1){
            if(System.nanoTime()>deadline)throw new IOException("Refresh time budget exceeded");
            total+=n;if(total>limit)throw new IOException("Response exceeds limit");out.write(buffer,0,n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private void notifyListeners(){main.post(()->{for(Runnable listener:listeners)listener.run();});}
}
