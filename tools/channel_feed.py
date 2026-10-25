#!/usr/bin/env python3
"""Read-only public YouTube data for the personal TV; Google credentials stay here."""
import argparse
import copy
import datetime
import http.client
import json
import os
import pathlib
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from subscription_feed import SubscriptionCache, SubscriptionSource, empty_feed
except ModuleNotFoundError:
    from tools.subscription_feed import SubscriptionCache, SubscriptionSource, empty_feed

CHANNEL_ID = "UCTHZK1x5rW-fv_VYieYhYPQ"
API = "https://www.googleapis.com/youtube/v3/"
MAX_BODY = 1024 * 1024
MAX_VIDEOS = 24
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


class FeedUnavailable(Exception):
    pass


def records(response):
    if not isinstance(response, dict) or not isinstance(response.get("items"), list):
        raise FeedUnavailable("Invalid YouTube collection")
    if not all(isinstance(item, dict) for item in response["items"]):
        raise FeedUnavailable("Invalid YouTube item")
    return response["items"]


def object_field(parent, name):
    value = parent.get(name, {})
    if not isinstance(value, dict):
        raise FeedUnavailable("Invalid YouTube object")
    return value


def count(value):
    if isinstance(value, bool) or value is None:
        return None
    text = str(value)
    if not re.fullmatch(r"\d+", text):
        return None
    number = int(text)
    return number if number <= 9223372036854775807 else None


def date_valid(value):
    try:
        parsed = datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.tzinfo is not None
    except (TypeError, ValueError, AttributeError):
        return False


def thumbnail(snippet):
    for kind in ("maxres", "standard", "high", "medium", "default"):
        value = object_field(object_field(snippet, "thumbnails"), kind).get("url", "")
        try:
            parsed = urllib.parse.urlsplit(value)
            host = parsed.hostname or ""
            if (parsed.scheme == "https" and not parsed.username and not parsed.password
                    and (host == "ytimg.com" or host.endswith(".ytimg.com"))):
                return value
        except (TypeError, ValueError):
            pass
    return None


def normalize_payload(channel_response, playlist_response, video_response, updated_at):
    if not date_valid(updated_at):
        raise FeedUnavailable("Invalid source timestamp")
    channels = records(channel_response)
    if not channels or channels[0].get("id") != CHANNEL_ID:
        raise FeedUnavailable("Expected channel was not returned")
    source = channels[0]
    stats = object_field(source, "statistics")
    allowed = {object_field(item, "contentDetails").get("videoId")
               for item in records(playlist_response)}
    videos, seen = [], set()
    for item in records(video_response):
        ident = item.get("id", "")
        snippet = object_field(item, "snippet")
        title = snippet.get("title", "")
        published = snippet.get("publishedAt", "")
        image = thumbnail(snippet)
        if (not isinstance(ident, str) or not VIDEO_ID.fullmatch(ident) or ident not in allowed
                or ident in seen or snippet.get("channelId") != CHANNEL_ID
                or not isinstance(title, str) or not title.strip() or not date_valid(published) or not image):
            continue
        seen.add(ident)
        numbers = object_field(item, "statistics")
        videos.append({"id": ident, "title": title.strip()[:500], "thumbnail": image,
            "publishedAt": published, "views": count(numbers.get("viewCount")),
            "likes": count(numbers.get("likeCount")),
            "kind": "live" if item.get("liveStreamingDetails") or snippet.get("liveBroadcastContent") == "live" else "video"})
    videos.sort(key=lambda item: datetime.datetime.fromisoformat(item["publishedAt"].replace("Z", "+00:00")), reverse=True)
    return {"version": 1, "updatedAt": updated_at, "stale": False,
        "channel": {"id": CHANNEL_ID, "title": object_field(source, "snippet").get("title", "ViktorKav"),
            "subscribers": None if stats.get("hiddenSubscriberCount") else count(stats.get("subscriberCount")),
            "totalViews": count(stats.get("viewCount")), "videoCount": count(stats.get("videoCount"))},
        "videos": videos[:MAX_VIDEOS]}


