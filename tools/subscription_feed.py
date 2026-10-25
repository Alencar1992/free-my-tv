#!/usr/bin/env python3
"""Read-only subscriptions, collected off the HTTP thread with a separate cache."""
import concurrent.futures
import copy
import datetime
import http.client
import hashlib
import json
import math
import os
import pathlib
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

SCOPE = 'https://www.googleapis.com/auth/youtube.readonly'
API = 'https://www.googleapis.com/youtube/v3/'
TOKEN_URL = 'https://oauth2.googleapis.com/token'
MAX_BODY = 1024 * 1024
MAX_CACHE_BODY = 2 * MAX_BODY
MAX_SUBSCRIPTIONS = 5000
SELECTION_VERSION = 2
MAX_RECOMMENDATIONS = 512
CATALOG_TTL = 6 * 3600
VIDEO_ID = re.compile(r'^[A-Za-z0-9_-]{11}$')
CHANNEL_ID = re.compile(r'^UC[A-Za-z0-9_-]{22}$')
PLAYLIST_ID = re.compile(r'^[A-Za-z0-9_-]{10,100}$')


class NoShortsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


def duration_seconds(value):
    if not isinstance(value, str):
        return None
    match = re.fullmatch(r'P(?:(\d{1,7})D)?(?:T(?:(\d{1,7})H)?(?:(\d{1,7})M)?(?:(\d{1,7})S)?)?', value)
    if not match or not any(part is not None for part in match.groups()):
        return None
    days, hours, minutes, seconds = (int(part or 0) for part in match.groups())
    total = days * 86400 + hours * 3600 + minutes * 60 + seconds
    return total if total <= 31536000 else None


class SubscriptionUnavailable(Exception):
    pass


class AuthorizationRequired(SubscriptionUnavailable):
    pass


def timestamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')


def parsed_date(value):
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError('Invalid date')
    result = datetime.datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('Missing timezone')
    return result


def collection(value):
    if (not isinstance(value, dict) or not isinstance(value.get('items'), list)
            or not all(isinstance(item, dict) for item in value['items'])):
        raise SubscriptionUnavailable('Invalid YouTube collection')
    return value['items']


def obj(value, name):
    result = value.get(name, {})
    if not isinstance(result, dict):
        raise SubscriptionUnavailable('Invalid YouTube object')
    return result


def number(value):
    if isinstance(value, bool) or value is None:
        return None
    text = str(value)
    return int(text) if re.fullmatch(r'\d{1,19}', text) and int(text) <= 9223372036854775807 else None


def text(value, limit):
    if not isinstance(value, str):
        return ''
    return ''.join(c for c in value if c >= ' ' and c != '\x7f').strip()[:limit]


def valid_image(value):
    if not isinstance(value, str) or len(value) > 2048:
        return False
    try:
        parsed = urllib.parse.urlsplit(value)
        host = parsed.hostname or ''
        return (parsed.scheme == 'https' and not parsed.username and not parsed.password
                and parsed.port in (None, 443) and (host == 'ytimg.com' or host.endswith('.ytimg.com')))
    except ValueError:
        return False


def normalize_video(item, allowed):
    ident = item.get('id', '')
    snippet = obj(item, 'snippet')
    channel = snippet.get('channelId', '')
    title, channel_title = text(snippet.get('title'), 500), text(snippet.get('channelTitle'), 200)
    if (not isinstance(ident, str) or not VIDEO_ID.fullmatch(ident)
            or not isinstance(channel, str) or not CHANNEL_ID.fullmatch(channel)
            or channel not in allowed or not title or not channel_title):
        return None
    try:
        parsed_date(snippet.get('publishedAt'))
    except (ValueError, TypeError):
        return None
    thumbnails, image = obj(snippet, 'thumbnails'), None
    for kind in ('maxres', 'standard', 'high', 'medium', 'default'):
        candidate = obj(thumbnails, kind).get('url')
        if valid_image(candidate):
            image = candidate
            break
    if not image:
        return None
    stats = obj(item, 'statistics')
    kind = 'live' if item.get('liveStreamingDetails') or snippet.get('liveBroadcastContent') == 'live' else 'video'
    duration = duration_seconds(obj(item, 'contentDetails').get('duration'))
    if kind == 'live' and duration is None:
        duration = 0
    return {'id': ident, 'title': title, 'channelId': channel, 'channelTitle': channel_title,
        'publishedAt': snippet['publishedAt'], 'thumbnail': image,
        'views': number(stats.get('viewCount')), 'likes': number(stats.get('likeCount')),
        'kind': kind, 'durationSeconds': duration,
        'isShort': False if kind == 'live' or (duration is not None and duration > 180) else None}


