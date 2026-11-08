package io.freemytv;

public final class ImageSizingTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int[] source : new int[][] {{1920,1080},{3840,2160},{800,600},{600,800},{100,50000},{Integer.MAX_VALUE,Integer.MAX_VALUE}}) {
            int sample = ImageSizing.sampleSize(source[0], source[1], 1920, 1080);
            check(sample > 0 && (sample & (sample - 1)) == 0, "Decoder sample is a positive power of two");
            check((source[0] - 1) / sample + 1 <= 1920, "Oversized sources cannot decode beyond target width");
            check((source[1] - 1) / sample + 1 <= 1080, "Very tall sources cannot allocate unbounded memory");
        }
        for (int[] source : new int[][] {{1920,1080},{4000,1000},{1000,4000}}) {
            float[] cover = ImageSizing.cover(source[0], source[1], 1920, 1080);
            check(cover[0] <= 0 && cover[1] <= 0 && cover[2] >= 1920 && cover[3] >= 1080, "Center crop fills the screen");
            check(Math.abs((cover[2]-cover[0])/(cover[3]-cover[1]) - (float)source[0]/source[1]) < .001, "Center crop preserves image proportions");
            check(Math.abs(cover[0] + cover[2] - 1920) < .01 && Math.abs(cover[1] + cover[3] - 1080) < .01, "Crop remains centered");
        }
        for (int[] invalid : new int[][] {{0,100},{100,0},{-1,100}}) {
            boolean rejected = false;
            try { ImageSizing.sampleSize(invalid[0], invalid[1], 1920, 1080); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid decoded image bounds must fail safely");
        }
        System.out.println("ImageSizingTest: " + assertions + " assertions passed");
    }
}