class GoogleSource:
    def __init__(self, key):
        if not key:
            raise FeedUnavailable("GOOGLE_API_KEY is required on the server")
        self.key = key

    def request(self, method, **params):
        params["key"] = self.key
        request = urllib.request.Request(API + method + "?" + urllib.parse.urlencode(params),
                                         headers={"User-Agent": "FreeMyTV-Channel/1.0"})
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                body = response.read(MAX_BODY + 1)
            if len(body) > MAX_BODY:
                raise FeedUnavailable("YouTube response exceeded the size limit")
            result = json.loads(body)
            records(result)
            return result
        except urllib.error.HTTPError as error:
            # Exceptions from urllib carry the authenticated URL: never log them.
            raise FeedUnavailable("YouTube HTTP " + str(error.code)) from None
        except (urllib.error.URLError, TimeoutError, ValueError, http.client.HTTPException, OSError):
            raise FeedUnavailable("YouTube request unavailable") from None

    def __call__(self):
        channel = self.request("channels", part="snippet,contentDetails,statistics", id=CHANNEL_ID)
        try:
            uploads = channel["items"][0]["contentDetails"]["relatedPlaylists"]["uploads"]
        except (IndexError, KeyError, TypeError):
            raise FeedUnavailable("Channel uploads are unavailable") from None
        playlist = self.request("playlistItems", part="contentDetails", playlistId=uploads, maxResults=MAX_VIDEOS)
        ids = [object_field(item, "contentDetails").get("videoId", "") for item in records(playlist)]
        ids = [ident for ident in ids if isinstance(ident, str) and VIDEO_ID.fullmatch(ident)]
        videos = self.request("videos", part="snippet,statistics,liveStreamingDetails", id=",".join(ids)) if ids else {"items": []}
        stamp = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        return normalize_payload(channel, playlist, videos, stamp)


class FeedCache:
    def __init__(self, loader, cache_path, ttl=900, clock=time.time):
        self.loader, self.path, self.ttl, self.clock = loader, pathlib.Path(cache_path), ttl, clock
        self.lock = threading.Lock()
        self.data, self.cached_at, self.retry_at = None, 0, 0
        try:
            saved = json.loads(self.path.read_text())
            data = saved["feed"]
            if data["channel"]["id"] == CHANNEL_ID and date_valid(data["updatedAt"]) and isinstance(data["videos"], list):
                self.data, self.cached_at = data, float(saved["cachedAt"])
        except (OSError, ValueError, KeyError, TypeError):
            pass

    def get(self):
        with self.lock:
            now = self.clock()
            age = now - self.cached_at
            if self.data is not None and 0 <= age < self.ttl:
                return copy.deepcopy(self.data)
            if now >= self.retry_at or now < self.cached_at:
                try:
                    loaded = self.loader()
                    if loaded.get("channel", {}).get("id") != CHANNEL_ID or not date_valid(loaded.get("updatedAt")):
                        raise FeedUnavailable("Invalid channel source")
                    self.data, self.cached_at, self.retry_at = loaded, now, 0
                    self.path.parent.mkdir(parents=True, exist_ok=True)
                    temporary = self.path.with_suffix(".tmp")
                    temporary.write_text(json.dumps({"cachedAt": now, "feed": loaded}, ensure_ascii=False))
                    temporary.replace(self.path)
                    return copy.deepcopy(loaded)
                except Exception as error:
                    # Cache is the availability boundary; report only the class, never an authenticated URL.
                    print("Channel source unavailable: " + type(error).__name__, flush=True)
                    self.retry_at = now + 60
            if self.data is None:
                raise FeedUnavailable("Channel data is temporarily unavailable")
            stale = copy.deepcopy(self.data)
            stale["stale"] = True
            return stale


class FeedHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        if path not in ("/channel", "/health"):
            return self.respond(404, {"error": "Not found"})
        if path == "/health":
            return self.respond(200, {"service": "free-my-tv-channel-feed", "ready": self.server.cache.data is not None,
                "recommendationsStatus": self.server.recommendations.get()["status"]})
        try:
            payload = self.server.cache.get()
            payload["recommendations"] = self.server.recommendations.get()
            self.respond(200, payload)
        except FeedUnavailable:
            self.respond(503, {"error": "Channel data is temporarily unavailable"})

    def respond(self, status, value):
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string, *args):
        pass


class _UnconfiguredSubscriptions:
    def get(self):
        return empty_feed()


class FeedServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, cache, recommendations=None):
        self.cache = cache
        self.recommendations = recommendations or _UnconfiguredSubscriptions()
        super().__init__(address, FeedHandler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3330)
    parser.add_argument("--cache", default="/state/channel.json")
    parser.add_argument("--subscriptions-cache", default="/state/subscriptions.json")
    args = parser.parse_args()
    try:
        cache = FeedCache(GoogleSource(os.environ.get("GOOGLE_API_KEY", "")), args.cache)
    except FeedUnavailable:
        raise SystemExit("Channel service requires its server-side Google API key") from None
    print("Channel feed ready on port " + str(args.port), flush=True)
    recommendations = SubscriptionCache(SubscriptionSource(os.environ.get("SUBSCRIPTIONS_CREDENTIALS",
        "/state/subscriptions-oauth.json")), args.subscriptions_cache)
    recommendations.get()
    FeedServer((args.host, args.port), cache, recommendations).serve_forever()


if __name__ == "__main__":
    main()