def refresh_interval(subscriptions):
    # One unit per uploads playlist, hydration in batches of 50, catalog every six hours.
    subscriptions = min(MAX_SUBSCRIPTIONS, max(0, subscriptions))
    catalog_cost = 2 * math.ceil(subscriptions / 50)
    refresh_cost = subscriptions + math.ceil(subscriptions * 5 / 50)
    return max(900, math.ceil(86400 * refresh_cost / max(1, 5500 - 4 * catalog_cost)))


def empty_feed(status='authorization_required'):
    return {'source': 'subscriptions', 'status': status, 'updatedAt': None, 'stale': False,
        'subscriptionCount': 0, 'selectionVersion': SELECTION_VERSION, 'videos': []}


def validate_feed(value):
    if (not isinstance(value, dict) or value.get('source') != 'subscriptions'
            or value.get('status') != 'ready' or type(value.get('subscriptionCount')) is not int
            or type(value.get('selectionVersion')) is not int or value['selectionVersion'] != SELECTION_VERSION
            or not 0 <= value['subscriptionCount'] <= MAX_SUBSCRIPTIONS
            or not isinstance(value.get('videos'), list) or len(value['videos']) > MAX_RECOMMENDATIONS):
        raise SubscriptionUnavailable('Invalid subscriptions cache')
    try:
        parsed_date(value.get('updatedAt'))
    except (ValueError, TypeError):
        raise SubscriptionUnavailable('Invalid subscriptions timestamp') from None
    normalized, seen = [], set()
    for item in value['videos']:
        if not isinstance(item, dict):
            raise SubscriptionUnavailable('Invalid subscriptions video')
        duration = item.get('durationSeconds')
        if (item.get('isShort') is not False or type(duration) is not int or not 0 <= duration <= 31536000
                or (item.get('kind') != 'live' and duration == 0)):
            raise SubscriptionUnavailable('Unverified subscriptions format')
        raw = {'id': item.get('id'), 'snippet': {'channelId': item.get('channelId'),
            'channelTitle': item.get('channelTitle'), 'title': item.get('title'),
            'publishedAt': item.get('publishedAt'), 'thumbnails': {'high': {'url': item.get('thumbnail')}},
            'liveBroadcastContent': 'live' if item.get('kind') == 'live' else 'none'},
            'statistics': {'viewCount': item.get('views'), 'likeCount': item.get('likes')},
            'contentDetails': {'duration': 'PT' + str(duration) + 'S'}}
        video = normalize_video(raw, {item.get('channelId')} if isinstance(item.get('channelId'), str) else set())
        if not video or video['id'] in seen or item.get('kind') not in ('live', 'video'):
            raise SubscriptionUnavailable('Invalid subscriptions video')
        video['isShort'] = False
        normalized.append(video)
        seen.add(video['id'])
    normalized.sort(key=lambda item: parsed_date(item['publishedAt']), reverse=True)
    return {'source': 'subscriptions', 'status': 'ready', 'updatedAt': value['updatedAt'],
        'stale': False, 'subscriptionCount': value['subscriptionCount'], 'selectionVersion': SELECTION_VERSION, 'videos': normalized}


