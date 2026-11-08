package io.freemytv;

/** Decode bounds and crop geometry, independent of the Android renderer. */
final class ImageSizing {
    private ImageSizing() {}

    static int sampleSize(int width, int height, int maxWidth, int maxHeight) {
        validate(width, height, maxWidth, maxHeight);
        int sample = 1;
        while ((width - 1) / sample + 1 > maxWidth || (height - 1) / sample + 1 > maxHeight) {
            if (sample == 1 << 30) throw new IllegalArgumentException("Image dimensions exceed decoder bounds");
            sample *= 2;
        }
        return sample;
    }

    static float[] cover(int width, int height, int targetWidth, int targetHeight) {
        validate(width, height, targetWidth, targetHeight);
        float scale = Math.max((float) targetWidth / width, (float) targetHeight / height);
        float w = width * scale, h = height * scale;
        return new float[] {(targetWidth - w) / 2, (targetHeight - h) / 2,
                (targetWidth + w) / 2, (targetHeight + h) / 2};
    }

    private static void validate(int w, int h, int tw, int th) {
        if (w <= 0 || h <= 0 || tw <= 0 || th <= 0) throw new IllegalArgumentException("Unreadable image dimensions");
    }
}
