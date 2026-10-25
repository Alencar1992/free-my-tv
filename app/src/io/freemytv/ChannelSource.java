package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One cached LAN feed for both channel metrics and video cards, with all I/O on its executor. */
final class ChannelSource {
    interface Listener { void onChannel(); }
    private static final long TTL = 15 * 60_000L;
    private static final int BODY_LIMIT = 1024 * 1024;
    private static ChannelSource instance;

    static synchronized ChannelSource get(Context context) {
        if (instance == null) instance = new ChannelSource(context.getApplicationContext());
        return instance;
    }

    private final AtomicFile disk, status;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile ChannelFeed current = ChannelFeed.empty();
    private volatile String error = "";
    private volatile boolean loading = true, fetching;
    private volatile long fetchedAt, retryAt;
    private int failures;
    private String savedJson;

    private ChannelSource(Context context) {
        disk = new AtomicFile(new File(context.getFilesDir(), "channel-feed.json"));
        status = new AtomicFile(new File(context.getFilesDir(), "channel-status.json"));
        io.execute(() -> {
            try (InputStream in = disk.openRead()) {
                savedJson = read(in, BODY_LIMIT);
                ChannelFeed saved = ChannelFeed.parse(savedJson);
                current = expired(saved.updatedAt) ? saved.withMetricsStale() : saved;
                fetchedAt = saved.updatedAt;
            } catch (Exception ignored) {
                savedJson = null;
            } finally {
                loading = false;
                writeStatus();
                notifyListeners();
                refreshIfStale();
            }
        });
    }

    ChannelFeed feed() { return current; }
    String error() { return error; }
    void addListener(Listener listener) { listeners.addIfAbsent(listener); }
    void removeListener(Listener listener) { listeners.remove(listener); }

    synchronized void refreshIfStale() {
        if (loading || fetching) return;
        long now = System.currentTimeMillis();
        if (now < retryAt && retryAt - now <= 5 * 60_000L) return;
        if (!current.stale && !expired(current.updatedAt) && !expired(fetchedAt)
                && !current.recommendationsNeedRefresh(now, fetchedAt)) return;
        fetching = true;
        io.execute(this::refresh);
    }

    private void refresh() {
        HttpURLConnection connection = null;
        try {
            String endpoint = Config.CHANNEL_FEED_URL.trim();
            if (endpoint.isEmpty()) throw new IOException("unconfigured");
            URL url = new URL(endpoint);
            if ((!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
                    || url.getUserInfo() != null || url.getRef() != null) throw new IOException("endpoint");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) throw new IOException("http");
            if (connection.getContentLengthLong() > BODY_LIMIT) throw new IOException("size");
            String body;
            try (InputStream in = connection.getInputStream()) { body = read(in, BODY_LIMIT); }
            ChannelFeed parsed = ChannelFeed.parse(body);
            if (expired(parsed.updatedAt)) parsed = parsed.withMetricsStale();
            current = parsed;
            fetchedAt = System.currentTimeMillis();
            savedJson = body;
            write(disk, parsed.stale ? staleJson(body, false) : body);
            if (parsed.stale) {
                error = Ui.tr("Servidor com dados desatualizados", "Server data is out of date");
                scheduleRetry();
            } else {
                error = ""; failures = 0; retryAt = 0;
            }
        } catch (Exception e) {
            current = current.withStale();
            error = Config.CHANNEL_FEED_URL.trim().isEmpty()
                    ? Ui.tr("Fonte do canal não configurada", "Channel feed is not configured")
                    : e instanceof IllegalArgumentException
                    ? Ui.tr("Feed do canal inválido", "Invalid channel feed")
                    : Ui.tr("Não foi possível atualizar o canal", "Could not refresh the channel");
            scheduleRetry();
            if (savedJson != null) {
                try { write(disk, staleJson(savedJson, true)); } catch (Exception ignored) { }
            }
        } finally {
            if (connection != null) connection.disconnect();
            writeStatus();
            fetching = false;
            notifyListeners();
        }
    }

    private void scheduleRetry() {
        failures = Math.min(failures + 1, 4);
        retryAt = System.currentTimeMillis() + Math.min(5 * 60_000L, 30_000L << (failures - 1));
    }

    private void writeStatus() {
        try {
            write(status, new JSONObject().put("count", current.videos.size()).put("error", error)
                    .put("at", current.updatedAt).put("checkedAt", System.currentTimeMillis())
                    .put("stale", current.stale).put("recommendationsCount", current.recommendations.size())
                    .put("recommendationsAt", current.recommendationsUpdatedAt)
                    .put("recommendationsStale", current.recommendationsStale)
                    .put("recommendationsStatus", current.recommendationsStatus).toString());
        } catch (Exception ignored) { }
    }

    private void notifyListeners() { main.post(() -> { for (Listener listener : listeners) listener.onChannel(); }); }

    private static boolean expired(long at) {
        long now = System.currentTimeMillis();
        return at <= 0 || now < at || now - at >= TTL;
    }

    private static String staleJson(String body, boolean recommendationsStale) throws Exception {
        JSONObject root = new JSONObject(body).put("stale", true);
        JSONObject recommendations = root.optJSONObject("recommendations");
        if (recommendationsStale && recommendations != null) recommendations.put("stale", true);
        return root.toString();
    }

    private static void write(AtomicFile file, String body) throws IOException {
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(body.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) file.failWrite(out);
            throw e;
        }
    }

    static String read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > limit) throw new IOException("Response exceeds limit");
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