class SubscriptionSource:
    def __init__(self, credentials_path, clock=time.time):
        self.credentials_path, self.clock = pathlib.Path(credentials_path), clock
        self.access_token, self.expires_at, self.credential_identity = None, 0, None
        self.catalog, self.catalog_at, self.subscription_count = [], None, 0
        self.catalog_revision = None
        self.quota_used, self.quota_lock = 0, threading.Lock()
        self.shorts_opener = urllib.request.build_opener(NoShortsRedirect())
        self.shorts_cache = {}
        self.shorts_lock = threading.Lock()

    def short_status(self, ident):
        """Public Shorts route: 200 is a Short; a matching watch redirect is a regular video.

        No OAuth goes to the web endpoint. Any other response stays unknown and is omitted.
        """
        if not isinstance(ident, str) or not VIDEO_ID.fullmatch(ident):
            return None
        with self.shorts_lock:
            if ident in self.shorts_cache:
                return self.shorts_cache[ident]
        request = urllib.request.Request('https://www.youtube.com/shorts/' + ident, method='HEAD',
            headers={'User-Agent': 'Mozilla/5.0 FreeMyTV/1.0'})
        result = None
        try:
            with self.shorts_opener.open(request, timeout=6) as response:
                if response.status == 200:
                    result = True
        except urllib.error.HTTPError as error:
            location = error.headers.get('Location', '')
            if error.code in (301, 302, 303, 307, 308):
                try:
                    parsed = urllib.parse.urlsplit(location)
                    if (parsed.scheme == 'https' and parsed.netloc == 'www.youtube.com' and parsed.path == '/watch'
                            and urllib.parse.parse_qs(parsed.query).get('v') == [ident]):
                        result = False
                except ValueError:
                    pass
            error.close()
        except (urllib.error.URLError, TimeoutError, http.client.HTTPException, OSError, ValueError):
            pass
        if result is not None:
            with self.shorts_lock:
                self.shorts_cache[ident] = result
                if len(self.shorts_cache) > 2048:
                    del self.shorts_cache[next(iter(self.shorts_cache))]
        return result

    def credential_revision(self):
        try:
            info = self.credentials_path.stat()
            if info.st_mode & 0o777 == 0o600 and 0 < info.st_size <= 16384:
                return [info.st_dev, info.st_ino, info.st_mtime_ns, info.st_size]
        except OSError:
            pass
        return None

    def configured(self):
        return self.credential_revision() is not None

    def credentials(self):
        try:
            if not self.configured():
                raise ValueError()
            with self.credentials_path.open() as handle:
                credentials = json.loads(handle.read(16385))
            if (not isinstance(credentials, dict) or credentials.get('type') != 'authorized_user'
                    or credentials.get('scope') != SCOPE
                    or any(not isinstance(credentials.get(key), str) or not credentials[key].strip()
                        or len(credentials[key]) > 4096 for key in ('client_id', 'client_secret', 'refresh_token'))):
                raise ValueError()
            return credentials
        except (OSError, ValueError, TypeError):
            raise AuthorizationRequired('Subscriptions authorization is required') from None

    def read_json(self, request, token_request=False):
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                body = response.read(MAX_BODY + 1)
            if len(body) > MAX_BODY:
                raise SubscriptionUnavailable('YouTube response exceeded the size limit')
            value = json.loads(body)
            if not isinstance(value, dict):
                raise SubscriptionUnavailable('Invalid YouTube response')
            return value
        except urllib.error.HTTPError as error:
            code = error.code
            error.close()
            if code == 401 or (token_request and code == 400):
                self.access_token = None
                raise AuthorizationRequired('Subscriptions authorization is required') from None
            raise SubscriptionUnavailable('YouTube HTTP ' + str(code)) from None
        except (urllib.error.URLError, TimeoutError, ValueError, http.client.HTTPException, OSError):
            raise SubscriptionUnavailable('YouTube request unavailable') from None

    def authorize(self):
        credentials = self.credentials()
        revision = self.credential_revision()
        identity = tuple(credentials[key] for key in ('client_id', 'client_secret', 'refresh_token'))
        if ((self.catalog_revision is not None and self.catalog_revision != revision)
                or (self.credential_identity is not None and identity != self.credential_identity)):
            self.catalog, self.catalog_at, self.subscription_count = [], None, 0
        self.catalog_revision = revision
        if self.access_token and self.clock() < self.expires_at and identity == self.credential_identity:
            return
        body = urllib.parse.urlencode({'grant_type': 'refresh_token', 'client_id': credentials['client_id'],
            'client_secret': credentials['client_secret'], 'refresh_token': credentials['refresh_token']}).encode()
        response = self.read_json(urllib.request.Request(TOKEN_URL, data=body,
            headers={'Content-Type': 'application/x-www-form-urlencoded'}), token_request=True)
        token, expires = response.get('access_token'), number(response.get('expires_in'))
        if response.get('scope', SCOPE) != SCOPE:
            raise AuthorizationRequired('Subscriptions authorization is required')
        if not isinstance(token, str) or not token or len(token) > 8192 or not expires or any(c.isspace() for c in token):
            raise SubscriptionUnavailable('Invalid OAuth response')
        self.access_token, self.expires_at = token, self.clock() + max(0, min(expires, 3600) - 60)
        self.credential_identity = identity

    def request(self, method, **params):
        # Authorization is deliberately unredirected: a redirect must never forward the bearer token.
        request = urllib.request.Request(API + method + '?' + urllib.parse.urlencode(params),
            headers={'User-Agent': 'FreeMyTV-Subscriptions/1.0'})
        request.add_unredirected_header('Authorization', 'Bearer ' + self.access_token)
        with self.quota_lock:
            self.quota_used += 1
        result = self.read_json(request)
        collection(result)
        return result

    def load_catalog(self):
        now = self.clock()
        if self.catalog_at is not None and 0 <= now - self.catalog_at < CATALOG_TTL:
            return
        channels, pages, next_page = {}, set(), None
        while True:
            params = {'part': 'snippet', 'mine': 'true', 'maxResults': 50}
            if next_page:
                params['pageToken'] = next_page
            response = self.request('subscriptions', **params)
            for item in collection(response):
                snippet = obj(item, 'snippet')
                ident = obj(snippet, 'resourceId').get('channelId', '')
                if isinstance(ident, str) and CHANNEL_ID.fullmatch(ident):
                    channels[ident] = {'id': ident}
                if len(channels) > MAX_SUBSCRIPTIONS:
                    raise SubscriptionUnavailable('Subscriptions catalog exceeded the size limit')
            next_page = response.get('nextPageToken')
            if not next_page:
                break
            if not isinstance(next_page, str) or len(next_page) > 512 or next_page in pages or len(pages) >= 100:
                raise SubscriptionUnavailable('Invalid subscriptions pagination')
            pages.add(next_page)
        catalog, ids = [], list(channels)
        for offset in range(0, len(ids), 50):
            expected = set(ids[offset:offset + 50])
            response = self.request('channels', part='contentDetails', id=','.join(ids[offset:offset + 50]))
            for item in collection(response):
                uploads = obj(obj(item, 'contentDetails'), 'relatedPlaylists').get('uploads', '')
                if item.get('id') in expected and isinstance(uploads, str) and PLAYLIST_ID.fullmatch(uploads):
                    catalog.append({'id': item['id'], 'uploads': uploads})
        self.catalog, self.catalog_at, self.subscription_count = catalog, now, len(ids)

    def playlist(self, channel):
        try:
            response = self.request('playlistItems', part='contentDetails', playlistId=channel['uploads'], maxResults=5)
        except SubscriptionUnavailable as error:
            if type(error) is SubscriptionUnavailable and str(error) == 'YouTube HTTP 404':
                return []  # A deleted channel can retain a subscription but have no uploads playlist.
            raise
        ids = []
        for item in collection(response)[:5]:
            ident = obj(item, 'contentDetails').get('videoId', '')
            if isinstance(ident, str) and VIDEO_ID.fullmatch(ident):
                ids.append(ident)
        return ids

    def __call__(self):
        self.quota_used = 0
        self.authorize()
        self.load_catalog()
        ids, seen = [], set()
        # Submit only four playlists at a time; large catalogs cannot queue thousands of futures.
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for offset in range(0, len(self.catalog), 4):
                for batch in pool.map(self.playlist, self.catalog[offset:offset + 4]):
                    for ident in batch:
                        if ident not in seen:
                            ids.append(ident)
                            seen.add(ident)
        videos, allowed = [], {item['id'] for item in self.catalog}
        for offset in range(0, len(ids), 50):
            batch = set(ids[offset:offset + 50])
            response = self.request('videos', part='snippet,statistics,liveStreamingDetails,contentDetails', id=','.join(ids[offset:offset + 50]))
            for item in collection(response):
                if isinstance(item.get('id'), str) and item['id'] in batch:
                    normalized = normalize_video(item, allowed)
                    if normalized and all(v['id'] != normalized['id'] for v in videos):
                        videos.append(normalized)
        # Keep older eligible uploads; the client diversifies each horizontal group by channel.
        videos.sort(key=lambda item: parsed_date(item['publishedAt']), reverse=True)
        selected = []
        def eligible(video):
            if video['durationSeconds'] is None:
                return None
            if video['isShort'] is None:
                video['isShort'] = self.short_status(video['id'])
            if video['isShort'] is not False or (video['kind'] != 'live' and video['durationSeconds'] <= 0):
                return None
            return video
        # Public format checks can be slow. Bound concurrency and preserve chronological order.
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for offset in range(0, len(videos), 4):
                selected.extend(v for v in pool.map(eligible, videos[offset:offset + 4]) if v is not None)
                if len(selected) >= MAX_RECOMMENDATIONS:
                    selected = selected[:MAX_RECOMMENDATIONS]
                    break
        return {'source': 'subscriptions', 'status': 'ready', 'updatedAt': timestamp(), 'stale': False,
            'subscriptionCount': self.subscription_count, 'selectionVersion': SELECTION_VERSION, 'videos': selected}

    def export_catalog(self):
        return {'updatedAt': self.catalog_at, 'subscriptionCount': self.subscription_count,
            'channels': self.catalog, 'credentialRevision': self.catalog_revision}

    def restore_catalog(self, value):
        if (not isinstance(value, dict) or value.get('credentialRevision') is None
                or value.get('credentialRevision') != self.credential_revision()):
            return
        stamp, count, channels = value.get('updatedAt'), value.get('subscriptionCount'), value.get('channels')
        if (type(stamp) not in (int, float) or not math.isfinite(stamp) or type(count) is not int
                or not 0 <= count <= MAX_SUBSCRIPTIONS or not isinstance(channels, list) or len(channels) > count):
            return
        seen = set()
        for item in channels:
            if (not isinstance(item, dict) or not isinstance(item.get('id'), str) or not CHANNEL_ID.fullmatch(item['id'])
                    or not isinstance(item.get('uploads'), str) or not PLAYLIST_ID.fullmatch(item['uploads']) or item['id'] in seen):
                return
            seen.add(item['id'])
        self.catalog_at, self.subscription_count, self.catalog_revision = stamp, count, value['credentialRevision']
        self.catalog = [{'id': item['id'], 'uploads': item['uploads']} for item in channels]


