package io.freemytv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Android-free generation gate and partial-cache reducer. All snapshots are immutable. */
final class DiscoveryState {
    static final long TTL = 15 * 60_000L;
    private static final class Record {
        final List<PublicVideos.Video> raw;
        final long at;
        Record(List<PublicVideos.Video> raw, long at) { this.raw=raw; this.at=at; }
    }
    private List<PublicVideos.Source> sources=Collections.emptyList();
    private final Map<String,Record> records=new LinkedHashMap<>();
    private final Set<String> done=new HashSet<>(), errors=new HashSet<>();
    private List<PublicVideos.Video> videos=Collections.emptyList();
    private String config="", revision="";
    private long generation;
    private boolean loading;

    synchronized long configure(List<PublicVideos.Source> wanted) {
        if(wanted==null || wanted.size()>20) throw new IllegalArgumentException("Too many public sources");
        int favorites=0,trailers=0;
        StringBuilder signature=new StringBuilder();
        Set<String> purposeKeys=new HashSet<>();
        List<PublicVideos.Source> accepted=new ArrayList<>();
        for(PublicVideos.Source source:wanted) {
            if(source==null) throw new IllegalArgumentException("Invalid source");
            String key=ChannelRef.key(source.ref);
            if(!purposeKeys.add(key+"|"+source.trailer+"|"+source.pkg)) continue;
            if(source.trailer)trailers++;else favorites++;
            if(favorites>12 || trailers>8)throw new IllegalArgumentException("Too many public sources");
            accepted.add(source);
            signature.append(key).append('\n').append(source.label).append('\n').append(source.pkg)
                    .append('\n').append(source.trailer).append('\n').append(source.official).append('\n');
        }
        String next=PublicVideos.digest(signature.toString());
        if(next.equals(config))return generation;
        config=next;generation++;loading=false;done.clear();errors.clear();
        sources=Collections.unmodifiableList(accepted);
        records.keySet().retainAll(new HashSet<>(refs()));
        rebuild();
        return generation;
    }
    synchronized long generation(){return generation;}
    synchronized List<PublicVideos.Source> sources(){return sources;}
    /** Canonical identity keys, used for disk and network work without duplicated fetches. */
    synchronized List<String> refs(){
        List<String> result=new ArrayList<>();
        for(PublicVideos.Source source:sources) {
            String key=ChannelRef.key(source.ref);
            if(!result.contains(key))result.add(key);
        }
        return result;
    }
    synchronized String reference(String key){
        for(PublicVideos.Source source:sources)if(ChannelRef.key(source.ref).equals(key))return source.ref;
        return null;
    }
    synchronized boolean current(long token){return token==generation;}
    synchronized void begin(long token){if(current(token)){loading=true;done.clear();errors.clear();}}
    synchronized boolean restore(long token,String ref,String id,String xml,long at){
        if(!eligible(token,ref) || at<=0 || !identityMatches(ref,id))return false;
        String key=ChannelRef.key(ref);
        if(records.containsKey(key))return false;
        List<PublicVideos.Video> parsed=PublicVideos.parseRaw(xml,id);
        records.put(key,new Record(parsed,at));rebuild();return true;
    }
    synchronized boolean accept(long token,String ref,String id,String xml,long at){
        if(!eligible(token,ref) || at<=0 || !identityMatches(ref,id))return false;
        List<PublicVideos.Video> parsed=PublicVideos.parseRaw(xml,id);
        String key=ChannelRef.key(ref);
        records.put(key,new Record(parsed,at));done.add(key);errors.remove(key);rebuild();return true;
    }
    synchronized void cached(long token,String ref){if(eligible(token,ref))done.add(ChannelRef.key(ref));}
    synchronized void fail(long token,String ref){if(eligible(token,ref)){String key=ChannelRef.key(ref);done.add(key);errors.add(key);}}
    synchronized void finish(long token){if(current(token))loading=false;}
    synchronized boolean fresh(String ref,long now){
        Record record=records.get(ChannelRef.key(ref));
        return record!=null && record.at>0 && now>=record.at && now-record.at<TTL;
    }
    synchronized long checkedAt(String ref){Record record=records.get(ChannelRef.key(ref));return record==null?0:record.at;}
    synchronized boolean stale(long now){
        if(!errors.isEmpty())return true;
        for(String ref:refs())if(!fresh(ref,now))return true;
        return false;
    }
    synchronized boolean loading(){return loading;}
    synchronized int failed(){return errors.size();}
    synchronized int completed(){return done.size();}
    synchronized int total(){return refs().size();}
    synchronized long updatedAt(){
        long oldest=Long.MAX_VALUE;
        for(String ref:refs()) {Record record=records.get(ref);if(record!=null)oldest=Math.min(oldest,record.at);}
        return oldest==Long.MAX_VALUE?0:oldest;
    }
    synchronized String revision(){return revision;}
    synchronized List<PublicVideos.Video> videos(){return videos;}
    private static boolean identityMatches(String ref,String id){
        if(!ChannelRef.validChannelId(id))return false;
        String expected=ChannelRef.channelId(ref);
        return expected==null || expected.equals(id);
    }
    private boolean eligible(long token,String ref){
        return current(token) && refs().contains(ChannelRef.key(ref));
    }
    private void rebuild(){
        List<List<PublicVideos.Video>> groups=new ArrayList<>();
        for(PublicVideos.Source source:sources){
            Record record=records.get(ChannelRef.key(source.ref));
            if(record!=null)groups.add(PublicVideos.select(record.raw,source));
        }
        videos=PublicVideos.merge(groups);
        StringBuilder data=new StringBuilder(config);
        for(PublicVideos.Video video:videos)data.append('\n').append(video.id).append('|').append(video.title).append('|')
                .append(video.channelTitle).append('|').append(video.publishedAt).append('|').append(video.favorite)
                .append('|').append(video.trailer).append('|').append(video.serviceLabel);
        revision=PublicVideos.digest(data.toString());
    }
}
