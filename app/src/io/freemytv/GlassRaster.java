package io.freemytv;

/** Cached CPU refraction for Android 12. Run on a worker when a photo or the clock changes, never per frame. */
final class GlassRaster {
    private GlassRaster() {}

    static int[] render(int[] slope, int[] cover, int width, int height, int[] photo, int photoWidth,
                        int photoHeight, float originX, float originY, float photoScale, float refraction) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x, coverage = cover[i] >>> 24;
            if (coverage == 0) continue;
            float nx = (((slope[i] >> 16) & 255) - 128) / 127f;
            float ny = (((slope[i] >> 8) & 255) - 128) / 127f;
            int px = clamp(Math.round((originX + x + nx * refraction) / photoScale), photoWidth);
            int py = clamp(Math.round((originY + y + ny * refraction) / photoScale), photoHeight);
            int sample = photo[py * photoWidth + px];
            float edge = Math.min(1, (float) Math.sqrt(nx * nx + ny * ny));
            float specular = Math.max(0, -nx * .5f - ny * .8f) * .72f;
            float sheen = .18f + specular + edge * .12f;
            int color = Math.round(coverage * (.82f + .18f * edge)) << 24;
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = (sample >> shift) & 255;
                color |= Math.min(255, Math.round(channel * (1 - sheen) + 255 * sheen)) << shift;
            }
            out[i] = color;
        }
        return out;
    }

    private static int clamp(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }
}
