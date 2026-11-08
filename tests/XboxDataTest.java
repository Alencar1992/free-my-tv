package io.freemytv;
import java.util.*;
public final class XboxDataTest {
 private static int n;
 private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
 private static void rejects(Runnable r,String m){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}check(bad,m);}
 private static String game(){return "{\"titleId\":\"123\",\"name\":\"Aventura Épica\",\"devices\":[\"XboxOne\"],\"displayImage\":\"https://images-eds-ssl.xboxlive.com/image?url=example\",\"achievement\":{\"currentAchievements\":1,\"totalAchievements\":4,\"currentGamerscore\":10,\"totalGamerscore\":100}}";}
 private static String page(String state,String token){return "{\"achievements\":[{\"id\":\"a\",\"progressState\":\""+state+"\",\"rewards\":[{\"type\":\"Gamerscore\",\"value\":\"10\"}]}],\"pagingInfo\":{\"continuationToken\":\""+token+"\"}}";}
 public static void main(String[] args){
  XboxData.Profile profile=XboxData.parseProfile("{\"content\":{\"profileUsers\":[{\"id\":\"1234567890123456\",\"settings\":[{\"id\":\"Gamertag\",\"value\":\"ExamplePlayer\"},{\"id\":\"RealName\",\"value\":\"Not consumed\"}]}]}}");
  check(profile.gamertag.equals("ExamplePlayer")&&profile.xuid.equals("1234567890123456"),"Only validated owner identity is read from the account response");
  rejects(()->XboxData.parseProfile("{\"error\":\"unauthorized\"}"),"An error object cannot be mistaken for a connected account");
  XboxData.Catalog catalog=XboxData.parseCatalog("{\"titles\":["+game()+","+game()+"],\"pagingInfo\":{\"continuationToken\":\"next page\"}}");
  check(catalog.games.size()==1&&catalog.continuation.equals("next page"),"Catalog deduplicates title IDs and preserves paging information");
  XboxData.Game g=catalog.games.get(0);
  check(g.id.equals("123")&&g.total==4&&!g.xbox360,"Catalog reads title/edition and achievement summary without inferring campaign progress");
  check(XboxData.search(catalog.games,"AVENTURA epica").size()==1,"Game search ignores case and accents");
  check(XboxData.search(catalog.games,"different").isEmpty(),"Search does not substitute an unrelated game");
  check(XboxData.coverUrl("http://images-eds.xboxlive.com/image?url=example").equals("https://images-eds-ssl.xboxlive.com/image?url=example"),"Legacy Xbox artwork is upgraded to canonical HTTPS");
  for(String bad:Arrays.asList("https://evil.test/cover.jpg","file:///tmp/cover.jpg","https://images-eds-ssl.xboxlive.com.evil.test/a","https://secret@images-eds-ssl.xboxlive.com/a","https://images-eds-ssl.xboxlive.com:8443/a"))check(XboxData.coverUrl(bad).isEmpty(),"Artwork URL cannot leave the approved CDN origin");
  Map<String,String> stats=XboxData.parseStats("{\"statlistscollection\":[{\"arrangebyfieldid\":\"private-owner\",\"stats\":[{\"name\":\"MinutesPlayed\",\"type\":\"Integer\",\"value\":\"97\"},{\"name\":\"NumHeadshots\",\"value\":\"0\"}]}]}");
  check(stats.get("MinutesPlayed").equals("97")&&stats.get("NumHeadshots").equals("0")&&stats.size()==2,"Native stats are read without copying owner identifiers");
  XboxData.Summary cached=new XboxData.Summary(g,g.unlocked,g.total,g.earnedScore,g.totalScore,stats,false);
  check(cached.time().equals("1h37min")&&cached.progress().equals("25,0%"),"Time and achievement percentage use distinct reported fields");
  check(new XboxData.Summary(g,-1,-1,-1,-1,Collections.emptyMap(),false).time().equals("—"),"Missing playtime is unavailable rather than zero");
  check(new XboxData.Summary(g,0,4,0,100,Collections.singletonMap("MinutesPlayed","0"),false).time().equals("0h00min"),"A reported zero playtime remains distinguishable from missing data");
  check(new XboxData.Summary(g,0,0,0,0,Collections.emptyMap(),false).progress().equals("—"),"No achievement denominator cannot produce a fake percentage");
  check(new XboxData.Summary(g,3,2,-1,-1,Collections.emptyMap(),false).progress().equals("—"),"Contradictory achievement totals cannot exceed100 percent");
  XboxData.AchievementPage first=XboxData.parseAchievements(page("InProgress","next")), second=XboxData.parseAchievements(page("Achieved",""));
  XboxData.Summary merged=XboxData.summary(g,Arrays.asList(first,second),stats,true);
  check(merged.total==1&&merged.unlocked==1&&merged.earnedScore==10,"Duplicate pages count each achievement once and preserve a newer unlock");
  XboxData.Summary partial=XboxData.summary(g,Collections.singletonList(first),stats,false);
  check(partial.partial&&partial.total==4&&partial.unlocked==1,"Partial pages retain catalog totals rather than claiming full completion");
  check(XboxData.metricLabel("NumHeadshots").contains("🎯")&&XboxData.metricLabel("NumBodiesLooted").contains("🎒"),"Known game metrics have meaningful emoji labels");
  check(XboxData.metricLabel("NumPokerGamesWon").contains("♠"),"Poker stats have a distinct icon");
  rejects(()->XboxData.parseAchievements("{\"achievements\":[{\"id\":\"x\"}]}"),"Missing achievement state cannot be silently counted as locked");
  rejects(()->XboxData.parseCatalog("{\"error\":\"unavailable\"}"),"A provider error cannot clear a successful catalog as an empty list");
  check(XboxData.parseCatalog("{\"titles\":[]}").games.isEmpty(),"A real empty history is valid");
  check(XboxData.parseStats("{\"users\":[{\"scids\":[{\"stats\":[{\"name\":\"MinutesPlayed\",\"value\":12}]}]}]}").get("MinutesPlayed").equals("12"),"User/scid stat responses are supported");
  System.out.println("XboxDataTest: "+n+" assertions passed");
 }
}
