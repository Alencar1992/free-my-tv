package io.freemytv;

public final class SkinTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Skin gta = Skin.fromId("gta-vi");
        check("gta-vi".equals(gta.id), "Saved GTA VI choice must open its own appearance");
        check("GTA VI".equals(gta.label), "GTA VI is visible in the appearance selector");
        check(Skin.fromId("viktorkav") == Skin.VIKTORKAV, "Existing graphite choice survives the update");
        check(Skin.fromId("liquid-glass") == Skin.LIQUID_GLASS, "Existing glass choice survives the update");
        Skin.Palette gameDay = new Skin.Palette(), gameNight = new Skin.Palette();
        gta.apply(gameDay, 0);
        gta.apply(gameNight, 1);
        check(gameDay.BACKGROUND == gameNight.BACKGROUND, "GTA VI keeps its sunset palette all day");
        check(gameDay.FOCUS == 0xFFFFD6A3, "GTA VI focus uses the reference's peach accent");
        check(!gta.glass, "GTA VI does not require a glass shader for its artwork");
        for (String id : new String[] {null, "", "removed-skin", "LIQUID-GLASS"})
            check(Skin.fromId(id) == Skin.VIKTORKAV, "Unknown skin must open the default launcher");
        for (Skin skin : Skin.values()) check(Skin.fromId(skin.id) == skin, "Saved choice must round trip");
        check(Skin.VIKTORKAV.isNight(false), "ViktorKav stays graphite during the day");
        check(Skin.VIKTORKAV.isNight(true), "ViktorKav stays graphite at night");
        check(!Skin.LIQUID_GLASS.isNight(false), "Apple follows local daytime");
        check(Skin.LIQUID_GLASS.isNight(true), "Apple follows local nighttime");
        Skin.Palette a = new Skin.Palette(), b = new Skin.Palette();
        Skin.VIKTORKAV.apply(a, 0);
        Skin.VIKTORKAV.apply(b, 1);
        check(a.BACKGROUND == b.BACKGROUND && a.TEXT == b.TEXT, "Default cannot inherit Apple's day palette");
        int fixed = a.BACKGROUND;
        Skin.LIQUID_GLASS.apply(b, 0);
        check(a.BACKGROUND == fixed, "A second renderer cannot mutate another renderer's palette");
        int light = b.BACKGROUND;
        Skin.LIQUID_GLASS.apply(b, 1);
        int dark = b.BACKGROUND;
        check(light != dark, "Apple must visibly follow day/night");
        Skin.LIQUID_GLASS.apply(b, -1);
        check(b.BACKGROUND == light, "Clamp before day endpoint");
        Skin.LIQUID_GLASS.apply(b, 2);
        check(b.BACKGROUND == dark, "Clamp after night endpoint");
        for (Skin skin : Skin.values()) for (float t : new float[] {0, 1}) {
            skin.apply(b, t);
            check(contrast(b.TEXT, b.SURFACE) >= 7, "Card text remains legible in both appearances");
            check(contrast(b.ON_FOCUS, b.FOCUS) >= 7, "Focused settings row remains legible");
            check(contrast(b.ACCENT, b.SURFACE) >= 4.5, "Section headings stay visible in daylight");
            for (float focus : new float[] {0, 1})
                check(contrast(b.appLabel(focus), b.SURFACE) >= 4.5,
                        "All Apps labels must contrast with light and dark surfaces at rest and on focus");
        }
        System.out.println("SkinTest: " + assertions + " assertions passed");
    }
    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int color) {
        double result = 0;
        double[] weights = {.2126, .7152, .0722};
        for (int i = 0; i < 3; i++) {
            double v = ((color >> (16 - 8 * i)) & 255) / 255.0;
            result += weights[i] * (v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4));
        }
        return result;
    }
}
