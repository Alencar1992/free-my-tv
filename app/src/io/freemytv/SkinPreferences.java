package io.freemytv;

import android.content.Context;

/** One persisted selection shared by Home, settings and new screensaver instances. */
final class SkinPreferences {
    private SkinPreferences() {}
    static Skin load(Context c) {
        return Skin.fromId(c.getSharedPreferences("home-ui", Context.MODE_PRIVATE).getString("skin", null));
    }
    static void save(Context c, Skin skin) {
        c.getSharedPreferences("home-ui", Context.MODE_PRIVATE).edit().putString("skin", skin.id).apply();
    }
}
