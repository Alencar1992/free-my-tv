package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validated public channel data. Missing counters remain unknown, never fabricated zeroes. */
final class ChannelFeed {
    static final String CHANNEL_ID = "UCTHZK1x5rW-fv_VYieYhYPQ";
    private static final String[] LABEL_PT = {"INSCRITOS", "VIEWS DO CANAL", "VIEWS · ÚLTIMO VÍDEO", "LIKES · ÚLTIMO VÍDEO"};
    private static final String[] LABEL_EN = {"SUBSCRIBERS", "CHANNEL VIEWS", "VIEWS · LATEST VIDEO", "LIKES · LATEST VIDEO"};
    final String channelId, channelTitle;
    final long subscribers, totalViews, videoCount, updatedAt;
    final boolean stale;
    final List<Video> videos, recommendations;
    final long recommendationsUpdatedAt;
    final boolean recommendationsStale;
    final String recommendationsStatus, recommendationsSource;
    final int recommendationsVersion;

    static final class Video {
        final String id, title, thumbnail, kind, channelId, channelTitle;
        final long publishedAt, views, likes, durationSeconds;
        final boolean nonShortVerified;

        private Video(JSONObject o, boolean subscription) throws Exception {
            id = text(o, "id", 11);
            if (!validVideoId(id)) throw new IllegalArgumentException("Invalid video ID");
            title = text(o, "title", 500);
            thumbnail = text(o, "thumbnail", 2048);
            if (!validThumbnail(thumbnail)) throw new IllegalArgumentException("Invalid thumbnail URL");
            kind = text(o, "kind", 5);
            if (!kind.equals("video") && !kind.equals("live")) throw new IllegalArgumentException("Invalid video kind");
            publishedAt = timestamp(o, "publishedAt");
            channelId = subscription ? text(o, "channelId", 24) : CHANNEL_ID;
            if (!channelId.matches("UC[A-Za-z0-9_-]{22}")) throw new IllegalArgumentException("Invalid channel ID");
            channelTitle = subscription ? text(o, "channelTitle", 200) : "ViktorKav";
            views = count(o, "views");
            likes = count(o, "likes");
            durationSeconds = count(o, "durationSeconds");
            nonShortVerified = Boolean.FALSE.equals(o.opt("isShort"))
                    && (kind.equals("live") || durationSeconds > 0);
        }
    }

    private ChannelFeed(String id, String title, long subs, long views, long count, long at, boolean old, List<Video> items, List<Video> recs, long recAt, boolean recOld, String recStatus, int recVersion, String recSource) {
        channelId = id; channelTitle = title; subscribers = subs; totalViews = views;
        videoCount = count; updatedAt = at; stale = old;
        videos = Collections.unmodifiableList(new ArrayList<>(items));
        recommendations = Collections.unmodifiableList(new ArrayList<>(recs));
        recommendationsUpdatedAt = recAt; recommendationsStale = recOld; recommendationsStatus = recStatus; recommendationsVersion = recVersion; recommendationsSource = recSource;
    }

    static ChannelFeed empty() {
        return new ChannelFeed(CHANNEL_ID, "ViktorKav", -1, -1, -1, 0, true, Collections.emptyList(), Collections.emptyList(), 0, true, "loading", 0, "");
    }

    ChannelFeed withStale() {
        return stale && recommendationsStale ? this : new ChannelFeed(channelId, channelTitle, subscribers, totalViews, videoCount, updatedAt, true,
                videos, recommendations, recommendationsUpdatedAt, true, recommendationsStatus, recommendationsVersion, recommendationsSource);
    }

    ChannelFeed withMetricsStale() {
        return stale ? this : new ChannelFeed(channelId, channelTitle, subscribers, totalViews, videoCount, updatedAt, true,
                videos, recommendations, recommendationsUpdatedAt, recommendationsStale, recommendationsStatus, recommendationsVersion, recommendationsSource);
    }

    boolean recommendationsNeedRefresh(long now, long checkedAt) {
        boolean needsFormat = recommendationsVersion < 2;
        for (Video video : recommendations) needsFormat |= !video.nonShortVerified;
        long interval = recommendationsStatus.equals("loading") || needsFormat ? 15_000L
                : recommendationsStatus.equals("ready") ? 15 * 60_000L : 60_000L;
        return checkedAt <= 0 || now < checkedAt || now - checkedAt >= interval;
    }

