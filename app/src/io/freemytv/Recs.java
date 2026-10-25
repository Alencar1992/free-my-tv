package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Subscription videos from the independent recommendations section of the LAN feed. */
final class Recs {
    static final class Pick implements VideoRail.Item {
        int id;
        String videoId, channelId, url, kind, title, backdrop, meta = "", app, pkg;
        public String id() { return videoId; }
        public String channel() { return channelId; }
        boolean detailed;
    }

    interface Listener { void onRecs(); }
    private static Recs instance;
    static synchronized Recs get(Context c) {
        if (instance == null) instance = new Recs(c.getApplicationContext());
        return instance;
    }

    private static final int IMAGE_LIMIT = 3 * 1024 * 1024;
    private static final ExecutorService STATUS_IO = Executors.newSingleThreadExecutor();
    private static volatile JSONObject lastStatus = new JSONObject();
    private static long statusReadAt;
    private final Context context;
    private final ChannelSource source;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Backdrop> images = new LinkedHashMap<>(16, 0.75f, true);
    private List<Pick> imageWindow = new ArrayList<>();
    private boolean decoding;
    private final Map<String, Long> imageFailures = new LinkedHashMap<>();
    private volatile List<Pick> picks = new ArrayList<>();
    private volatile boolean light, prefetching;
    private ChannelFeed lastFeed;
    private boolean listEnglish;

    private Recs(Context c) {
        context = c;
        source = ChannelSource.get(c);
        io.execute(() -> {
            File dir = new File(context.getCacheDir(), "channel-thumbnails");
            File[] abandoned = dir.listFiles((directory, name) -> name.endsWith(".part"));
            if (abandoned != null) for (File file : abandoned) file.delete();
            prune(dir);
        });
        rebuild();
        source.addListener(() -> {
            rebuild();
            notifyListeners();
        });
    }

    void addListener(Listener listener) { listeners.addIfAbsent(listener); }
    void removeListener(Listener listener) { listeners.remove(listener); }
    static boolean hasKey(Context c) { return !Config.CHANNEL_FEED_URL.trim().isEmpty(); }
    String error() { return source.error(); }
    String sourceRevision() { return lastFeed == null ? "" : lastFeed.recommendationsSource; }
    boolean resetRecommendations() {
        ChannelFeed feed = lastFeed;
        if (feed == null) return true;
        return feed.recommendationsStatus.equals("authorization_required")
                || (feed.recommendationsStatus.equals("loading") && feed.recommendations.isEmpty());
    }
    static String periodLabel() { return Ui.tr("SUAS INSCRIÇÕES", "YOUR SUBSCRIPTIONS"); }
    static String serviceName(Pick pick) { return pick == null ? "" : "YouTube"; }
    static String key(Pick pick) { return pick.videoId; }

    /** Settings is a separate process. Its polling never performs disk I/O on the UI thread. */
    static synchronized JSONObject status(Context c) {
        long now = System.currentTimeMillis();
        if (now < statusReadAt || now - statusReadAt >= 2000) {
            statusReadAt = now;
            File file = new File(c.getFilesDir(), "channel-status.json");
            STATUS_IO.execute(() -> {
                try (InputStream in = new FileInputStream(file)) {
                    lastStatus = new JSONObject(ChannelSource.read(in, 8192));
                } catch (Exception ignored) { }
            });
        }
        return lastStatus;
    }

    private void rebuild() {
        ChannelFeed feed = source.feed();
        if (feed == lastFeed && listEnglish == Ui.en) return;
        List<Pick> list = new ArrayList<>();
        DateTimeFormatter date = DateTimeFormatter.ofPattern(Ui.en ? "MMM d, yyyy" : "dd/MM/yyyy",
                Ui.en ? Locale.US : new Locale("pt", "BR")).withZone(ZoneId.systemDefault());
        for (ChannelFeed.Video video : feed.recommendations) {
            if (!video.nonShortVerified) continue;
            Pick pick = new Pick();
            pick.videoId = video.id;
            pick.channelId = video.channelId;
            pick.id = video.id.hashCode();
            pick.url = "https://www.youtube.com/watch?v=" + video.id;
            pick.kind = video.kind;
            pick.title = video.title;
            pick.backdrop = video.thumbnail;
            pick.app = "youtube";
            pick.pkg = "com.google.android.youtube.tv";
            pick.detailed = true;
            pick.meta = video.channelTitle + " · " + date.format(Instant.ofEpochMilli(video.publishedAt));
            list.add(pick);
        }
        picks = list;
        lastFeed = feed;
        listEnglish = Ui.en;
    }

    List<Pick> now() { return now(null); }
    List<Pick> now(String kind) {
        List<Pick> out = new ArrayList<>();
        if (lastFeed == null) return out;
        for (ChannelFeed.Video video : lastFeed.selectedRecommendations(kind, 9)) {
            for (Pick pick : picks) if (pick.videoId.equals(video.id)) {
                out.add(pick);
                break;
            }
        }
        return out;
    }

