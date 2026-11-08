package io.freemytv;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;

/** Local game artwork. All methods perform I/O and run on a background worker. */
final class Wallpaper {
    static final String HOME = GameGallery.HOME;
    static final String LOGO = GameGallery.LOGO;
    private static final int W = 1920, H = 1080;
    private Wallpaper() {}

    static Bitmap decode(AssetManager assets, String path, int width, int height) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = assets.open(path)) { BitmapFactory.decodeStream(in, null, bounds); }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, width, height);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = assets.open(path)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            if (b == null) throw new IOException("Unreadable bundled artwork: " + path);
            return b;
        }
    }

    static Bitmap home(AssetManager assets) throws IOException { return home(assets,HOME); }

    static Bitmap home(AssetManager assets,String asset) throws IOException {
        Bitmap src = decode(assets, asset, W, H), out = null;
        try {
            out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            float[] crop = ImageSizing.cover(src.getWidth(), src.getHeight(), W, H);
            c.drawBitmap(src, null, new RectF(crop[0], crop[1], crop[2], crop[3]), p);
            c.drawColor(0x40261D3D);
            p.setShader(new LinearGradient(0, 0, W, 0, new int[] {0xC0261D3D, 0x44261D3D}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            p.setShader(new LinearGradient(0, H * .42f, 0, H, new int[] {0, 0xF0261D3D}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, H * .42f, W, H, p);
            return Backdrop.gpu(out);
        } catch (RuntimeException | OutOfMemoryError e) {
            if (out != null && !out.isRecycled()) out.recycle();
            throw e;
        } finally {
            src.recycle();
        }
    }
}
