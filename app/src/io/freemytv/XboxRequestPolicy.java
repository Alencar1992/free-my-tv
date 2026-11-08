package io.freemytv;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
final class XboxRequestPolicy {
    static final long FRESH_MS=15*60*1000L;
    private final ArrayDeque<Long> calls=new ArrayDeque<>();
    private final LinkedHashMap<String,Long> forced=new LinkedHashMap<>();
    private final LinkedHashMap<String,Long> attempts=new LinkedHashMap<>();
    private long retryAt;
    synchronized void attempted(String target,long now){
        attempts.put(target,now);
        if(attempts.size()>1000)attempts.remove(attempts.keySet().iterator().next());
    }
    synchronized boolean automatic(String target,long now){
        Long prior=attempts.get(target);
        if(prior!=null&&now>=prior&&now-prior<FRESH_MS)return false;
        attempted(target,now);return true;
    }
    synchronized boolean acquire(long now) {
        while(!calls.isEmpty() && now-calls.peekFirst()>=3600000L) calls.removeFirst();
        if(now<retryAt || calls.size()>=100)return false;
        calls.addLast(now);return true;
    }
    synchronized boolean force(String target,long now) {
        Long prior=forced.get(target);
        if(prior!=null && now-prior<30000L)return false;
        forced.put(target,now);
        if(forced.size()>1000)forced.remove(forced.keySet().iterator().next());
        return true;
    }
    synchronized void backoff(String retry,long now) {
        long delay=60000L;
        try { delay=Math.max(1L,Math.min(24*3600L,Long.parseLong(retry)))*1000L; }
        catch(Exception ignored) {
            try {
                SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                delay=Math.max(1000L,Math.min(24*3600000L,f.parse(retry).getTime()-now));
            } catch(Exception invalid) { }
        }
        retryAt=Math.max(retryAt,now+delay);
    }
    static byte[] read(InputStream in,int max) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(Math.min(max,16384));
        byte[] chunk=new byte[8192];int n;
        while((n=in.read(chunk))!=-1) {
            if(bytes.size()>max-n)throw new IOException("Response too large");
            bytes.write(chunk,0,n);
        }
        return bytes.toByteArray();
    }
    static boolean title(String s) { return s!=null && s.matches("[0-9]{1,20}"); }
    static boolean continuation(String s) { return s!=null && s.length()<=2048 && s.matches("[A-Za-z0-9_:/+=.\\-]*"); }
}
