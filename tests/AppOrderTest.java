package io.freemytv;
import java.util.*;
public final class AppOrderTest {
    private static int n;
    private static void check(boolean b,String m) { n++; if(!b) throw new AssertionError(m); }
    public static void main(String[] args) {
        List<String> defaults=Arrays.asList("tv.alpha.app","tv.beta.app");
        Set<String> installed=new HashSet<>(Arrays.asList("tv.alpha.app","tv.beta.app","tv.extra.app"));
        check(AppOrder.visible(null,defaults,installed).equals(defaults),"Existing installations keep their default dock");
        check(AppOrder.visible(Collections.emptyList(),defaults,installed).isEmpty(),"An intentionally empty dock must stay empty");
        check(AppOrder.visible(Arrays.asList("tv.extra.app","missing.app","tv.extra.app","tv.alpha.app"),defaults,installed).equals(Arrays.asList("tv.extra.app","tv.alpha.app")),"User order is stable, duplicates and missing apps are skipped");
        List<String> moved=AppOrder.move(defaults,1,-1);
        check(moved.equals(Arrays.asList("tv.beta.app","tv.alpha.app")),"Moving up changes only the requested order");
        check(defaults.get(0).equals("tv.alpha.app"),"Moving cannot mutate a caller's stored list");
        check(AppOrder.move(defaults,0,-1).equals(defaults),"Moving beyond the beginning does not wrap");
        check(AppOrder.remove(defaults,"tv.alpha.app").equals(Collections.singletonList("tv.beta.app")),"Removing a shortcut preserves the others");
        check(AppOrder.add(defaults,"tv.extra.app").size()==3,"Installed apps can be added");
        check(AppOrder.add(defaults,"tv.alpha.app").size()==2,"Repeated add is idempotent");
        List<String> full=new ArrayList<>(); for(int i=0;i<7;i++) full.add("tv.app"+i);
        boolean rejected=false;try { AppOrder.add(full,"tv.other.app"); }catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"The editor must report the seven-app limit rather than silently lose choices");
        rejected=false;try {AppOrder.add(defaults,"not a package");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"Malformed package identifiers are rejected");
        System.out.println("AppOrderTest: "+n+" assertions passed");
    }
}
