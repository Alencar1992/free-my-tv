package io.freemytv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VideoRailTest {
    private static int checks;
    private static final class Item implements VideoRail.Item {
        final String id, channel;
        Item(String id, String channel) { this.id = id; this.channel = channel; }
        public String id() { return id; }
        public String channel() { return channel; }
    }
    public static void main(String[] args) {
        VideoRail<Item> rail = new VideoRail<>();
        yes(rail.current().isEmpty(), "Empty feed");
        yes(!rail.next() && !rail.previous(), "Empty navigation");
        List<Item> source = Arrays.asList(i("a1", "a"), i("a2", "a"), i("b1", "b"), i("c1", "c"),
                i("d1", "d"), i("e1", "e"), i("f1", "f"), i("a3", "a"), i("b2", "b"), i("c2", "c"));
        rail.merge(source);
        ids(rail.current(), "a1", "b1", "c1");
        ids(rail.peekNext(), "d1", "e1", "f1");
        ids(rail.current(), "a1", "b1", "c1");
        yes(!rail.previous(), "No wrap at beginning");
        yes(rail.next(), "Advance at right edge");
        eq(3, rail.start());
        ids(rail.current(), "d1", "e1", "f1");
        rail.merge(source);
        yes(rail.previous(), "History");
        ids(rail.current(), "a1", "b1", "c1");
        yes(rail.next() && rail.next(), "Reuse reserved page then build the next");
        ids(rail.current(), "a2", "b2", "c2");
        yes(rail.next(), "Incomplete tail");
        ids(rail.current(), "a3");
        yes(!rail.next(), "Do not repeat seen videos at the end");
        rail.merge(Arrays.asList(i("a3", "a"), i("g1", "g"), i("h1", "h"), i("j1", "j")));
        yes(rail.next(), "A refreshed catalog appends new suggestions");
        ids(rail.current(), "g1", "h1", "j1");
        rail.rewind();
        ids(rail.current(), "a1", "b1", "c1");
        eq(0, rail.start());
        Set<String> seen = new HashSet<>();
        do {
            Set<String> channels = new HashSet<>();
            for (Item item : rail.current()) {
                yes(seen.add(item.id), "No duplicate video across pages");
                yes(channels.add(item.channel), "Different channels within a group");
            }
        } while (rail.next());
        eq(13, seen.size());
        rail.clear();
        List<Item> large = new ArrayList<>();
        for (int n = 0; n < 250; n++) large.add(i("video-" + n, "channel-" + (n % 37)));
        rail.merge(large);
        seen.clear();
        do {
            yes(rail.current().size() <= 3, "Bounded visible group");
            Set<String> channels = new HashSet<>();
            for (Item item : rail.current()) {
                yes(seen.add(item.id), "Large catalog never repeats a video");
                yes(channels.add(item.channel), "Large catalog keeps channel diversity");
            }
            rail.merge(large);
        } while (rail.next());
        eq(250, seen.size());
        rail.clear();
        rail.merge(Collections.singletonList(i("solo", "a")));
        ids(rail.current(), "solo");
        yes(!rail.previous() && !rail.next(), "A new tab has separate history");
        try { rail.current().clear(); throw new AssertionError("Mutable page"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("VideoRailTest: " + checks + " assertions passed");
    }
    private static Item i(String id, String channel) { return new Item(id, channel); }
    private static void ids(List<Item> items, String... expected) {
        eq(expected.length, items.size());
        for (int n = 0; n < expected.length; n++) yes(expected[n].equals(items.get(n).id), "Unexpected item at " + n);
    }
    private static void eq(int a, int b) { yes(a == b, "Expected " + a + ", got " + b); }
    private static void yes(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
