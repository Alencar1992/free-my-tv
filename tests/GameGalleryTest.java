package io.freemytv;

import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class GameGalleryTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Set<String> assets = new HashSet<>(), categories = new HashSet<>();
        check(GameGallery.entries().size() >= 8, "Offline gallery offers a varied set of real screenshots");
        for (GameGallery.Entry e : GameGallery.entries()) {
            check(assets.add(e.asset), "No duplicate local photos in the catalog");
            check(e.asset.startsWith("wallpapers/") && !e.asset.contains("..") && !e.asset.contains(":"), "Gallery never depends on a remote URL");
            check(Files.isRegularFile(Paths.get("app/assets", e.asset)), "Each gallery photo is actually bundled");
            check(!e.title(false).isEmpty() && !e.title(true).isEmpty(), "Every photo has Portuguese and English labels");
            categories.add(e.category);
        }
        for (String category : new String[] {"leonida", "cars", "npcs", "jason", "lucia", "couple"})
            check(categories.contains(category), "Requested category is available: " + category);
        Set<String> failed = new HashSet<>(), seen = new HashSet<>();
        GameGallery.Entry first = GameGallery.next(null, failed), previous = null;
        for (int i = 0; i < GameGallery.entries().size(); i++) {
            GameGallery.Entry next = GameGallery.next(previous == null ? null : previous.asset, failed);
            check(next != null && seen.add(next.asset), "A full cycle shows every image exactly once");
            check(previous == null || !previous.asset.equals(next.asset), "Consecutive slides differ when alternatives exist");
            previous = next;
        }
        check(GameGallery.next(previous.asset, failed) == first, "The gallery wraps around indefinitely");
        failed.add(first.asset);
        check(!GameGallery.next(null, failed).asset.equals(first.asset), "An unreadable first photo is skipped");
        failed.addAll(assets);
        check(GameGallery.next(previous.asset, failed) == null, "All failed images terminate selection for a clock fallback");
        failed.remove(first.asset);
        check(GameGallery.next(first.asset, failed) == first, "A single remaining photo can remain visible");
        check(GameGallery.next("removed-photo", failed) == first, "A removed previous photo cannot break the cycle");
        boolean immutable = false;
        try { GameGallery.entries().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "A view cannot mutate another view's catalog");
        check(Files.isRegularFile(Paths.get("app/assets", GameGallery.LOGO)), "The real game logo is included");
        System.out.println("GameGalleryTest: " + assertions + " assertions passed");
    }
}
