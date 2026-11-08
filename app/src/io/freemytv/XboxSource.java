package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicitly connected, read-only OpenXBL source. No timer or background polling. */
final class XboxSource {
    static final class Snapshot {
        final boolean configured,loading,stale;
        final String message,profileName,selectedId;
        final List<XboxData.Game> games;
        final XboxData.Summary summary;
        final long updatedAt;
        Snapshot(boolean configured,boolean loading,boolean stale,String message,String profileName,
                 String selectedId,List<XboxData.Game> games,XboxData.Summary summary,long updatedAt) {
            this.configured=configured;this.loading=loading;this.stale=stale;this.message=message;
            this.profileName=profileName;this.selectedId=selectedId;
            this.games=Collections.unmodifiableList(new ArrayList<>(games));
            this.summary=summary!=null && summary.game.id.equals(selectedId)?summary:null;this.updatedAt=updatedAt;
        }
        XboxData.Game selected() { for(XboxData.Game game:games)if(game.id.equals(selectedId))return game;return null; }
    }
    private static XboxSource instance;
    static synchronized XboxSource get(Context context) {
        if(instance==null)instance=new XboxSource(context.getApplicationContext());return instance;
    }
    private final Context context;
    private final XboxVault vault;
    private final AtomicFile cache;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final XboxRequestPolicy policy=new XboxRequestPolicy();
    private volatile Snapshot state=empty("");
    private XboxVault.Credential credential;
    private long generation,catalogAt;
    private static final class CachedDetail {
        final XboxData.Summary summary;final long at;final boolean stale;
        CachedDetail(XboxData.Summary summary,long at,boolean stale){this.summary=summary;this.at=at;this.stale=stale;}
    }
    private final LinkedHashMap<String,CachedDetail> details=new LinkedHashMap<>(24,.75f,true);
    private void remember(XboxData.Summary summary,long at,boolean stale){
        if(summary==null||at<=0)return;
        details.put(summary.game.id,new CachedDetail(summary,at,stale));
        while(details.size()>20)details.remove(details.keySet().iterator().next());
    }
    private HttpURLConnection active;
    private static Snapshot empty(String message) {
        return new Snapshot(false,false,false,message,"",null,Collections.emptyList(),null,0);
    }
    private XboxSource(Context context) {
        this.context=context;vault=new XboxVault(context);cache=new AtomicFile(new File(context.getFilesDir(),"xbox-metadata.v1"));
        long token=generation;io.execute(()->restore(token));
    }
    Snapshot snapshot() {
        Snapshot s=state;
        if(s.configured && s.updatedAt>0 && System.currentTimeMillis()-s.updatedAt>=XboxRequestPolicy.FRESH_MS && !s.stale)
            return new Snapshot(true,s.loading,true,s.message,s.profileName,s.selectedId,s.games,s.summary,s.updatedAt);
        return s;
    }
    void addListener(Runnable listener) { listeners.addIfAbsent(listener); }
    void removeListener(Runnable listener) { listeners.remove(listener); }
    private void notifyListeners() { main.post(()->{ for(Runnable listener:listeners)listener.run(); }); }
    private synchronized boolean current(long token) { return token==generation; }
    private synchronized long invalidate() { generation++;if(active!=null)active.disconnect();active=null;return generation; }
    synchronized void connect(String entered) {
        final String key=entered==null?"":entered.trim();
        long token=invalidate();credential=null;catalogAt=0;details.clear();XboxArt.get(context).clear();
        io.execute(()->{synchronized(XboxSource.this){if(current(token)){vault.clear();cache.delete();}}});
        if(key.length()<8 || key.length()>2048 || !key.matches("[\\x21-\\x7E]+")) { state=empty("Informe uma chave OpenXBL válida.");notifyListeners();return; }
        state=new Snapshot(false,true,false,"Validando conexão…","",null,Collections.emptyList(),null,0);notifyListeners();
        io.execute(()->connect(token,key));
    }
    private void connect(long token,String key) {
        try {
            XboxData.Profile profile=XboxData.parseProfile(request(token,key,"/account"));
            if(profile.xuid.isEmpty())throw new Failure("Conta não reconhecida pelo provedor.");
            synchronized(this) {
                if(!current(token))return;
                try { vault.save(key,profile.xuid); }
                catch(Exception e) { throw new Failure("Não foi possível proteger a chave neste dispositivo."); }
                credential=new XboxVault.Credential(key,profile.xuid);
                state=new Snapshot(true,true,false,"Conectado; lendo histórico…",profile.gamertag,null,Collections.emptyList(),null,0);
            }
            notifyListeners();load(token,key,true);
        } catch(Exception e) { fail(token,e); }
    }
    synchronized void disconnect() {
        long token=invalidate();credential=null;catalogAt=0;details.clear();XboxArt.get(context).clear();state=empty("");notifyListeners();
        io.execute(()->{synchronized(XboxSource.this){if(current(token)){vault.clear();cache.delete();}}});
    }
    synchronized void select(String id) {
        Snapshot s=state;XboxData.Game selected=null;
        if(!s.configured || !XboxRequestPolicy.title(id))return;
        for(XboxData.Game game:s.games)if(game.id.equals(id))selected=game;
        if(selected==null || id.equals(s.selectedId))return;
        long token=invalidate();
        CachedDetail cached=details.get(id);long now=System.currentTimeMillis();
        boolean stale=cached==null||cached.stale||now<cached.at||now-cached.at>=XboxRequestPolicy.FRESH_MS;
        state=new Snapshot(true,false,stale,"Jogo selecionado.",s.profileName,id,s.games,cached==null?catalogSummary(selected):cached.summary,cached==null?0:cached.at);
        io.execute(()->{synchronized(XboxSource.this){if(current(token))saveCache();}});notifyListeners();refresh(false);
    }
    synchronized void refresh(boolean force) {
        Snapshot s=snapshot();if(credential==null || !s.configured || s.loading)return;
        long now=System.currentTimeMillis();String target=s.selectedId==null?"history":s.selectedId;
        if(force && !policy.force(target,now)) { state=new Snapshot(true,false,s.stale,"Aguarde 30 segundos para atualizar este jogo.",s.profileName,s.selectedId,s.games,s.summary,s.updatedAt);notifyListeners();return; }
        if(!force && !s.stale && s.updatedAt>0 && now-s.updatedAt<XboxRequestPolicy.FRESH_MS)return;
        if(!force&&!policy.automatic(target,now))return;
        policy.attempted(target,now);
        long token=generation;String key=credential.key;
        state=new Snapshot(true,true,s.stale,"Atualizando…",s.profileName,s.selectedId,s.games,s.summary,s.updatedAt);notifyListeners();
        io.execute(()->{try {load(token,key,false);}catch(Exception e){fail(token,e);}});
    }
    private void load(long token,String key,boolean initial) throws Exception {
        Snapshot s;
        synchronized(this) { if(!current(token))return;s=state; }
        policy.attempted(s.selectedId==null?"history":s.selectedId,System.currentTimeMillis());
        String historyMessage="Histórico retornado pelo Xbox; não representa todas as licenças compradas.";
        if(initial || s.games.isEmpty() || System.currentTimeMillis()-catalogAt>=XboxRequestPolicy.FRESH_MS) {
            XboxData.Catalog catalog=XboxData.parseCatalog(request(token,key,"/player/titleHistory"));
            String chosen=s.selectedId;boolean found=false;
            for(XboxData.Game game:catalog.games)if(game.id.equals(chosen))found=true;
            if(!found)chosen=catalog.games.isEmpty()?null:catalog.games.get(0).id;
            synchronized(this) {
                if(!current(token))return;
                catalogAt=System.currentTimeMillis();
                state=new Snapshot(true,true,true,historyMessage,s.profileName,chosen,catalog.games,
                        chosen!=null && chosen.equals(s.selectedId)?s.summary:null,s.updatedAt);
                s=state;saveCache();
            }
            if(catalog.truncated || !catalog.continuation.isEmpty())historyMessage="Histórico limitado aos jogos retornados (até 1000).";
            notifyListeners();
        }
        XboxData.Game game=s.selected();
        if(game==null) {
            synchronized(this) { if(!current(token))return;state=new Snapshot(true,false,false,"Nenhum jogo no histórico retornado.",s.profileName,null,s.games,null,System.currentTimeMillis());saveCache(); }
            notifyListeners();return;
        }
        policy.attempted(game.id,System.currentTimeMillis());
        List<XboxData.AchievementPage> pages=new ArrayList<>();Set<String> tokens=new HashSet<>();
        String continuation="";int count=0;boolean complete=false;String detailError="";
        try {
            do {
                String path="/achievements/title/"+game.id;
                if(!continuation.isEmpty())path+="/"+URLEncoder.encode(continuation,"UTF-8").replace("+","%20");
                XboxData.AchievementPage page=XboxData.parseAchievements(request(token,key,path));pages.add(page);count+=page.items.size();
                continuation=page.continuation;
                if(continuation.isEmpty()) { complete=true;break; }
                if(!XboxRequestPolicy.continuation(continuation) || !tokens.add(continuation))break;
            } while(pages.size()<10 && count<2000);
        } catch(Exception e) { detailError=safe(e); }
        Map<String,String> stats=Collections.emptyMap();
        try { stats=XboxData.parseStats(request(token,key,"/achievements/stats/"+game.id)); }
        catch(Exception e) { if(detailError.isEmpty())detailError=safe(e); }
        XboxData.Summary summary=XboxData.summary(game,pages,stats,complete);
        synchronized(this) {
            if(!current(token))return;
            // A failed refresh retains a prior matching successful snapshot rather than erasing cached metrics.
            if(!detailError.isEmpty() && s.summary!=null && s.updatedAt>0)summary=s.summary;
            long updated=detailError.isEmpty()?System.currentTimeMillis():s.updatedAt;
            state=new Snapshot(true,false,!detailError.isEmpty(),detailError.isEmpty()?historyMessage:detailError+" Dados em cache ou parciais.",s.profileName,game.id,s.games,summary,updated);
            remember(summary,updated,!detailError.isEmpty());saveCache();
        }
        notifyListeners();
    }
    private static XboxData.Summary catalogSummary(XboxData.Game game) {
        return new XboxData.Summary(game,game.unlocked,game.total,game.earnedScore,game.totalScore,Collections.emptyMap(),true);
    }
    private String request(long token,String key,String path) throws Exception {
        HttpURLConnection connection;
        synchronized(this) {
            if(!current(token))throw new Failure("Operação cancelada.");
            if(!policy.acquire(System.currentTimeMillis()))throw new Failure("Limite de consultas; tente novamente mais tarde.");
            // Only internally constructed paths reach this fixed host. Never follow a credentialed redirect.
            connection=(HttpURLConnection)new URL("https://xbl.io/api/v2"+path).openConnection();active=connection;
        }
        try {
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("GET");connection.setConnectTimeout(12000);connection.setReadTimeout(18000);
            connection.setRequestProperty("X-Authorization",key);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Accept-Encoding","identity");
            int status=connection.getResponseCode();
            if(status==429) { policy.backoff(connection.getHeaderField("Retry-After"),System.currentTimeMillis());throw new Failure("Provedor limitou as consultas; aguarde para tentar novamente."); }
            if(status==401 || status==403)throw new Failure("Chave recusada pelo provedor. Reconecte sua conta.");
            if(status<200 || status>=300)throw new Failure("Serviço Xbox indisponível no momento.");
            if(connection.getContentLengthLong()>4*1024*1024)throw new Failure("Resposta do provedor excede o limite.");
            try(InputStream in=connection.getInputStream()) {
                byte[] bytes=XboxRequestPolicy.read(in,4*1024*1024);
                if(!current(token))throw new Failure("Operação cancelada.");
                return new String(bytes,StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect();synchronized(this){if(active==connection)active=null;} }
    }
    private static final class Failure extends IOException { Failure(String message){super(message);} }
    private static String safe(Exception e) { return e instanceof Failure?e.getMessage():"Não foi possível atualizar o Xbox."; }
    private synchronized void fail(long token,Exception error) {
        if(!current(token))return;Snapshot s=state;
        state=new Snapshot(credential!=null,false,s.configured,safe(error),s.profileName,s.selectedId,s.games,s.summary,s.updatedAt);notifyListeners();
    }
    private void restore(long token) {
        synchronized(this) {
            if(!current(token))return;
            try {
                credential=vault.load();if(credential==null){cache.delete();return;}
                state=new Snapshot(true,false,true,"Conectado. Atualize para consultar o Xbox.","",null,Collections.emptyList(),null,0);
                try(FileInputStream in=cache.openRead()) {
                    JSONObject object=new JSONObject(new String(XboxRequestPolicy.read(in,2*1024*1024),StandardCharsets.UTF_8));
                    if(!credential.owner.equals(object.optString("owner"))) {cache.delete();notifyListeners();return;}
                    List<XboxData.Game> games=new ArrayList<>();JSONArray rows=object.getJSONArray("games");
                    for(int i=0;i<Math.min(1000,rows.length());i++) {
                        JSONObject g=rows.getJSONObject(i);String id=g.getString("id");if(!XboxRequestPolicy.title(id))continue;
                        games.add(new XboxData.Game(id,g.optString("name"),XboxData.coverUrl(g.optString("cover")),g.optBoolean("x360"),g.optInt("unlocked",-1),g.optInt("total",-1),g.optInt("earned",-1),g.optInt("score",-1)));
                    }
                    String selected=object.optString("selected");XboxData.Game game=null;
                    for(XboxData.Game g:games)if(g.id.equals(selected))game=g;
                    XboxData.Summary summary=null;JSONObject detail=object.optJSONObject("summary");
                    if(game!=null && detail!=null && selected.equals(detail.optString("id"))) {
                        Map<String,String> stats=new LinkedHashMap<>();JSONObject metrics=detail.optJSONObject("stats");
                        if(metrics!=null) {java.util.Iterator<String> keys=metrics.keys();while(keys.hasNext() && stats.size()<100){String k=keys.next();stats.put(k,metrics.optString(k));}}
                        summary=new XboxData.Summary(game,detail.optInt("unlocked",-1),detail.optInt("total",-1),detail.optInt("earned",-1),detail.optInt("score",-1),stats,detail.optBoolean("partial",true));
                    }
                    long now=System.currentTimeMillis();catalogAt=object.optLong("catalogAt");long updated=object.optLong("updated");
                    if(catalogAt>now || catalogAt<0)catalogAt=0;if(updated>now || updated<0)updated=0;
                    boolean stale=updated==0 || now-updated>=XboxRequestPolicy.FRESH_MS;
                    state=new Snapshot(true,false,stale,"Dados privados em cache; atualize para consultar o Xbox.",object.optString("profile"),game==null?null:selected,games,summary,updated);
                    remember(summary,updated,state.stale);
                } catch(Exception ignored) {cache.delete();}
            } catch(Exception ignored) {vault.clear();cache.delete();credential=null;state=empty("Armazenamento protegido indisponível; conecte novamente.");}
            notifyListeners();
        }
    }
    /** Caller holds source lock, so disconnect cannot race an old atomic cache write. */
    private void saveCache() {
        if(credential==null)return;Snapshot s=state;FileOutputStream out=null;
        try {
            JSONObject o=new JSONObject().put("owner",credential.owner).put("profile",s.profileName).put("selected",s.selectedId).put("updated",s.updatedAt).put("catalogAt",catalogAt);
            JSONArray games=new JSONArray();
            for(XboxData.Game g:s.games)games.put(new JSONObject().put("id",g.id).put("name",g.name).put("cover",g.cover).put("x360",g.xbox360).put("unlocked",g.unlocked).put("total",g.total).put("earned",g.earnedScore).put("score",g.totalScore));
            o.put("games",games);
            if(s.summary!=null) { XboxData.Summary d=s.summary;o.put("summary",new JSONObject().put("id",d.game.id).put("unlocked",d.unlocked).put("total",d.total).put("earned",d.earnedScore).put("score",d.totalScore).put("partial",d.partial).put("stats",new JSONObject(d.stats))); }
            byte[] bytes=o.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2*1024*1024)return;
            out=cache.startWrite();out.write(bytes);cache.finishWrite(out);
        } catch(Exception ignored) {if(out!=null)cache.failWrite(out);}
    }
}
