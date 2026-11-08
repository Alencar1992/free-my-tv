package io.freemytv;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Date requested by the user; all boundaries are Brasília calendar midnights. No network. */
final class LaunchCountdown {
    enum Phase { COUNTING, LAUNCH_DAY, HIDDEN }
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    private static final long START=LocalDate.of(2026,11,19).atStartOfDay(ZONE).toInstant().toEpochMilli();
    private static final long END=LocalDate.of(2026,11,20).atStartOfDay(ZONE).toInstant().toEpochMilli();
    static final class State {
        final Phase phase;
        final long days;
        final int hours,minutes,seconds;
        private State(Phase phase,long totalSeconds){
            this.phase=phase;days=totalSeconds/86400;
            hours=(int)(totalSeconds/3600%24);minutes=(int)(totalSeconds/60%60);seconds=(int)(totalSeconds%60);
        }
        String digits(){return String.format(Locale.ROOT,"%02d/%02d/%02d/%02d",days,hours,minutes,seconds);}
    }
    private LaunchCountdown(){}
    static State at(long epochMillis){
        if(epochMillis>=END)return new State(Phase.HIDDEN,0);
        if(epochMillis>=START)return new State(Phase.LAUNCH_DAY,0);
        long remaining=START-epochMillis;
        return new State(Phase.COUNTING,remaining/1000+(remaining%1000==0?0:1));
    }
}
