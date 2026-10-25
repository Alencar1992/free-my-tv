package io.freemytv;

public final class GlassRasterTest {
    private static int assertions;
    private static void check(boolean ok, String why) {
        assertions++; if (!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        int[] photo = {0xFFFF0000, 0xFF0000FF};
        int[] flat = {0xFF8080FF};
        int[] covered = {0xFFFFFFFF};
        int[] a = GlassRaster.render(flat, new int[] {0}, 1, 1, photo, 2, 1, 0, 0, 1, 0);
        check(a[0] == 0, "Outside the clock glyph remains transparent");
        a = GlassRaster.render(flat, covered, 1, 1, photo, 2, 1, -100, 0, 1, 0);
        check(((a[0] >> 16) & 255) > (a[0] & 255), "Sampling clamps to left photo edge");
        a = GlassRaster.render(flat, covered, 1, 1, photo, 2, 1, 100, 0, 1, 0);
        check((a[0] & 255) > ((a[0] >> 16) & 255), "Sampling clamps to right photo edge");
        int unbent = GlassRaster.render(flat, covered, 1, 1, photo, 2, 1, 0, 0, 1, 0)[0];
        int bent = GlassRaster.render(new int[] {0xFFFF80FF}, covered, 1, 1, photo, 2, 1, 0, 0, 1, 1)[0];
        check((unbent & 255) < ((unbent >> 16) & 255), "Flat glass samples its actual position");
        check((bent & 255) > ((bent >> 16) & 255), "Surface slope refracts a different photo pixel");
        int partial = GlassRaster.render(flat, new int[] {0x80000000}, 1, 1, photo, 2, 1, 0, 0, 1, 0)[0];
        check((partial >>> 24) > 0 && (partial >>> 24) < 128, "Anti-aliased edges retain partial coverage");
        System.out.println("GlassRasterTest: " + assertions + " assertions passed");
    }
}
