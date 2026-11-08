package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.net.URI;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Bounded read-only OpenXBL payloads. No credentials, Android objects or inferred campaign progress. */
final class XboxData {
 static final int MAX_GAMES=1000,MAX_ACHIEVEMENTS=2000,JSON_LIMIT=4*1024*1024;
 private XboxData(){}
 static final class Game {
  final String id,name,cover;final boolean xbox360;final int unlocked,total,earnedScore,totalScore;
  Game(String id,String name,String cover,boolean xbox360,int unlocked,int total,int earnedScore,int totalScore){
   if(!titleId(id))throw bad();this.id=id;this.name=clean(name,160);this.cover=coverUrl(cover);this.xbox360=xbox360;
   boolean inconsistent=unlocked>=0&&total>=0&&unlocked>total;
   this.unlocked=inconsistent?-1:unlocked;this.total=inconsistent?-1:total;this.earnedScore=earnedScore;this.totalScore=totalScore;
  }
 }
 static final class Profile {final String xuid,gamertag;Profile(String id,String name){xuid=id;gamertag=name;}}
 static final class Catalog {
  final List<Game> games;final String continuation;final boolean truncated;
  Catalog(List<Game> games,String continuation,boolean truncated){this.games=Collections.unmodifiableList(new ArrayList<>(games));this.continuation=continuation;this.truncated=truncated;}
 }
 static final class Achievement {
  final String id;final boolean unlocked;final int score;
  Achievement(String id,boolean unlocked,int score){this.id=id;this.unlocked=unlocked;this.score=score;}
 }
 static final class AchievementPage {
  final List<Achievement> items;final String continuation;
  AchievementPage(List<Achievement> items,String continuation){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.continuation=continuation;}
 }
 static final class Summary {
  final Game game;final int unlocked,total,earnedScore,totalScore;final Map<String,String> stats;final boolean partial;
  Summary(Game game,int unlocked,int total,int earnedScore,int totalScore,Map<String,String> stats,boolean partial){
   this.game=game;this.unlocked=unlocked;this.total=total;this.earnedScore=earnedScore;this.totalScore=totalScore;
   this.stats=Collections.unmodifiableMap(new LinkedHashMap<>(stats));this.partial=partial;
  }
  String time(){
   String raw=null;for(Map.Entry<String,String> s:stats.entrySet())if(s.getKey().equalsIgnoreCase("MinutesPlayed"))raw=s.getValue();
   if(raw==null)return "—";
   try{BigDecimal v=new BigDecimal(raw);if(v.signum()<0||v.compareTo(new BigDecimal("100000000"))>0)return "—";
    long minutes=v.longValue();return minutes/60+"h"+String.format(Locale.ROOT,"%02d",minutes%60)+"min";
   }catch(RuntimeException e){return "—";}
  }
  String progress(){return total<=0||unlocked<0||unlocked>total?"—":String.format(Locale.ROOT,"%.1f%%",100d*unlocked/total).replace('.',',');}
 }
 static boolean titleId(String id){try{return id!=null&&id.matches("[0-9]{1,10}")&&Long.parseLong(id)>0&&Long.parseLong(id)<=4294967295L;}catch(RuntimeException e){return false;}}
 static Profile parseProfile(String json){
  try{
   JSONObject o=root(json);String id=o.optString("xuid",""),name=o.optString("gamertag","");JSONArray users=o.optJSONArray("profileUsers");
   if(users!=null&&users.length()>0){JSONObject user=users.getJSONObject(0);id=user.optString("id","");JSONArray settings=user.optJSONArray("settings");
    if(settings!=null)for(int i=0;i<Math.min(100,settings.length());i++){JSONObject setting=settings.optJSONObject(i);if(setting!=null&&"Gamertag".equals(setting.optString("id")))name=setting.optString("value","");}}
   if(!id.matches("[0-9]{1,20}")||clean(name,80).isEmpty())throw bad();return new Profile(id,clean(name,80));
  }catch(Exception e){throw bad();}
 }
 static Catalog parseCatalog(String json){
  try{
   JSONObject root=root(json);JSONArray titles=root.optJSONArray("titles");if(titles==null)throw bad();
   List<Game> games=new ArrayList<>();Set<String> seen=new HashSet<>();
   for(int i=0;i<Math.min(MAX_GAMES,titles.length());i++){
    JSONObject item=titles.optJSONObject(i);if(item==null)continue;
    String id=item.optString("titleId",""),name=clean(item.optString("name",""),160);if(!titleId(id)||name.isEmpty()||!seen.add(id))continue;
    JSONObject a=item.optJSONObject("achievement");if(a==null)a=new JSONObject();boolean old=false;
    JSONArray devices=item.optJSONArray("devices");if(devices!=null)for(int j=0;j<Math.min(20,devices.length());j++)if("Xbox360".equalsIgnoreCase(devices.optString(j)))old=true;
    games.add(new Game(id,name,item.optString("displayImage",""),old,count(a,"currentAchievements"),count(a,"totalAchievements"),count(a,"currentGamerscore"),count(a,"totalGamerscore")));
   }
   return new Catalog(games,token(root),titles.length()>MAX_GAMES);
  }catch(Exception e){throw bad();}
 }
 static AchievementPage parseAchievements(String json){
  try{
   JSONObject root=root(json);JSONArray list=root.optJSONArray("achievements");if(list==null||list.length()>MAX_ACHIEVEMENTS)throw bad();List<Achievement> out=new ArrayList<>();
   for(int i=0;i<list.length();i++){
    JSONObject a=list.getJSONObject(i);String id=clean(a.optString("id",""),80),state=a.optString("progressState","");
    if(id.isEmpty()||!(state.equals("Achieved")||state.equals("InProgress")||state.equals("NotStarted")))throw bad();
    int score=-1;JSONArray rewards=a.optJSONArray("rewards");if(rewards!=null)for(int r=0;r<Math.min(20,rewards.length());r++){
     JSONObject reward=rewards.optJSONObject(r);if(reward!=null&&"Gamerscore".equalsIgnoreCase(reward.optString("type")))score=count(reward,"value");}
    out.add(new Achievement(id,state.equals("Achieved"),score));
   }
   return new AchievementPage(out,token(root));
  }catch(Exception e){throw bad();}
 }
 static Map<String,String> parseStats(String json){
  JSONObject object=root(json);if(object.has("error")&&!object.has("stats")&&!object.has("statlistscollection")&&!object.has("users"))throw bad();
  Map<String,String> result=new LinkedHashMap<>();stats(object,result,0,new int[]{0});return Collections.unmodifiableMap(result);
 }
 private static void stats(Object object,Map<String,String> result,int depth,int[] seen){
  if(object==null||depth>12||++seen[0]>10000||result.size()>=100)return;
  if(object instanceof JSONArray){JSONArray array=(JSONArray)object;for(int i=0;i<Math.min(2000,array.length());i++)stats(array.opt(i),result,depth+1,seen);return;}
  if(!(object instanceof JSONObject))return;JSONObject o=(JSONObject)object;Object value=o.opt("value");String name=clean(o.optString("name",""),100);
  String lower=name.toLowerCase(Locale.ROOT);
  if(!name.isEmpty()&&name.matches("[\\p{L}\\p{N}_.: -]+")&&!lower.matches(".*(token|password|secret|authorization|xuid|email).*")&&!lower.equals("apikey")
    &&(value instanceof String||value instanceof Number||value instanceof Boolean))result.put(name,clean(String.valueOf(value),100));
  for(String key:new String[]{"stats","statlistscollection","groups","users","scids"})stats(o.opt(key),result,depth+1,seen);
 }
 static Summary summary(Game game,List<AchievementPage> pages,Map<String,String> stats,boolean complete){
  Map<String,Achievement> unique=new LinkedHashMap<>();boolean bounded=false;
  for(AchievementPage page:pages)for(Achievement a:page.items){
   Achievement old=unique.get(a.id);if(old!=null)unique.put(a.id,new Achievement(a.id,old.unlocked||a.unlocked,Math.max(old.score,a.score)));
   else if(unique.size()<MAX_ACHIEVEMENTS)unique.put(a.id,a);else bounded=true;
  }
  complete=complete&&!bounded;int unlocked=game.unlocked,total=game.total,earned=game.earnedScore,max=game.totalScore;
  if(complete&&!unique.isEmpty()){
   unlocked=0;total=unique.size();long score=0,all=0;boolean known=true;
   for(Achievement a:unique.values()){if(a.unlocked)unlocked++;if(a.score<0)known=false;else{all+=a.score;if(a.unlocked)score+=a.score;}}
   if(known&&all<=Integer.MAX_VALUE){earned=(int)score;max=(int)all;}
  }
  return new Summary(game,unlocked,total,earned,max,stats,!complete);
 }
 static String metricLabel(String name){
  if(name.equalsIgnoreCase("MinutesPlayed"))return "⏱️ Tempo jogado";
  if(name.equalsIgnoreCase("NumHeadshots"))return "🎯 Tiros na cabeça";
  if(name.equalsIgnoreCase("NumBodiesLooted"))return "🎒 Corpos saqueados";
  if(name.equalsIgnoreCase("NumPokerGamesWon"))return "♠️ Vitórias no pôquer";
  return "🎮 "+clean(name,80).replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');
 }
 static List<Game> search(List<Game> games,String query){String needle=fold(clean(query,120));List<Game> found=new ArrayList<>();for(Game game:games)if(fold(game.name).contains(needle))found.add(game);return Collections.unmodifiableList(found);}
 private static String fold(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
 static String coverUrl(String input){
  if(input==null||input.length()>4096)return "";
  try{
   URI uri=new URI(input);String host=uri.getHost();if(host==null||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawFragment()!=null)return "";
   host=host.toLowerCase(Locale.ROOT);if(!host.equals("images-eds-ssl.xboxlive.com")&&!host.equals("images-eds.xboxlive.com")&&!host.equals("store-images.s-microsoft.com"))return "";
   if(!"http".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(uri.getScheme()))return "";
   if(host.equals("images-eds.xboxlive.com"))host="images-eds-ssl.xboxlive.com";
   return "https://"+host+(uri.getRawPath()==null?"":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
  }catch(Exception e){return "";}
 }
 private static int count(JSONObject object,String key){Object raw=object.opt(key);if(raw==null||raw==JSONObject.NULL)return -1;try{BigDecimal n=new BigDecimal(String.valueOf(raw));return n.signum()>=0&&n.scale()<=0&&n.compareTo(new BigDecimal(Integer.MAX_VALUE))<=0?n.intValueExact():-1;}catch(RuntimeException e){return -1;}}
 private static String token(JSONObject object){JSONObject page=object.optJSONObject("pagingInfo");String token=page==null?object.optString("continuationToken",""):page.optString("continuationToken","");if(token.length()>2048||token.matches("(?s).*[\\p{Cntrl}].*"))throw bad();return token;}
 private static JSONObject root(String json){
  if(json==null||json.length()>JSON_LIMIT)throw bad();
  try{JSONTokener reader=new JSONTokener(json);Object parsed=reader.nextValue();if(!(parsed instanceof JSONObject)||reader.nextClean()!=0)throw bad();JSONObject o=(JSONObject)parsed;Object body=o.opt("content");if(body instanceof JSONObject)o=(JSONObject)body;return o;}catch(Exception e){throw bad();}
 }
 private static String clean(String value,int max){String s=value==null?"":value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]","").trim();return s.length()>max?s.substring(0,max):s;}
 private static IllegalArgumentException bad(){return new IllegalArgumentException("Xbox data unavailable or invalid");}
}
