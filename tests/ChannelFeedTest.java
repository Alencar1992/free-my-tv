package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

/** Plain JVM assertions exercise the same org.json API provided by Android. */
public final class ChannelFeedTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        nineRecommendationsAreDistinctAndVerified();
        legacyFormatMetadataTriggersPromptRefresh();
        tabFilteringPrecedesChannelDiversification();
        subscriptionsDoNotReplaceOwnMetrics();
        recommendationsAreSortedDeduplicatedAndBounded();
        recommendationsValidateIdentityAndThumbnails();
        recommendationStatesDoNotDamageMetrics();
        malformedRecommendationsStayIsolated();
        staleDomainsStayIndependent();
        metricExpirationLeavesRecommendationsFresh();
        pendingSubscriptionsPollBeforeMetricTtl();
        sourceRevisionSurvivesFreshnessChanges();
        missingCountsStayUnknown();
        invalidSchemaIsRejected();
        invalidVideosAreDiscarded();
        newestVideoWinsOverLive();
        videosAreSortedDeduplicatedAndBounded();
        displayFormatsKeepMeaningAndLocale();
        emptyAndStaleKeepTheirMeaning();
        System.out.println("ChannelFeedTest: " + checks + " assertions passed");
    }

    private static void nineRecommendationsAreDistinctAndVerified() throws Exception {
        JSONArray rows = new JSONArray();
        rows.put(recommendation("short000001", "2026-10-24T15:00:00Z", "video").put("isShort", true));
        JSONObject unknown = recommendation("maybe000001", "2026-10-24T14:00:00Z", "video");
        unknown.remove("isShort"); rows.put(unknown);
        for (int i = 0; i < 12; i++) {
            String channel = "UC" + String.format("%022d", i);
            rows.put(recommendation(String.format("new%08d", i), "2026-10-24T12:00:00Z", "video").put("channelId", channel));
            rows.put(recommendation(String.format("old%08d", i), "2026-10-23T12:00:00Z", "video").put("channelId", channel));
        }
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(rows)).toString());
        java.util.List<ChannelFeed.Video> selected = f.selectedRecommendations(null, 9);
        eq(9, selected.size());
        java.util.Set<String> channels = new java.util.HashSet<>();
        for (ChannelFeed.Video v : selected) { check(v.id.startsWith("new"), "latest verified long video wins"); channels.add(v.channelId); }
        eq(9, channels.size());
        eq(9, f.selectedRecommendations("video", 9).size());
        eq(0, f.selectedRecommendations("live", 9).size());
        eq(0, f.selectedRecommendations(null, 0).size());
    }

    private static void tabFilteringPrecedesChannelDiversification() throws Exception {
        JSONObject live = recommendation("live0000001", "2026-10-24T15:00:00Z", "live").put("durationSeconds", 0);
        JSONObject regular = recommendation("video000001", "2026-10-24T14:00:00Z", "video");
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray().put(live).put(regular))).toString());
        eq(1, f.selectedRecommendations(null, 9).size());
        eq("live0000001", f.selectedRecommendations(null, 9).get(0).id);
        eq("video000001", f.selectedRecommendations("video", 9).get(0).id);
        eq("live0000001", f.selectedRecommendations("live", 9).get(0).id);
    }

    private static void legacyFormatMetadataTriggersPromptRefresh() throws Exception {
        JSONObject old = recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video");
        old.remove("isShort"); old.remove("durationSeconds");
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray().put(old))).toString());
        long now = 1792497600000L;
        eq(true, f.recommendationsNeedRefresh(now, now - 16_000));
        eq(false, f.recommendationsNeedRefresh(now, now - 5_000));
        f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray()).put("selectionVersion", 1)).toString());
        eq(true, f.recommendationsNeedRefresh(now, now - 16_000));
    }

    // Moving recommendation videos into the own-channel list would corrupt the metrics.
    private static void subscriptionsDoNotReplaceOwnMetrics() throws Exception {
        JSONObject thirdParty = recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("views", 999999).put("likes", 9999);
        JSONObject root = fixture().put("recommendations", subscriptions(new JSONArray().put(thirdParty)));
        ChannelFeed f = ChannelFeed.parse(root.toString());
        eq(1, f.recommendations.size());
        ChannelFeed.Video rec = f.recommendations.get(0);
        eq("THIRDabcdef", rec.id); eq("UCabcdefghijklmnopqrstuv", rec.channelId);
        eq("Outro canal", rec.channelTitle);
        eq("abcdefghijk", f.latest().id); eq("1,234", f.metricValue(2, false, true)); eq("87", f.metricValue(3, false, true));
        eq(1, f.videos.size()); eq("ready", f.recommendationsStatus);
        eq(1792494000000L, f.recommendationsUpdatedAt); eq(false, f.recommendationsStale);
    }

    // A duplicated or unsorted backend result must not repeat cards or overfill the cache.
    private static void recommendationsAreSortedDeduplicatedAndBounded() throws Exception {
        JSONArray list = new JSONArray();
        for (int i = 1; i <= 30; i++) list.put(recommendation(String.format("other%06d", i), String.format("2026-09-%02dT12:00:00Z", i), "video"));
        list.put(recommendation("other000030", "2026-09-30T12:00:00Z", "video"));
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(list)).toString());
        eq(30, f.recommendations.size()); eq("other000030", f.recommendations.get(0).id); eq("other000001", f.recommendations.get(29).id);
        for (int i = 31; i <= 530; i++) list.put(recommendation(String.format("other%06d", i), "2026-10-01T12:00:00Z", "video"));
        eq(512, ChannelFeed.parse(fixture().put("recommendations", subscriptions(list)).toString()).recommendations.size());
        try { f.recommendations.clear(); throw new AssertionError("mutable subscriptions videos"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }

    // Accepting an arbitrary intent ID or remote image URL would make cards unsafe.
    private static void recommendationsValidateIdentityAndThumbnails() throws Exception {
        JSONArray list = new JSONArray();
        for (String id : new String[] {"UUabcdefghijklmnopqrstuv", "UCshort", "UCabcdefghijklmnopqrstuvw", "UCabcdefghijklmnopqrstu/", "UCabcdefghijklmnopqrstué"})
            list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("channelId", id));
        for (String url : new String[] {"http://i.ytimg.com/x", "https://i.ytimg.com.evil.test/x", "https://i.ytimg.com@evil.test/x", "file:///tmp/x", "https://i.ytimg.com:444/x", "https://i.ytimg.com/x#fragment"})
            list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("thumbnail", url));
        list.put(recommendation("../unsafe!!", "2026-10-20T11:00:00Z", "video"));
        list.put(recommendation("THIRDabcdef", "garbage", "video"));
        list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("channelTitle", " "));
        list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("channelTitle", "bad\nname"));
        list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("channelTitle", repeat('x', 201)));
        list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video").put("title", repeat('x', 501)));
        list.put(JSONObject.NULL).put("bad entry");
        list.put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "live"));
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(list)).toString());
        eq(1, f.recommendations.size()); eq("live", f.recommendations.get(0).kind);
    }

    // Old cached own videos and authorization states must never become subscription cards.
    private static void recommendationStatesDoNotDamageMetrics() throws Exception {
        ChannelFeed legacy = ChannelFeed.parse(fixture().toString());
        eq(0, legacy.recommendations.size()); eq("authorization_required", legacy.recommendationsStatus);
        eq(0L, legacy.recommendationsUpdatedAt); eq("abcdefghijk", legacy.latest().id);
        eq("loading", ChannelFeed.empty().recommendationsStatus);
        for (String state : new String[] {"loading", "authorization_required", "unavailable", "ready"}) {
            JSONObject recs = subscriptions(new JSONArray()).put("status", state).put("updatedAt", JSONObject.NULL).put("stale", true);
            ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", recs).toString());
            eq(state, f.recommendationsStatus); eq(0, f.recommendations.size()); eq(0L, f.recommendationsUpdatedAt);
            eq("1,234", f.metricValue(2, false, true)); check(!f.stale, "subscription state must not age own metrics");
        }
        JSONObject unauthorized = subscriptions(new JSONArray().put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video")))
                .put("status", "authorization_required");
        eq(0, ChannelFeed.parse(fixture().put("recommendations", unauthorized).toString()).recommendations.size());
    }

    // A malformed optional extension must leave the established metric payload usable.
    private static void malformedRecommendationsStayIsolated() throws Exception {
        JSONObject valid = subscriptions(new JSONArray().put(recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video")));
        for (Object bad : new Object[] {JSONObject.NULL, "bad", new JSONArray(), new JSONObject(),
                new JSONObject(valid.toString()).put("source", "popular"),
                new JSONObject(valid.toString()).put("status", "unknown"),
                new JSONObject(valid.toString()).put("stale", "false"),
                new JSONObject(valid.toString()).put("updatedAt", "2026-10-20T11:00:00+01:00"),
                new JSONObject(valid.toString()).put("subscriptionCount", -1),
                new JSONObject(valid.toString()).put("subscriptionCount", "17"),
                new JSONObject(valid.toString()).put("videos", "bad")}) {
            ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", bad).toString());
            eq("unavailable", f.recommendationsStatus); eq(0, f.recommendations.size());
            eq(125400L, f.subscribers); eq("abcdefghijk", f.latest().id);
        }
    }

    // Metric expiration must not pretend the independent subscription snapshot failed.
    private static void staleDomainsStayIndependent() throws Exception {
        JSONObject root = fixture().put("stale", true).put("recommendations", subscriptions(new JSONArray()));
        ChannelFeed f = ChannelFeed.parse(root.toString());
        check(f.stale, "own stale flag"); check(!f.recommendationsStale, "subscriptions retain own freshness");
        ChannelFeed failed = f.withStale(); check(failed.recommendationsStale, "network failure ages subscriptions even if metrics already stale");
        eq(f.recommendationsUpdatedAt, failed.recommendationsUpdatedAt);
        f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray()).put("stale", true)).toString());
        check(!f.stale, "old subscriptions do not age own metrics"); check(f.recommendationsStale, "subscription stale flag");
    }

    private static void metricExpirationLeavesRecommendationsFresh() throws Exception {
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray().put(
                recommendation("THIRDabcdef", "2026-10-20T11:00:00Z", "video")))).toString());
        ChannelFeed expired = f.withMetricsStale();
        check(expired.stale, "own metrics expire"); check(!expired.recommendationsStale, "subscription cadence is independent");
        eq("THIRDabcdef", expired.recommendations.get(0).id); eq(1792494000000L, expired.recommendationsUpdatedAt);
        ChannelFeed failed = expired.withStale();
        check(failed.recommendationsStale, "network failure ages both domains");
        eq("THIRDabcdef", failed.recommendations.get(0).id); eq("ready", failed.recommendationsStatus);
    }

    // Fresh metrics must not hide a subscription background job or new authorization for 15 minutes.
    private static void pendingSubscriptionsPollBeforeMetricTtl() throws Exception {
        long checkedAt = 1792497600000L;
        for (String state : new String[] {"loading", "authorization_required", "unavailable", "ready"}) {
            ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations",
                    subscriptions(new JSONArray()).put("status", state)).toString());
            long interval = state.equals("loading") ? 15000L : state.equals("ready") ? 900000L : 60000L;
            check(!f.recommendationsNeedRefresh(checkedAt, checkedAt), "do not hammer a successful response");
            check(!f.recommendationsNeedRefresh(checkedAt + interval - 1, checkedAt), "wait for the status interval");
            check(f.recommendationsNeedRefresh(checkedAt + interval, checkedAt), "poll " + state + " at its deadline");
            check(f.recommendationsNeedRefresh(checkedAt, 0), "never checked is due");
            check(f.recommendationsNeedRefresh(checkedAt - 1, checkedAt), "clock rollback cannot freeze polling");
            check(!f.stale, "polling must not age metric data");
            check(!f.recommendationsStale, "polling must not age adaptive subscription data");
        }
        ChannelFeed ready = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray())
                .put("updatedAt", "2026-10-19T11:00:00Z").put("stale", true)).toString());
        check(!ready.recommendationsNeedRefresh(checkedAt + 60000, checkedAt), "ready snapshot follows normal polling even with an old adaptive snapshot");
    }

    private static void sourceRevisionSurvivesFreshnessChanges() throws Exception {
        String revision = repeat('a', 64);
        ChannelFeed f = ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray()).put("sourceRevision", revision)).toString());
        eq(revision, f.recommendationsSource);
        eq(revision, f.withStale().recommendationsSource);
        eq(revision, f.withMetricsStale().recommendationsSource);
        eq("", ChannelFeed.parse(fixture().put("recommendations", subscriptions(new JSONArray()).put("sourceRevision", "invalid")).toString()).recommendationsSource);
    }

    private static String repeat(char c, int n) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < n; i++) result.append(c);
        return result.toString();
    }

    private static JSONObject subscriptions(JSONArray videos) throws Exception {
        return new JSONObject().put("source", "subscriptions").put("status", "ready")
                .put("updatedAt", "2026-10-20T11:00:00Z").put("stale", false).put("selectionVersion", 2).put("subscriptionCount", 17).put("videos", videos);
    }

    private static JSONObject recommendation(String id, String date, String kind) throws Exception {
        return video(id, date, kind).put("channelId", "UCabcdefghijklmnopqrstuv").put("channelTitle", "Outro canal").put("isShort", false).put("durationSeconds", 600);
    }

    private static void missingCountsStayUnknown() throws Exception {
        JSONObject root = fixture();
        root.getJSONObject("channel").put("subscribers", JSONObject.NULL).remove("totalViews");
        root.getJSONArray("videos").getJSONObject(0).put("likes", JSONObject.NULL).remove("views");
        ChannelFeed f = ChannelFeed.parse(root.toString());
        eq(-1L, f.subscribers); eq(-1L, f.totalViews);
        eq(-1L, f.latest().likes); eq("—", f.metricValue(0, true, false));
        eq("—", f.metricValue(2, false, true));
        eq(1792497600000L, f.updatedAt);
        root.getJSONObject("channel").put("subscribers", 0);
        eq("≈ 0", ChannelFeed.parse(root.toString()).metricValue(0, false, true));
    }

    private static void invalidSchemaIsRejected() throws Exception {
        rejects("{}"); rejects("[]"); rejects("not-json");
        JSONObject root = fixture(); root.put("stale", "false"); rejects(root.toString());
        root = fixture(); root.put("updatedAt", "2026-02-30T12:00:00Z"); rejects(root.toString());
        root = fixture(); root.remove("videos"); rejects(root.toString());
        root = fixture(); root.getJSONObject("channel").put("id", "UC1234567890123456789012"); rejects(root.toString());
        root = fixture(); root.getJSONObject("channel").put("title", "  "); rejects(root.toString());
        for (Object bad : new Object[] {"42", -2, 1.25, true}) {
            root = fixture(); root.getJSONObject("channel").put("subscribers", bad); rejects(root.toString());
        }
    }

    private static void invalidVideosAreDiscarded() throws Exception {
        JSONArray videos = new JSONArray();
        for (String url : new String[] {"http://i.ytimg.com/vi/a/default.jpg", "https://ytimg.com.evil.test/x", "https://i.ytimg.com@evil.test/x", "file:///tmp/x", "https://i.ytimg.com:444/x"}) {
            videos.put(video("abcdefghijk", "2026-10-20T10:00:00Z", "video").put("thumbnail", url));
        }
        videos.put(video("../unsafe!!", "2026-10-20T10:00:00Z", "video"));
        videos.put(video("abcdefghijk", "garbage", "video"));
        videos.put(video("abcdefghijk", "2026-10-20T10:00:00Z", "short"));
        videos.put(video("abcdefghijk", "2026-10-20T10:00:00Z", "video").put("title", JSONObject.NULL));
        videos.put(video("ABCDEFGHIJK", "2026-10-20T10:00:00Z", "video"));
        eq(1, ChannelFeed.parse(fixture().put("videos", videos).toString()).videos.size());
    }

    private static void newestVideoWinsOverLive() throws Exception {
        JSONArray list = new JSONArray().put(video("LIVEabcdefg", "2026-10-20T11:00:00Z", "live"))
                .put(video("VIDEabcdefg", "2026-10-19T11:00:00Z", "video"));
        eq("VIDEabcdefg", ChannelFeed.parse(fixture().put("videos", list).toString()).latest().id);
        list.remove(1);
        ChannelFeed onlyLive = ChannelFeed.parse(fixture().put("videos", list).toString());
        eq("LIVEabcdefg", onlyLive.latest().id);
        check(onlyLive.metricLabel(2, false).contains("LIVE"), "fallback live is labeled accurately");
        check(onlyLive.metricLabel(3, true).contains("STREAM"), "fallback stream is labeled accurately");
    }

    private static void videosAreSortedDeduplicatedAndBounded() throws Exception {
        JSONArray list = new JSONArray();
        for (int i = 1; i <= 30; i++) list.put(video(String.format("video%06d", i), String.format("2026-09-%02dT12:00:00Z", i), "video"));
        list.put(video("video000030", "2026-09-30T12:00:00Z", "video"));
        ChannelFeed f = ChannelFeed.parse(fixture().put("videos", list).toString());
        eq(24, f.videos.size()); eq("video000030", f.videos.get(0).id); eq("video000007", f.videos.get(23).id);
        try { f.videos.clear(); throw new AssertionError("mutable feed videos"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void displayFormatsKeepMeaningAndLocale() throws Exception {
        ChannelFeed f = ChannelFeed.parse(fixture().toString());
        eq("≈ 125,4 mil", f.metricValue(0, true, false));
        eq("≈ 125.4K", f.metricValue(0, true, true));
        eq("12.345.678", f.metricValue(1, false, false));
        eq("12,345,678", f.metricValue(1, false, true));
        eq("1,2 mil", f.metricValue(2, true, false));
        eq("87", f.metricValue(3, false, true));
        eq("—", f.metricValue(9, false, true));
        check(f.metricLabel(2, false).contains("VÍDEO"), "latest views label explains period");
        check(f.metricLabel(3, true).contains("VIDEO"), "latest likes label explains period");
    }

    private static void emptyAndStaleKeepTheirMeaning() throws Exception {
        ChannelFeed empty = ChannelFeed.empty();
        eq(null, empty.latest()); eq(0L, empty.updatedAt); eq("—", empty.metricValue(1, false, false));
        ChannelFeed old = ChannelFeed.parse(fixture().toString()).withStale();
        check(old.stale, "stale flag survives failed refresh"); eq(125400L, old.subscribers);
        eq(1792497600000L, old.updatedAt);
    }

    private static JSONObject fixture() throws Exception {
        return new JSONObject().put("updatedAt", "2026-10-20T12:00:00Z").put("stale", false)
                .put("channel", new JSONObject().put("id", "UCTHZK1x5rW-fv_VYieYhYPQ").put("title", "ViktorKav")
                        .put("subscribers", 125400).put("totalViews", 12345678).put("videoCount", 500))
                .put("videos", new JSONArray().put(video("abcdefghijk", "2026-10-19T10:00:00Z", "video")));
    }

    private static JSONObject video(String id, String date, String kind) throws Exception {
        return new JSONObject().put("id", id).put("title", "Vídeo útil").put("thumbnail", "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg")
                .put("publishedAt", date).put("views", 1234).put("likes", 87).put("kind", kind);
    }

    private static void rejects(String json) throws Exception {
        try { ChannelFeed.parse(json); throw new AssertionError("accepted invalid schema: " + json); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void eq(Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
