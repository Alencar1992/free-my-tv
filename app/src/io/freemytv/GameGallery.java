package io.freemytv;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Offline official screenshots; selection has no network or Android dependencies. */
final class GameGallery {
    static final String HOME = "wallpapers/vice-city-01.jpg", LOGO = "wallpapers/gta-vi-logo.png";
    static final class Entry {
        final String asset, category;
        private final String pt, en;
        Entry(String file, String category, String pt, String en) {
            this.asset = "wallpapers/" + file + ".jpg";
            this.category = category;
            this.pt = pt;
            this.en = en;
        }
        String title(boolean english) { return english ? en : pt; }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("vice-city-01", "leonida", "Bem-vindo a Vice City", "Welcome to Vice City"),
            new Entry("cars", "cars", "Carros de Vice City", "Cars of Vice City"),
            new Entry("jason", "jason", "Jason Duval", "Jason Duval"),
            new Entry("leonida-keys", "leonida", "Leonida Keys", "Leonida Keys"),
            new Entry("lucia", "lucia", "Lucia Caminos", "Lucia Caminos"),
            new Entry("cal-hampton", "npcs", "Cal Hampton", "Cal Hampton"),
            new Entry("vice-city-03", "leonida", "As ruas de Vice City", "The streets of Vice City"),
            new Entry("jason-lucia", "couple", "Jason e Lucia", "Jason and Lucia"),
            new Entry("port-gellhorn", "leonida", "Port Gellhorn", "Port Gellhorn"),
            new Entry("boobie-ike", "npcs", "Boobie Ike", "Boobie Ike"),
            new Entry("vice-city-06", "npcs", "Vice City à noite", "Vice City at night")
    ));

    private GameGallery() {}
    static List<Entry> entries() { return ENTRIES; }

    /** Returns null once every asset failed; a sole surviving image may stay on screen. */
    static Entry next(String previous, Set<String> failed) {
        int start = 0;
        for (int i = 0; i < ENTRIES.size(); i++) if (ENTRIES.get(i).asset.equals(previous)) { start = i + 1; break; }
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get((start + i) % ENTRIES.size());
            if (!failed.contains(e.asset)) return e;
        }
        return null;
    }
}
