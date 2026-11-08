package io.freemytv;
import java.time.Instant;
import java.util.TimeZone;
public final class LaunchCountdownTest {
    private static int checks;
    private static long time(String iso){return Instant.parse(iso).toEpochMilli();}
    private static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        LaunchCountdown.State start=LaunchCountdown.at(time("2026-11-08T03:00:00Z"));
        check(start.phase==LaunchCountdown.Phase.COUNTING,"The counter is shown before the date requested by the user");
        check(start.digits().equals("11/00/00/00"),"Days use the Brasília midnight boundary, not UTC midnight");
        LaunchCountdown.State mixed=LaunchCountdown.at(time("2026-11-17T00:55:54Z"));
        check(mixed.digits().equals("02/02/04/06"),"Days, hours, minutes and seconds are independently formatted");
        check(LaunchCountdown.at(time("2026-11-19T02:59:59.999Z")).digits().equals("00/00/00/01"),"A fractional last second does not show premature zero");
        LaunchCountdown.State launch=LaunchCountdown.at(time("2026-11-19T03:00:00Z"));
        check(launch.phase==LaunchCountdown.Phase.LAUNCH_DAY && launch.digits().equals("00/00/00/00"),"At midnight the counter stops at zero and enters launch-day state");
        check(LaunchCountdown.at(time("2026-11-20T02:59:59.999Z")).phase==LaunchCountdown.Phase.LAUNCH_DAY,"The launch message lasts for all of November19 in Brasília");
        check(LaunchCountdown.at(time("2026-11-20T03:00:00Z")).phase==LaunchCountdown.Phase.HIDDEN,"At midnight November20 both counter and message disappear");
        check(LaunchCountdown.at(time("2030-01-01T00:00:00Z")).phase==LaunchCountdown.Phase.HIDDEN,"A later visit cannot restart an expired counter");
        TimeZone prior=TimeZone.getDefault();
        try{
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            check(LaunchCountdown.at(time("2026-11-19T02:59:59Z")).phase==LaunchCountdown.Phase.COUNTING,"Device timezone does not move the chosen Brasília launch date");
        }finally{TimeZone.setDefault(prior);}
        check(LaunchCountdown.at(time("2026-11-20T03:00:00Z")).phase==LaunchCountdown.Phase.HIDDEN
                && LaunchCountdown.at(time("2026-11-18T03:00:00Z")).digits().equals("01/00/00/00"),"Clock correction is recalculated from wall time, not a cached ticking total");
        System.out.println("LaunchCountdownTest: "+checks+" assertions passed");
    }
}
