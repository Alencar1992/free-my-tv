package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Private, independently stored settings; no account or launcher mode is changed here. */
final class Personalization {
    private Personalization() {}
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("home-personalization", Context.MODE_PRIVATE); }
    private static List<String> strings(String json) {
        List<String> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(json); for (int i=0;i<a.length();i++) out.add(a.getString(i)); }
        catch (Exception ignored) { out.clear(); }
        return out;
    }
    static List<String> appOrder(Context c) {
        SharedPreferences p = prefs(c);
        return p.contains("apps") ? strings(p.getString("apps", "[]")) : null;
    }
    static void saveApps(Context c,List<String> apps) {
        List<String> checked = new ArrayList<>(); for (String pkg:apps) checked=AppOrder.add(checked,pkg);
        prefs(c).edit().putString("apps",new JSONArray(checked).toString()).apply();
    }
    static void restoreApps(Context c) { prefs(c).edit().remove("apps").apply(); }
    static List<String> favorites(Context c) {
        try { return validateFavorites(strings(prefs(c).getString("favorites","[]"))); }
        catch (IllegalArgumentException e) { return new ArrayList<>(); }
    }
    private static List<String> validateFavorites(List<String> refs) {
        if (refs.size()>12) throw new IllegalArgumentException("Escolha até 12 canais");
        List<String> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(String ref:refs) {String normalized=ChannelRef.normalize(ref); if(seen.add(ChannelRef.key(normalized)))out.add(normalized);}
        return out;
    }
    static void saveFavorites(Context c,List<String> refs) {
        prefs(c).edit().putString("favorites",new JSONArray(validateFavorites(refs)).toString()).apply();
    }
    static boolean publicDiscovery(Context c) { return prefs(c).getBoolean("public",Config.CHANNEL_FEED_URL.trim().isEmpty()); }
    static void setPublicDiscovery(Context c,boolean enabled) { prefs(c).edit().putBoolean("public",enabled).apply(); }
    static boolean trailersEnabled(Context c) { return prefs(c).getBoolean("trailers",true); }
    static void setTrailersEnabled(Context c,boolean enabled) { prefs(c).edit().putBoolean("trailers",enabled).apply(); }
    static final class Trailer {
        final String pkg,label,channel; final boolean official;
        Trailer(String pkg,String label,String channel,boolean official) { this.pkg=pkg;this.label=label;this.channel=channel;this.official=official; }
    }
    private static final String[][] PRESETS={
        {"com.netflix.ninja","Netflix Brasil","@NetflixBrasil"},
        {"com.amazon.amazonvideo.livingroom","Prime Video Brasil","@PrimeVideoBR"},
        {"com.disney.disneyplus","Disney+ Brasil","@disneyplusbr"},
        {"com.wbd.stream","Max Brasil","@StreamMaxBR"},
        {"com.hbo.hbonow","Max Brasil","@StreamMaxBR"}
    };
    private static boolean isOfficial(Trailer source) {
        for(String[] preset:PRESETS) if(preset[0].equals(source.pkg)&&ChannelRef.key(preset[2]).equals(ChannelRef.key(source.channel))) return true;
        return false;
    }
    private static boolean installed(Context c,String pkg) {
        PackageManager pm=c.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg)!=null || pm.getLaunchIntentForPackage(pkg)!=null;
    }
    static List<Trailer> trailerSources(Context c) {
        List<Trailer> out=new ArrayList<>();
        SharedPreferences p=prefs(c);
        if(!p.contains("trailerSources")) {
            Set<String> selected=new HashSet<>(HomeApps.selectedPackages(c)), seen=new HashSet<>();
            for(String[] preset:PRESETS) if(selected.contains(preset[0])&&installed(c,preset[0])&&seen.add(preset[2]))
                out.add(new Trailer(preset[0],preset[1],ChannelRef.normalize(preset[2]),true));
            return out;
        }
        try {
            JSONArray array=new JSONArray(p.getString("trailerSources","[]"));
            for(int i=0;i<Math.min(8,array.length());i++) {
                JSONObject item=array.getJSONObject(i);
                String pkg=item.getString("pkg"),channel=ChannelRef.normalize(item.getString("channel"));
                Trailer candidate=new Trailer(pkg,item.getString("label"),channel,item.optBoolean("official",false));
                if(AppOrder.valid(pkg)&&installed(c,pkg))out.add(new Trailer(pkg,candidate.label,channel,candidate.official&&isOfficial(candidate)));
            }
        } catch(Exception ignored) { }
        return out;
    }
    static void saveTrailerSources(Context c,List<Trailer> sources) {
        if(sources.size()>8)throw new IllegalArgumentException("Escolha até 8 fontes de trailers");
        JSONArray array=new JSONArray(); Set<String> seen=new HashSet<>();
        for(Trailer source:sources) {
            if(!AppOrder.valid(source.pkg)||source.label==null||source.label.trim().isEmpty()||source.label.length()>80)
                throw new IllegalArgumentException("Fonte de trailer inválida");
            String channel=ChannelRef.normalize(source.channel);
            if(!seen.add(source.pkg+"|"+ChannelRef.key(channel)))continue;
            JSONObject item=new JSONObject();
            try { item.put("pkg",source.pkg);item.put("label",source.label.trim());item.put("channel",channel);item.put("official",source.official&&isOfficial(source)); }
            catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
            array.put(item);
        }
        prefs(c).edit().putString("trailerSources",array.toString()).apply();
    }
    static void restoreTrailerSources(Context c) { prefs(c).edit().remove("trailerSources").apply(); }
}