    List<Pick> catalog(String kind) {
        List<Pick> out = new ArrayList<>();
        for (Pick pick : picks) if (kind == null || kind.equals(pick.kind)) out.add(pick);
        return out;
    }

    List<Pick> wanted() {
        return now();
    }

    void refreshIfStale() {
        source.refreshIfStale();
        rebuild();
        // Home requests only its visible group and the next three thumbnails.
    }

    void setLight(boolean value) {
        if (light == value) return;
        light = value;
        synchronized (images) { images.clear(); }
    }

    /** Replace pending work instead of queuing every page crossed by a held DPAD key. */
    void prefetch(List<Pick> visible, List<Pick> next) {
        synchronized (images) {
            imageWindow = new ArrayList<>(visible);
            imageWindow.addAll(next);
            if (decoding) return;
            decoding = true;
        }
        io.execute(() -> {
            while (true) {
                Pick wanted = null;
                synchronized (images) {
                    for (Pick pick : imageWindow) {
                        if (!images.containsKey(pick.backdrop) && !imageBackoff(pick.backdrop)) {
                            wanted = pick;
                            break;
                        }
                    }
                    if (wanted == null) { decoding = false; return; }
                }
                loadImage(wanted);
            }
        });
    }

    Backdrop backdrop(Pick pick) {
        if (pick == null || pick.backdrop == null) return null;
        synchronized (images) { return images.get(pick.backdrop); }
    }

    private boolean hasImage(Pick pick) { return pick != null && imageFile(pick).isFile(); }
    File readyImage(Pick pick) { return hasImage(pick) ? imageFile(pick) : null; }

    List<Pick> showcase() {
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Pick pick : wanted()) if (hasImage(pick) && seen.add(key(pick))) out.add(pick);
        for (Pick pick : picks) if (hasImage(pick) && seen.add(key(pick))) out.add(pick);
        return out;
    }

    void prefetchAll() {
        if (prefetching) return;
        prefetching = true;
        List<Pick> snapshot = picks;
        final List<Pick> list = new ArrayList<>(snapshot.subList(0, Math.min(12, snapshot.size())));
        io.execute(() -> {
            try {
                for (Pick pick : list) {
                    synchronized (images) {
                        if (decoding) break; // A resumed home takes priority over screensaver warming.
                        if (imageBackoff(pick.backdrop)) continue;
                    }
                    try { download(pick); } catch (IOException ignored) { imageFailed(pick.backdrop); }
                }
            } finally {
                prefetching = false;
                notifyListeners();
            }
        });
    }

    private File imageFile(Pick pick) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pick.backdrop.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            return new File(new File(context.getCacheDir(), "channel-thumbnails"), name + ".jpg");
        } catch (Exception e) { throw new IllegalArgumentException("Invalid image key", e); }
    }

    private void download(Pick pick) throws IOException {
        if (!ChannelFeed.validThumbnail(pick.backdrop)) throw new IOException("Invalid thumbnail URL");
        File file = imageFile(pick);
        if (file.isFile()) return;
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException("Thumbnail cache unavailable");
        File part = new File(file.getPath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(pick.backdrop).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Thumbnail unavailable");
            if (connection.getContentLengthLong() > IMAGE_LIMIT) throw new IOException("Thumbnail exceeds limit");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > IMAGE_LIMIT) throw new IOException("Thumbnail exceeds limit");
                    out.write(buffer, 0, n);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(part.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4096 || bounds.outHeight > 4096
                    || (long) bounds.outWidth * bounds.outHeight > 10_000_000L) throw new IOException("Invalid thumbnail dimensions");
            if (!part.renameTo(file)) throw new IOException("Could not save thumbnail");
            prune(file.getParentFile());
        } finally {
            connection.disconnect();
            if (part.exists()) part.delete();
        }
    }

    private void loadImage(Pick pick) {
        File file = imageFile(pick);
        try {
            download(pick);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > Backdrop.W || bounds.outHeight / options.inSampleSize > Backdrop.H)
                options.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            if (bitmap == null) throw new IOException("Unreadable thumbnail");
            boolean forLight = light;
            Backdrop backdrop = Backdrop.make(bitmap, forLight);
            if (forLight != light) return;
            synchronized (images) {
                images.put(pick.backdrop, backdrop);
                imageFailures.remove(pick.backdrop);
                while (images.size() > 10) images.remove(images.keySet().iterator().next());
            }
            notifyListeners();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            file.delete();
            imageFailed(pick.backdrop);
        }
    }

    private boolean imageBackoff(String key) {
        Long at = imageFailures.get(key);
        long now = System.currentTimeMillis();
        return at != null && now >= at && now - at < 5 * 60_000L;
    }

    private void imageFailed(String key) {
        synchronized (images) {
            imageFailures.put(key, System.currentTimeMillis());
            while (imageFailures.size() > 24) imageFailures.remove(imageFailures.keySet().iterator().next());
        }
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles((directory, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= 24) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - 24; i++) files[i].delete();
    }

    private void notifyListeners() { main.post(() -> { for (Listener listener : listeners) listener.onRecs(); }); }
}
