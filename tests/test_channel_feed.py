import copy
import importlib.util
import http.client
import json
import pathlib
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch

SOURCE = pathlib.Path(__file__).resolve().parents[1] / "tools" / "channel_feed.py"
CHANNEL_ID = "UCTHZK1x5rW-fv_VYieYhYPQ"
FIRST = "aBcDeFgHiJ1"
SECOND = "bCdEfGhIjK2"


def payloads():
    channel = {"items": [{"id": CHANNEL_ID, "snippet": {"title": "ViktorKav"},
        "statistics": {"subscriberCount": "101000", "viewCount": "7640966", "videoCount": "480"},
        "contentDetails": {"relatedPlaylists": {"uploads": "UUexample"}}}]}
    playlist = {"items": [{"contentDetails": {"videoId": FIRST}}, {"contentDetails": {"videoId": SECOND}}]}
    video = {"id": FIRST, "snippet": {"channelId": CHANNEL_ID, "title": "Vídeo & IA",
        "publishedAt": "2026-10-18T15:00:00Z", "liveBroadcastContent": "none",
        "thumbnails": {"maxres": {"url": "https://i.ytimg.com/vi/" + FIRST + "/maxresdefault.jpg"}}},
        "statistics": {"viewCount": "12345", "likeCount": "678"}}
    live = copy.deepcopy(video)
    live.update(id=SECOND, liveStreamingDetails={"actualStartTime": "2026-10-19T15:00:00Z"})
    live["snippet"].update(title="Live recente", publishedAt="2026-10-19T15:00:00Z")
    live["snippet"]["thumbnails"]["maxres"]["url"] = "https://i.ytimg.com/vi/" + SECOND + "/maxresdefault.jpg"
    return channel, playlist, {"items": [video, live]}


class FeedTests(unittest.TestCase):
    def setUp(self):
        if not SOURCE.exists():
            self.fail("YouTube channel feed implementation is not available yet")
        spec = importlib.util.spec_from_file_location("tv_channel_feed", SOURCE)
        self.feed = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.feed)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = pathlib.Path(self.temp.name) / "cache.json"

    def normalized(self):
        return self.feed.normalize_payload(*payloads(), "2026-10-20T04:00:00Z")

    def test_normalizes_real_public_counts_and_sorts_latest_first(self):
        result = self.normalized()
        self.assertEqual(101000, result["channel"]["subscribers"])
        self.assertEqual(7640966, result["channel"]["totalViews"])
        self.assertEqual([SECOND, FIRST], [v["id"] for v in result["videos"]])
        self.assertEqual(["live", "video"], [v["kind"] for v in result["videos"]])
        self.assertEqual(12345, result["videos"][1]["views"])
        self.assertEqual("Vídeo & IA", result["videos"][1]["title"])

    def test_unavailable_counts_stay_null_and_unsafe_videos_are_omitted(self):
        channel, playlist, videos = payloads()
        channel["items"][0]["statistics"].pop("subscriberCount")
        videos["items"][0]["statistics"].pop("likeCount")
        videos["items"][1]["snippet"]["thumbnails"]["maxres"]["url"] = "http://127.0.0.1/private"
        result = self.feed.normalize_payload(channel, playlist, videos, "2026-10-20T04:00:00Z")
        self.assertIsNone(result["channel"]["subscribers"])
        self.assertEqual([FIRST], [v["id"] for v in result["videos"]])
        self.assertIsNone(result["videos"][0]["likes"])

    def test_foreign_channel_or_malformed_identity_is_rejected(self):
        channel, playlist, videos = payloads()
        channel["items"][0]["id"] = "UCnotVictor"
        with self.assertRaises(self.feed.FeedUnavailable):
            self.feed.normalize_payload(channel, playlist, videos, "2026-10-20T04:00:00Z")
        channel, playlist, videos = payloads()
        videos["items"][0]["id"] = "../../bad"
        result = self.feed.normalize_payload(channel, playlist, videos, "2026-10-20T04:00:00Z")
        self.assertEqual([SECOND], [v["id"] for v in result["videos"]])

    def test_cache_refreshes_only_after_ttl(self):
        now = [1000000.0]
        current = [self.normalized()]
        cache = self.feed.FeedCache(lambda: copy.deepcopy(current[0]), self.path, clock=lambda: now[0])
        self.assertEqual(101000, cache.get()["channel"]["subscribers"])
        current[0]["channel"]["subscribers"] = 102000
        now[0] += 899
        self.assertEqual(101000, cache.get()["channel"]["subscribers"])
        now[0] += 2
        self.assertEqual(102000, cache.get()["channel"]["subscribers"])

    def test_cache_survives_restart_and_marks_failed_refresh_stale(self):
        now = [1000000.0]
        first = self.feed.FeedCache(self.normalized, self.path, clock=lambda: now[0]).get()
        def unavailable():
            raise self.feed.FeedUnavailable("upstream unavailable")
        now[0] += 901
        restarted = self.feed.FeedCache(unavailable, self.path, clock=lambda: now[0]).get()
        self.assertTrue(restarted["stale"])
        self.assertEqual(first["updatedAt"], restarted["updatedAt"])
        self.assertEqual(first["channel"], restarted["channel"])

    def test_failure_without_cache_is_unavailable_not_zero_metrics(self):
        def unavailable():
            raise self.feed.FeedUnavailable("no source")
        cache = self.feed.FeedCache(unavailable, self.path)
        with self.assertRaises(self.feed.FeedUnavailable):
            cache.get()

    def test_malformed_upstream_keeps_existing_cache_stale(self):
        now = [1000000.0]
        broken = [False]
        def loader():
            channel, playlist, videos = payloads()
            if broken[0]:
                playlist["items"].append(None)
            return self.feed.normalize_payload(channel, playlist, videos, "2026-10-20T04:00:00Z")
        cache = self.feed.FeedCache(loader, self.path, clock=lambda: now[0])
        good = cache.get()
        broken[0] = True
        now[0] += 901
        stale = cache.get()
        self.assertTrue(stale["stale"])
        self.assertEqual(good["videos"], stale["videos"])

    def test_truncated_upstream_becomes_safe_unavailability(self):
        class TruncatedResponse:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, limit): raise http.client.IncompleteRead(b'{"items":')
        with patch("urllib.request.urlopen", return_value=TruncatedResponse()):
            with self.assertRaises(self.feed.FeedUnavailable) as error:
                self.feed.GoogleSource("fake-secret").request("channels", part="statistics")
        self.assertNotIn("fake-secret", str(error.exception))

    def test_http_endpoint_serves_public_feed_without_credentials(self):
        cache = self.feed.FeedCache(self.normalized, self.path)
        server = self.feed.FeedServer(("127.0.0.1", 0), cache)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        url = "http://127.0.0.1:" + str(server.server_port) + "/channel"
        with urllib.request.urlopen(url, timeout=3) as response:
            body = response.read().decode()
            self.assertEqual("application/json; charset=utf-8", response.headers["Content-Type"])
        self.assertEqual("ViktorKav", json.loads(body)["channel"]["title"])
        self.assertNotIn("GOOGLE_API_KEY", body)
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(url + "/not-found", timeout=3)
        self.assertEqual(404, error.exception.code)
        error.exception.close()


if __name__ == "__main__":
    unittest.main()