    static ChannelFeed parse(String json) {
        try {
            if (json == null || json.length() > 1024 * 1024) throw new IllegalArgumentException("Invalid feed body");
            JSONObject root = new JSONObject(json);
            JSONObject channel = root.getJSONObject("channel");
            String id = text(channel, "id", 24);
            if (!CHANNEL_ID.equals(id)) throw new IllegalArgumentException("Unexpected channel");
            String title = text(channel, "title", 200);
            long at = timestamp(root, "updatedAt");
            Object old = root.get("stale");
            if (!(old instanceof Boolean)) throw new IllegalArgumentException("Invalid stale flag");
            long subs = count(channel, "subscribers"), views = count(channel, "totalViews"), total = count(channel, "videoCount");
            List<Video> unique = videos(root.getJSONArray("videos"), false);
            List<Video> recommendations = Collections.emptyList();
            long recAt = 0;
            boolean recOld = true;
            String recStatus = "authorization_required";
            int recVersion = 2;
            String recSource = "";
            if (root.has("recommendations")) {
                try {
                    JSONObject recs = root.getJSONObject("recommendations");
                    if (!"subscriptions".equals(text(recs, "source", 30))) throw new IllegalArgumentException("Invalid recommendation source");
                    String status = text(recs, "status", 30);
                    if (!status.equals("ready") && !status.equals("loading") && !status.equals("authorization_required") && !status.equals("unavailable"))
                        throw new IllegalArgumentException("Invalid recommendation status");
                    Object stale = recs.get("stale");
                    if (!(stale instanceof Boolean)) throw new IllegalArgumentException("Invalid recommendation stale flag");
                    long subscriptionCount = count(recs, "subscriptionCount");
                    if (subscriptionCount < 0 || subscriptionCount > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid subscription count");
                    long updated = recs.isNull("updatedAt") && recs.has("updatedAt") ? 0 : timestamp(recs, "updatedAt");
                    List<Video> items = videos(recs.getJSONArray("videos"), true);
                    recommendations = status.equals("authorization_required") ? Collections.emptyList() : items;
                    recAt = updated; recOld = (Boolean) stale; recStatus = status;
                    recVersion = recs.optInt("selectionVersion", 0);
                    recSource = recs.optString("sourceRevision", "");
                    if (!recSource.matches("[a-f0-9]{64}")) recSource = "";
                } catch (Exception ignored) {
                    recStatus = "unavailable";
                }
            }
            return new ChannelFeed(id, title, subs, views, total, at, (Boolean) old, unique,
                    recommendations, recAt, recOld, recStatus, recVersion, recSource);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid channel feed", e);
        }
    }

    private static List<Video> videos(JSONArray list, boolean subscription) {
        List<Video> videos = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (entry == null) continue;
            try { videos.add(new Video(entry, subscription)); } catch (Exception ignored) { }
        }
        videos.sort((a, b) -> Long.compare(b.publishedAt, a.publishedAt));
        Set<String> ids = new HashSet<>();
        List<Video> unique = new ArrayList<>();
        for (Video video : videos) if (ids.add(video.id)) {
            unique.add(video);
            if (unique.size() == (subscription ? 512 : 24)) break;
        }
        return unique;
    }

    List<Video> selectedRecommendations(String kind, int limit) {
        List<Video> selected = new ArrayList<>();
        Set<String> channels = new HashSet<>();
        if (limit <= 0) return selected;
        for (Video video : recommendations) {
            if (!video.nonShortVerified || (kind != null && !kind.equals(video.kind)) || !channels.add(video.channelId)) continue;
            selected.add(video);
            if (selected.size() == limit) break;
        }
        return selected;
    }

    Video latest() {
        for (Video video : videos) if (video.kind.equals("video")) return video;
        return videos.isEmpty() ? null : videos.get(0);
    }

    String metricLabel(int index, boolean english) {
        if (index < 0 || index > 3) return "";
        Video latest = latest();
        if (index >= 2 && latest != null && latest.kind.equals("live"))
            return (index == 2 ? "VIEWS" : "LIKES") + (english ? " · LATEST STREAM" : " · ÚLTIMA LIVE");
        return (english ? LABEL_EN : LABEL_PT)[index];
    }

    String metricValue(int index, boolean compact, boolean english) {
        Video latest = latest();
        long value = index == 0 ? subscribers : index == 1 ? totalViews
                : index == 2 && latest != null ? latest.views : index == 3 && latest != null ? latest.likes : -1;
        if (value < 0) return "—";
        Locale locale = english ? Locale.US : new Locale("pt", "BR");
        String text;
        if (compact && value >= 1000) {
            double divisor = value >= 1_000_000_000 ? 1_000_000_000.0 : value >= 1_000_000 ? 1_000_000.0 : 1000.0;
            String suffix = value >= 1_000_000_000 ? (english ? "B" : " bi") : value >= 1_000_000 ? (english ? "M" : " mi") : (english ? "K" : " mil");
            NumberFormat format = NumberFormat.getNumberInstance(locale);
            format.setGroupingUsed(false); format.setMaximumFractionDigits(1);
            text = format.format(value / divisor) + suffix;
        } else text = NumberFormat.getIntegerInstance(locale).format(value);
        return (index == 0 ? "≈ " : "") + text;
    }

    static boolean validVideoId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{11}"); }

    static boolean validThumbnail(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && (host.equals("ytimg.com") || host.endsWith(".ytimg.com"))
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawFragment() == null && uri.getRawPath() != null && !uri.getRawPath().isEmpty();
        } catch (Exception e) { return false; }
    }

    private static String text(JSONObject object, String key, int max) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("Invalid " + key);
        String text = ((String) value).trim();
        if (text.isEmpty() || text.length() > max || text.matches("(?s).*[\\p{Cntrl}].*")) throw new IllegalArgumentException("Invalid " + key);
        return text;
    }

    private static long timestamp(JSONObject object, String key) throws Exception {
        String value = text(object, key, 40);
        if (!value.endsWith("Z")) throw new IllegalArgumentException("Timestamp must be UTC");
        long at = Instant.parse(value).toEpochMilli();
        if (at <= 0) throw new IllegalArgumentException("Invalid timestamp");
        return at;
    }

    private static long count(JSONObject object, String key) throws Exception {
        if (!object.has(key) || object.isNull(key)) return -1;
        Object value = object.get(key);
        if (!(value instanceof Number) || !value.toString().matches("[0-9]+")) throw new IllegalArgumentException("Invalid count");
        return Long.parseLong(value.toString());
    }
}