class SubscriptionCache:
    def __init__(self, loader, cache_path, clock=time.time):
        self.loader, self.path, self.clock = loader, pathlib.Path(cache_path), clock
        self.lock, self.worker = threading.Lock(), None
        self.data, self.cached_at, self.retry_at, self.status = None, 0, 0, 'loading'
        self.credential_revision = self.current_revision()
        try:
            if self.path.stat().st_size > MAX_CACHE_BODY:
                raise ValueError()
            with self.path.open() as handle:
                saved = json.loads(handle.read(MAX_CACHE_BODY + 1))
            if not isinstance(saved, dict):
                raise ValueError()
            if (hasattr(loader, 'credential_revision')
                    and (self.credential_revision is None or saved.get('credentialRevision') != self.credential_revision)):
                raise ValueError()
            cached_feed = saved['feed']
            upgrading = isinstance(cached_feed, dict) and cached_feed.get('selectionVersion') == 1
            if upgrading:
                cached_feed = dict(cached_feed, selectionVersion=SELECTION_VERSION)
            data, cached_at = validate_feed(cached_feed), saved['cachedAt']
            if upgrading:
                data['selectionVersion'] = 1  # Clients poll promptly until the expanded snapshot is ready.
            if type(cached_at) not in (int, float) or not math.isfinite(cached_at):
                raise ValueError()
            self.data, self.cached_at, self.status = data, (self.clock() - refresh_interval(data['subscriptionCount']) - 1) if upgrading else cached_at, 'ready'
            if hasattr(loader, 'restore_catalog'):
                loader.restore_catalog(saved.get('catalog'))
        except (OSError, ValueError, TypeError, KeyError, SubscriptionUnavailable):
            pass

    def current_revision(self):
        return self.loader.credential_revision() if hasattr(self.loader, 'credential_revision') else None

    def get(self):
        with self.lock:
            revision = self.current_revision()
            if revision != self.credential_revision:
                self.data, self.cached_at, self.retry_at, self.status = None, 0, 0, 'loading'
                self.credential_revision = revision
            now = self.clock()
            ttl = refresh_interval(self.data['subscriptionCount'] if self.data else 0)
            age = now - self.cached_at
            fresh = self.data is not None and 0 <= age < ttl
            configured = not hasattr(self.loader, 'configured') or self.loader.configured()
            if not configured:
                self.status = 'authorization_required'
            elif not fresh and now >= self.retry_at and not (self.worker and self.worker.is_alive()):
                self.status = 'loading' if self.data is None else self.status
                self.worker = threading.Thread(target=self.refresh, args=(revision,), name='subscriptions-refresh', daemon=True)
                self.worker.start()
            result = copy.deepcopy(self.data) if self.data else empty_feed(self.status)
            result['sourceRevision'] = hashlib.sha256(json.dumps(revision).encode()).hexdigest()
            result['status'] = self.status
            result['stale'] = self.data is not None and (not fresh or self.status != 'ready')
            return result

    def refresh(self, revision):
        try:
            loaded = validate_feed(self.loader())
            now = self.clock()
            with self.lock:
                if revision != self.credential_revision or revision != self.current_revision():
                    return
            saved = {'cachedAt': now, 'feed': loaded, 'credentialRevision': revision}
            if hasattr(self.loader, 'export_catalog'):
                saved['catalog'] = self.loader.export_catalog()
            try:
                self.path.parent.mkdir(parents=True, exist_ok=True)
                temporary = self.path.with_suffix('.tmp')
                descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
                with os.fdopen(descriptor, 'w') as handle:
                    os.fchmod(handle.fileno(), 0o600)
                    json.dump(saved, handle, ensure_ascii=False)
                temporary.replace(self.path)
            except OSError:
                pass  # A read-only disk must not hide a successful in-memory refresh.
            with self.lock:
                if revision != self.credential_revision or revision != self.current_revision():
                    return
                self.data, self.cached_at, self.retry_at, self.status = loaded, now, 0, 'ready'
        except Exception as error:
            # No exception text is logged: transport errors may contain secrets and authenticated URLs.
            with self.lock:
                if revision != self.credential_revision or revision != self.current_revision():
                    return
                self.status = 'authorization_required' if isinstance(error, AuthorizationRequired) else 'unavailable'
                count = max(self.data['subscriptionCount'] if self.data else 0,
                    getattr(self.loader, 'subscription_count', 0))
                spent = getattr(self.loader, 'quota_used', 0)
                self.retry_at = self.clock() + max(refresh_interval(count), math.ceil(spent * 86400 / 5500))
