package io.freemytv;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared IBM Plex Sans text, JetBrains Mono measurements and the original ViktorKav monogram.
 */
final class Ui {
    private Ui() {}

    static final Locale PT = new Locale("pt", "BR");
    /** English or Portuguese, from config.properties ("language") or, with "auto", the TV's own language. */
    static volatile boolean en = english();
    static Typeface thin, light, displayMedium, displaySemibold, regular, medium, semibold, mono;
    private static Drawable brand;
    private static boolean loaded;
    private static Fonts channelFonts, glassFonts, gameFonts;
    private static final Rect BOUNDS = new Rect();
    private static final RectF OVAL = new RectF();

    static synchronized void init(Context c) {
        if (loaded) return;
        loaded = true;
        AssetManager a = c.getAssets();
        channelFonts = new Fonts(a, Skin.VIKTORKAV);
        thin = channelFonts.thin; light = channelFonts.light;
        displayMedium = channelFonts.displayMedium; displaySemibold = channelFonts.displaySemibold;
        regular = channelFonts.regular; medium = channelFonts.medium;
        semibold = channelFonts.semibold; mono = channelFonts.mono;
        brand = c.getDrawable(R.drawable.vk).mutate();
        brand.setTint(TvTheme.BRAND);
    }

    /** A bundled variable font at the requested weight; the system font if the file is missing. */
    private static Typeface font(AssetManager a, String path, int weight) {
        if (has(a, path)) {
            try {
                Typeface t = new Typeface.Builder(a, path)
                        .setFontVariationSettings("'wght' " + weight).build();
                if (t != null) return t;
            } catch (RuntimeException ignored) {
            }
        }
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    private static final String FONT = "fonts/IBMPlexSans.ttf";

    static synchronized Fonts fonts(Context c, Skin skin) {
        init(c);
        if (skin == Skin.GTA_VI) {
            if (gameFonts == null) gameFonts = new Fonts(c.getAssets(), skin);
            return gameFonts;
        }
        if (!skin.glass) return channelFonts;
        if (glassFonts == null) glassFonts = new Fonts(c.getAssets(), skin);
        return glassFonts;
    }

    /** Font sets are immutable: a paused Home never inherits fonts from another view. */
    static final class Fonts {
        final Typeface thin, light, displayMedium, displaySemibold, regular, medium, semibold, mono;
        private final boolean proportional;
        Fonts(AssetManager a, Skin skin) {
            boolean glass = skin.glass, game = skin == Skin.GTA_VI;
            proportional = glass || game;
            String path = proportional ? "fonts/InterVariable.ttf" : FONT;
            thin = font(a, path, glass ? 200 : 300);
            light = font(a, path, 300);
            displayMedium = font(a, path, game ? 700 : 500); displaySemibold = font(a, path, game ? 700 : 600);
            regular = font(a, path, 400); medium = font(a, path, 500); semibold = font(a, path, game ? 700 : 600);
            mono = game ? Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    : glass ? regular : font(a, "fonts/JetBrainsMono.ttf", 400);
        }
        Paint numbers(Typeface face, float size, int color) {
            Paint p = text(proportional ? face : Typeface.create(mono, face.getWeight(), false), size, color);
            p.setFontFeatureSettings("'tnum'");
            return p;
        }
    }

    static void brand(Canvas c, float x, float y, float size) {
        if (brand == null) return;
        brand.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size * 1297 / 1332));
        brand.draw(c);
    }

    /** "en"/"pt" in config.properties, or "auto": Portuguese when the TV is set to Portuguese, English otherwise. */
    static boolean english() {
        String lang = Config.LANGUAGE.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("pt")) return false;
        if (lang.startsWith("en")) return true;
        return !"pt".equals(Locale.getDefault().getLanguage());
    }

    private static boolean has(AssetManager a, String path) {
        try (InputStream in = a.open(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    static Paint text(Typeface face, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    /** Fixed-width digits (the clock and the prices don't jitter when a digit changes). */
    static Paint numbers(Typeface face, float size, int color) {
        Paint p = text(Typeface.create(mono, face.getWeight(), false), size, color);
        p.setFontFeatureSettings("'tnum'");
        return p;
    }

    /** Height of capital letters (and digits) above the baseline. */
    static float cap(Paint p) {
        synchronized (BOUNDS) {
            p.getTextBounds("H8", 0, 2, BOUNDS);
            return -BOUNDS.top;
        }
    }

    /** Half the cap height: added to the center line, it gives the baseline that centers the text. */
    static float halfCap(Paint p) {
        return cap(p) / 2f;
    }

    /** Left bearing of the first character (large, thin fonts start a little inward). */
    static float leftBearing(Paint p, String s) {
        if (s.isEmpty()) return 0;
        synchronized (BOUNDS) {
            p.getTextBounds(s, 0, 1, BOUNDS);
            return BOUNDS.left;
        }
    }

    static int alpha(int color, float a) {
        int base = (color >>> 24) & 255;
        return (Math.round(base * Math.max(0, Math.min(1, a))) << 24) | (color & 0xFFFFFF);
    }

    static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    static String tr(String pt, String english) {
        return en ? english : pt;
    }

    /** "21:47" (in English, "9:47", like the iPhone in the US). */
    static String clock(long now) {
        return en ? new SimpleDateFormat("h:mm", Locale.US).format(new Date(now))
                : new SimpleDateFormat("HH:mm", PT).format(new Date(now));
    }

    /** "5:21" (sunrise and sunset; in English, "5:21 AM"). */
    static String time(long at) {
        return en ? new SimpleDateFormat("h:mm a", Locale.US).format(new Date(at))
                : new SimpleDateFormat("H:mm", PT).format(new Date(at));
    }

    /** "domingo, 18 de outubro" (lowercase, like the iPhone in Portuguese); "Sunday, October 18" in English. */
    static String date(long now) {
        return en ? new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date(now))
                : new SimpleDateFormat("EEEE, d 'de' MMMM", PT).format(new Date(now));
    }

    /** Spinning arc (work in progress). */
    static void spinner(Canvas c, float cx, float cy, float r, float seconds, Paint stroke) {
        OVAL.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(OVAL, (seconds * 360f) % 360f, 270f, false, stroke);
    }
}
