import copy
import importlib.util
import io
import json
import pathlib
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.parse
from unittest.mock import patch

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'tools' / 'subscription_feed.py'
SCOPE = 'https://www.googleapis.com/auth/youtube.readonly'
CHANNEL = 'UC' + 'a' * 22
OTHER = 'UC' + 'b' * 22
FIRST = 'abcdefghij1'
SECOND = 'abcdefghij2'
STAMP = '2026-10-20T10:00:00Z'


def video(ident=FIRST, channel=CHANNEL, published=STAMP):
    return {'id': ident, 'snippet': {'title': ' Example video ', 'channelId': channel,
        'channelTitle': 'Example channel', 'publishedAt': published,
        'thumbnails': {'high': {'url': 'https://i.ytimg.com/vi/' + ident + '/hqdefault.jpg'}}},
        'statistics': {'viewCount': '10', 'likeCount': '2'}, 'contentDetails': {'duration': 'PT10M'}}


def feed():
    return {'source': 'subscriptions', 'status': 'ready', 'updatedAt': STAMP,
        'stale': False, 'subscriptionCount': 2, 'selectionVersion': 2, 'videos': [{
            'id': FIRST, 'title': 'Example video', 'thumbnail': 'https://i.ytimg.com/vi/' + FIRST + '/hqdefault.jpg',
            'publishedAt': STAMP, 'views': 10, 'likes': 2, 'kind': 'video',
            'channelId': CHANNEL, 'channelTitle': 'Example channel', 'durationSeconds': 600, 'isShort': False}]}


class Response:
    def __init__(self, data): self.body = json.dumps(data).encode()
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, limit): return self.body[:limit]


class SubscriptionTests(unittest.TestCase):
    def setUp(self):
        if not SOURCE.exists():
            self.fail('Subscription backend is not implemented')
        spec = importlib.util.spec_from_file_location('tv_subscription_feed', SOURCE)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = pathlib.Path(self.temp.name) / 'subscriptions.json'
        self.credentials = pathlib.Path(self.temp.name) / 'oauth.json'

    def authorize(self, **changes):
        data = {'type': 'authorized_user', 'client_id': 'test-client', 'client_secret': 'test-secret',
            'refresh_token': 'test-refresh', 'scope': SCOPE}
        data.update(changes)
        self.credentials.write_text(json.dumps(data))
        self.credentials.chmod(0o600)

    def cache(self, loader, now=None):
        return self.module.SubscriptionCache(loader, self.path, clock=(lambda: now[0]) if now else time.time)

    def finish(self, cache):
        worker = cache.worker
        if worker: worker.join(3)
        self.assertFalse(worker and worker.is_alive(), 'Background refresh did not finish')

    def test_duration_is_preserved_without_guessing_short_from_length(self):
        raw = video()
        raw['contentDetails']['duration'] = 'PT2M30S'
        result = self.module.normalize_video(raw, {CHANNEL})
        self.assertEqual(150, result.get('durationSeconds'))
        self.assertIsNone(result.get('isShort'))
        raw['contentDetails']['duration'] = 'PT1H2M3S'
        result = self.module.normalize_video(raw, {CHANNEL})
        self.assertEqual(3723, result.get('durationSeconds'))
        self.assertIs(False, result.get('isShort'))

    def test_selection_skips_short_and_unknown_then_uses_older_upload_from_same_channel(self):
        source = self.module.SubscriptionSource(self.credentials)
        source.catalog = [{'id': CHANNEL, 'uploads': 'UU' + CHANNEL[2:]}, {'id': OTHER, 'uploads': 'UU' + OTHER[2:]}]
        short = video('short000001', CHANNEL, '2026-10-20T13:00:00Z')
        short['contentDetails']['duration'] = 'PT1M'
        unknown = video('maybe000001', OTHER, '2026-10-20T12:00:00Z')
        unknown['contentDetails']['duration'] = 'PT2M'
        horizontal = video(SECOND, OTHER, '2026-10-20T11:00:00Z')
        horizontal['contentDetails']['duration'] = 'PT2M30S'
        rows = [short, unknown, horizontal, video()]
        def request(method, **params):
            return {'items': rows} if method == 'videos' else {'items': [{'contentDetails': {'videoId': v['id']}} for v in rows]}
        with patch.object(source, 'authorize'), patch.object(source, 'load_catalog'), patch.object(source, 'request', side_effect=request), \
                patch.object(source, 'short_status', create=True, side_effect=lambda ident: {'short000001': True, 'maybe000001': None, SECOND: False}[ident]):
            result = source()
        self.assertEqual([SECOND, FIRST], [v['id'] for v in result['videos']])
        self.assertTrue(all(v.get('isShort') is False for v in result['videos']))

    def test_new_live_does_not_remove_the_channels_regular_video_tab_candidate(self):
        source = self.module.SubscriptionSource(self.credentials)
        source.catalog = [{'id': CHANNEL, 'uploads': 'UU' + CHANNEL[2:]}]
        live = video(SECOND, CHANNEL, '2026-10-20T12:00:00Z')
        live['snippet']['liveBroadcastContent'] = 'live'
        live['contentDetails']['duration'] = 'PT0S'
        rows = [live, video()]
        def request(method, **params):
            return {'items': rows} if method == 'videos' else {'items': [{'contentDetails': {'videoId': v['id']}} for v in rows]}
        with patch.object(source, 'authorize'), patch.object(source, 'load_catalog'), patch.object(source, 'request', side_effect=request):
            result = source()
        self.assertEqual([SECOND, FIRST], [v['id'] for v in result['videos']])

    def test_catalog_keeps_older_uploads_for_horizontal_navigation(self):
        source = self.module.SubscriptionSource(self.credentials)
        source.catalog = [{'id': 'UC' + str(i).zfill(22), 'uploads': 'UU' + str(i).zfill(22)} for i in range(30)]
        rows = [video(str(i).zfill(11), source.catalog[0]['id'], '2026-10-22T10:00:00Z') for i in range(25)]
        rows += [video(str(100+i).zfill(11), c['id']) for i, c in enumerate(source.catalog[1:])]
        def request(method, **params):
            if method == 'playlistItems': return {'items': [{'contentDetails': {'videoId': v['id']}} for v in rows]}
            wanted = set(params['id'].split(','))
            return {'items': [v for v in rows if v['id'] in wanted]}
        # Supply all candidates so the test exercises global ordering rather than upload-page depth.
        with patch.object(source, 'authorize'), patch.object(source, 'load_catalog'), patch.object(source, 'request', side_effect=request), \
                patch.object(source, 'playlist', return_value=[v['id'] for v in rows]):
            result = source()
        self.assertEqual(54, len(result['videos']))
        self.assertEqual(30, len({v['channelId'] for v in result['videos']}))

    def test_cached_feed_rejects_unverified_or_short_entries(self):
        for changes in ({'isShort': True}, {'isShort': None}, {'isShort': 'false'}, {'durationSeconds': None}):
            data = feed()
            data['videos'][0].update(changes)
            with self.assertRaises(self.module.SubscriptionUnavailable): self.module.validate_feed(data)
        legacy = feed()
        del legacy['videos'][0]['isShort']
        with self.assertRaises(self.module.SubscriptionUnavailable): self.module.validate_feed(legacy)
        legacy = feed()
        legacy['selectionVersion'] = 1
        with self.assertRaises(self.module.SubscriptionUnavailable): self.module.validate_feed(legacy)
        legacy = feed()
        del legacy['selectionVersion']
        with self.assertRaises(self.module.SubscriptionUnavailable): self.module.validate_feed(legacy)
        data = feed()
        data['videos'][0]['durationSeconds'] = 120
        self.assertEqual(120, self.module.validate_feed(data)['videos'][0]['durationSeconds'])

    def test_short_route_is_read_without_oauth_and_unexpected_redirects_stay_unknown(self):
        source = self.module.SubscriptionSource(self.credentials)
        class Head:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *args): pass
        def head(request, timeout):
            self.assertEqual('HEAD', request.get_method())
            self.assertIsNone(request.get_header('Authorization'))
            return Head()
        with patch.object(source.shorts_opener, 'open', side_effect=head):
            self.assertIs(True, source.short_status(FIRST))
        for location, expected in [('https://www.youtube.com/watch?v=' + SECOND, False),
                                   ('https://example.test/watch?v=' + SECOND, None),
                                   ('https://www.youtube.com/watch?v=' + FIRST, None),
                                   ('https://[invalid', None)]:
            error = urllib.error.HTTPError('https://www.youtube.com/shorts/' + SECOND, 303, 'See Other', {'Location': location}, None)
            with patch.object(source.shorts_opener, 'open', side_effect=error):
                self.assertIs(expected, source.short_status(SECOND))
            source.shorts_cache.clear()

    def test_missing_credentials_is_explicit_without_network_or_stale_cache(self):
        source = self.module.SubscriptionSource(self.credentials)
        cache = self.cache(source)
        with patch('urllib.request.urlopen', side_effect=AssertionError('Unexpected network')):
            result = cache.get()
        self.assertEqual('authorization_required', result['status'])
        self.assertEqual([], result['videos'])
        self.assertIsNone(result['updatedAt'])

    def test_only_readonly_credentials_with_private_file_permissions_are_accepted(self):
        for changes in ({'scope': SCOPE + ' https://www.googleapis.com/auth/youtube'},
                        {'type': 'service_account'}, {'refresh_token': ''}):
            self.authorize(**changes)
            with self.assertRaises(self.module.AuthorizationRequired):
                self.module.SubscriptionSource(self.credentials)()
        self.authorize()
        self.credentials.chmod(0o644)
        with self.assertRaises(self.module.AuthorizationRequired):
            self.module.SubscriptionSource(self.credentials)()

    def test_refresh_uses_post_and_bearer_header_never_query_tokens(self):
        self.authorize()
        requests = []
        def open_request(request, timeout):
            requests.append(request)
            if request.full_url == 'https://oauth2.googleapis.com/token':
                self.assertEqual('POST', request.get_method())
                body = urllib.parse.parse_qs(request.data.decode())
                self.assertEqual(['test-refresh'], body['refresh_token'])
                self.assertLessEqual(timeout, 10)
                return Response({'access_token': 'private-access', 'expires_in': 3600, 'scope': SCOPE})
            self.assertNotIn('private-access', request.full_url)
            self.assertEqual('Bearer private-access', request.get_header('Authorization'))
            return Response({'items': []})
        with patch('urllib.request.urlopen', side_effect=open_request):
            source = self.module.SubscriptionSource(self.credentials)
            result = source()
            self.assertEqual(1, source.quota_used)
            source()
        self.assertEqual([], result['videos'])
        self.assertEqual(1, sum(r.get_method() == 'POST' for r in requests))
        self.assertEqual(0, source.quota_used)

    def test_paginated_catalog_upload_limit_normalization_and_catalog_ttl(self):
        self.authorize()
        calls = []
        def open_request(request, timeout):
            if request.get_method() == 'POST': return Response({'access_token': 'access', 'expires_in': 3600})
            url = urllib.parse.urlsplit(request.full_url)
            method, params = url.path.rsplit('/', 1)[-1], urllib.parse.parse_qs(url.query)
            calls.append((method, params))
            if method == 'subscriptions':
                self.assertEqual(['true'], params['mine'])
                if 'pageToken' not in params:
                    return Response({'items': [{'snippet': {'resourceId': {'channelId': CHANNEL}, 'title': 'First'}}], 'nextPageToken': 'page2'})
                return Response({'items': [{'snippet': {'resourceId': {'channelId': OTHER}, 'title': 'Second'}},
                    {'snippet': {'resourceId': {'channelId': CHANNEL}, 'title': 'Duplicate'}}]})
            if method == 'channels':
                return Response({'items': [{'id': ident, 'contentDetails': {'relatedPlaylists': {'uploads': 'UU' + ident[2:]}}} for ident in (CHANNEL, OTHER)]})
            if method == 'playlistItems':
                self.assertEqual(['5'], params['maxResults'])
                return Response({'items': [{'contentDetails': {'videoId': FIRST}}, {'contentDetails': {'videoId': SECOND}},
                    {'contentDetails': {'videoId': FIRST}}]})
            return Response({'items': [video(), video(SECOND, OTHER, '2026-10-20T11:00:00Z'),
                video('bad'), video('abcdefghij3', 'UC' + 'z' * 22)]})
        now = [1000000.0]
        source = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
        with patch('urllib.request.urlopen', side_effect=open_request):
            result = source()
            self.assertEqual(6, source.quota_used)  # 2 subscription pages + 1 channels batch + 2 uploads + 1 videos
            self.assertEqual(2, result['subscriptionCount'])
            self.assertEqual([SECOND, FIRST], [v['id'] for v in result['videos']])
            self.assertEqual(OTHER, result['videos'][0]['channelId'])
            self.assertEqual('Example channel', result['videos'][0]['channelTitle'])
            self.assertEqual(10, result['videos'][0]['views'])
            now[0] += 900
            source()
            self.assertEqual(3, source.quota_used)
            now[0] += 21600
            source()
            self.assertEqual(6, source.quota_used)

    def test_cadence_scales_to_daily_budget_and_never_refreshes_faster_than_15_minutes(self):
        self.assertEqual(900, self.module.refresh_interval(0))
        self.assertEqual(900, self.module.refresh_interval(10))
        for subscriptions in (100, 500, 1000):
            interval = self.module.refresh_interval(subscriptions)
            catalog_cost = 2 * ((subscriptions + 49) // 50)
            refresh_cost = subscriptions + ((subscriptions * 5 + 49) // 50)
            self.assertGreaterEqual(interval, 900)
            self.assertLessEqual(refresh_cost * 86400 / interval + 4 * catalog_cost, 5500)

    def test_collection_is_bounded_batches_and_keeps_more_than_24(self):
        self.authorize()
        source = self.module.SubscriptionSource(self.credentials)
        def request(method, **params):
            if method == 'subscriptions':
                return {'items': [{'snippet': {'resourceId': {'channelId': 'UC' + str(n).zfill(22)}}} for n in range(60)]}
            if method == 'channels':
                self.assertLessEqual(len(params['id'].split(',')), 50)
                return {'items': [{'id': ident, 'contentDetails': {'relatedPlaylists': {'uploads': 'UU' + ident[2:]}}} for ident in params['id'].split(',')]}
            if method == 'playlistItems':
                return {'items': [{'contentDetails': {'videoId': str(int(params['playlistId'][2:])).zfill(11)}}]}
            self.assertLessEqual(len(params['id'].split(',')), 50)
            return {'items': [video(ident, 'UC' + str(int(ident)).zfill(22)) for ident in params['id'].split(',')]}
        with patch.object(source, 'request', side_effect=request), patch('urllib.request.urlopen',
                return_value=Response({'access_token': 'access', 'expires_in': 3600})):
            result = source()
        self.assertEqual(60, result['subscriptionCount'])
        self.assertEqual(60, len(result['videos']))

    def test_expanded_catalog_format_checks_are_bounded_and_ordered(self):
        source = self.module.SubscriptionSource(self.credentials)
        source.catalog = [{'id': CHANNEL, 'uploads': 'UU' + CHANNEL[2:]}]
        rows = [video(str(i).zfill(11)) for i in range(12)]
        for row in rows: row['contentDetails']['duration'] = 'PT2M'
        active, maximum, lock = [0], [0], threading.Lock()
        def check(ident):
            with lock:
                active[0] += 1
                maximum[0] = max(maximum[0], active[0])
            time.sleep(.01)
            with lock: active[0] -= 1
            return False
        with patch.object(source, 'authorize'), patch.object(source, 'load_catalog'), \
                patch.object(source, 'playlist', return_value=[v['id'] for v in rows]), \
                patch.object(source, 'request', return_value={'items': rows}), patch.object(source, 'short_status', side_effect=check):
            result = source()
        self.assertEqual([v['id'] for v in rows], [v['id'] for v in result['videos']])
        self.assertGreater(maximum[0], 1)
        self.assertLessEqual(maximum[0], 4)

    def test_upgrade_keeps_verified_last_good_videos_while_expanding_catalog(self):
        legacy = feed()
        legacy['selectionVersion'] = 1
        self.path.write_text(json.dumps({'feed': legacy, 'cachedAt': 100}))
        started, release = threading.Event(), threading.Event()
        def slow():
            started.set()
            release.wait(3)
            return feed()
        cache = self.cache(slow, [101])
        try:
            result = cache.get()
            self.assertEqual(FIRST, result['videos'][0]['id'])
            self.assertTrue(result['stale'])
            self.assertRegex(result['sourceRevision'], r'^[a-f0-9]{64}$')
            self.assertEqual(1, result['selectionVersion'])
            self.assertTrue(started.wait(1))
        finally:
            release.set()
            self.finish(cache)

    def test_background_refresh_returns_immediately_and_deduplicates_workers(self):
        started, release = threading.Event(), threading.Event()
        def slow():
            started.set()
            release.wait(3)
            return feed()
        cache = self.cache(slow)
        self.addCleanup(release.set)
        before = time.monotonic()
        self.assertEqual('loading', cache.get()['status'])
        self.assertLess(time.monotonic() - before, 0.2)
        self.assertTrue(started.wait(1))
        worker = cache.worker
        cache.get()
        self.assertIs(worker, cache.worker)
        release.set()
        self.finish(cache)
        self.assertEqual('ready', cache.get()['status'])
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)

    def test_persistent_last_good_data_survives_transport_and_authorization_failure(self):
        now = [1000000.0]
        cache = self.cache(feed, now)
        cache.get()
        self.finish(cache)
        now[0] += 901
        def offline(): raise self.module.SubscriptionUnavailable('URL with secret should never surface')
        restarted = self.cache(offline, now)
        first = restarted.get()
        self.assertEqual(FIRST, first['videos'][0]['id'])
        self.assertTrue(first['stale'])
        self.finish(restarted)
        self.assertEqual('unavailable', restarted.get()['status'])
        now[0] += 901
        def revoked(): raise self.module.AuthorizationRequired('private token')
        restarted.loader = revoked
        restarted.get()
        self.finish(restarted)
        result = restarted.get()
        self.assertEqual('authorization_required', result['status'])
        self.assertTrue(result['stale'])
        self.assertEqual(STAMP, result['updatedAt'])
        self.assertEqual(FIRST, result['videos'][0]['id'])
        self.assertNotIn('private', json.dumps(result))

    def test_malformed_or_oversized_saved_cache_cannot_reach_clients(self):
        self.path.write_text(json.dumps({'cachedAt': 1000000, 'feed': dict(feed(), videos=[{'id': '../../bad'}])}))
        cache = self.cache(lambda: (_ for _ in ()).throw(self.module.SubscriptionUnavailable()))
        self.assertEqual([], cache.get()['videos'])
        self.finish(cache)
        self.path.write_text(' ' * (self.module.MAX_CACHE_BODY + 1))
        self.assertIsNone(self.cache(feed).data)

    def test_non_object_saved_cache_is_a_cold_start_with_configured_credentials(self):
        self.authorize()
        for value in ([], None, 'wrong-shape', 42, True):
            with self.subTest(value=value):
                self.path.write_text(json.dumps(value))
                source = self.module.SubscriptionSource(self.credentials)
                cache = self.cache(source)
                self.assertIsNone(cache.data)
                with patch('urllib.request.urlopen', side_effect=TimeoutError('offline')):
                    self.assertEqual([], cache.get()['videos'])
                    self.finish(cache)
                self.assertEqual('unavailable', cache.get()['status'])

    def test_non_object_optional_cache_does_not_prevent_own_metrics_http_startup(self):
        from tools import channel_feed as channel
        import http.client
        self.authorize()
        self.path.write_text('[]')
        recommendations = self.cache(self.module.SubscriptionSource(self.credentials))
        class OwnCache:
            data = {'channel': {'id': 'own'}, 'videos': [{'id': 'own-video', 'views': 42}]}
            def get(self): return copy.deepcopy(self.data)
        server = channel.FeedServer(('127.0.0.1', 0), OwnCache(), recommendations)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
        self.addCleanup(connection.close)
        with patch('urllib.request.urlopen', side_effect=TimeoutError('offline')):
            connection.request('GET', '/channel')
            response = connection.getresponse()
            self.assertEqual(200, response.status)
            result = json.loads(response.read())
            self.finish(recommendations)
        self.assertEqual([{'id': 'own-video', 'views': 42}], result['videos'])
        self.assertEqual([], result['recommendations']['videos'])

    def test_malformed_refresh_keeps_last_good_data(self):
        now = [1000000.0]
        cache = self.cache(feed, now)
        cache.get()
        self.finish(cache)
        now[0] += 901
        cache.loader = lambda: dict(feed(), videos=[{'id': '../../bad'}])
        cache.get()
        self.finish(cache)
        result = cache.get()
        self.assertEqual('unavailable', result['status'])
        self.assertEqual(FIRST, result['videos'][0]['id'])

    def test_upstream_errors_are_sanitized_and_unauthorized_requires_authorization(self):
        self.authorize()
        for code in (400, 401, 403, 500):
            failure = urllib.error.HTTPError('https://example/?secret=test-refresh', code, 'private-access', {}, io.BytesIO(b'private'))
            with patch('urllib.request.urlopen', side_effect=failure):
                with self.assertRaises(self.module.SubscriptionUnavailable) as caught:
                    self.module.SubscriptionSource(self.credentials)()
            self.assertNotIn('test-refresh', str(caught.exception))
            self.assertNotIn('private', str(caught.exception))
            if code in (400, 401): self.assertIsInstance(caught.exception, self.module.AuthorizationRequired)
        with patch('urllib.request.urlopen', side_effect=TimeoutError('private-token')):
            with self.assertRaises(self.module.SubscriptionUnavailable) as caught:
                self.module.SubscriptionSource(self.credentials)()
        self.assertNotIn('private-token', str(caught.exception))

    def test_oversized_and_noncollection_responses_are_rejected(self):
        self.authorize()
        for data in ({'access_token': 'x' * (1024 * 1024)}, ['bad']):
            with patch('urllib.request.urlopen', return_value=Response(data)):
                with self.assertRaises(self.module.SubscriptionUnavailable):
                    self.module.SubscriptionSource(self.credentials)()

    def test_failed_large_collection_backs_off_using_catalog_size_and_spent_quota(self):
        now = [1000000.0]
        class FailingCollection:
            subscription_count = 500
            quota_used = 550
            def __call__(self): raise self.module.SubscriptionUnavailable()
        loader = FailingCollection()
        loader.module = self.module
        cache = self.cache(loader, now)
        cache.get()
        self.finish(cache)
        self.assertGreaterEqual(cache.retry_at - now[0], 8640)
        worker = cache.worker
        now[0] += 901
        cache.get()
        self.assertIs(worker, cache.worker)

    def test_persisted_catalog_skips_subscription_enumeration_after_restart(self):
        self.authorize()
        now = [1000000.0]
        source = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
        source.catalog = [{'id': CHANNEL, 'uploads': 'UU' + CHANNEL[2:]}]
        source.catalog_at, source.subscription_count = now[0], 1
        # Saving uses the real cache boundary; only the external API is replaced.
        def network(request, timeout):
            if request.get_method() == 'POST': return Response({'access_token': 'access', 'expires_in': 3600})
            method = urllib.parse.urlsplit(request.full_url).path.rsplit('/', 1)[-1]
            if method != 'playlistItems': self.fail('Fresh persisted catalog was enumerated again')
            return Response({'items': []})
        with patch('urllib.request.urlopen', side_effect=network):
            cache = self.cache(source, now)
            cache.get()
            self.finish(cache)
            restarted_source = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
            restarted = self.cache(restarted_source, now)
            now[0] += 901
            restarted.get()
            self.finish(restarted)
        self.assertEqual(1, restarted.get()['subscriptionCount'])
        self.assertEqual('ready', restarted.get()['status'])

    def test_credentials_replacement_invalidates_memory_and_persisted_catalog(self):
        self.authorize()
        now = [1000000.0]
        old = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
        calls = []
        def network(request, timeout):
            if request.get_method() == 'POST': return Response({'access_token': 'access', 'expires_in': 3600})
            method = urllib.parse.urlsplit(request.full_url).path.rsplit('/', 1)[-1]
            calls.append(method)
            return Response({'items': []})
        with patch('urllib.request.urlopen', side_effect=network):
            old()
            saved = old.export_catalog()
            self.authorize(refresh_token='different-account-refresh')
            old()
            self.assertEqual(['subscriptions', 'subscriptions'], calls)
            restarted = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
            restarted.restore_catalog(saved)
            restarted()
            self.assertEqual(['subscriptions', 'subscriptions', 'subscriptions'], calls)
        self.assertNotIn('test-refresh', json.dumps(saved))
        self.assertNotIn('different-account', json.dumps(saved))

    def test_credential_replacement_drops_cached_feed_in_memory_and_after_restart(self):
        self.authorize()
        now = [1000000.0]
        release, entered = threading.Event(), threading.Event()
        block_new_account = [False]
        self.addCleanup(release.set)
        def network(request, timeout):
            if request.get_method() == 'POST':
                refresh = urllib.parse.parse_qs(request.data.decode())['refresh_token'][0]
                if refresh == 'account-b':
                    if block_new_account[0]:
                        entered.set()
                        release.wait(3)
                    return Response({'access_token': 'token-b', 'expires_in': 3600})
                return Response({'access_token': 'token-a', 'expires_in': 3600})
            is_b = request.get_header('Authorization') == 'Bearer token-b'
            channel, ident = (OTHER, SECOND) if is_b else (CHANNEL, FIRST)
            method = urllib.parse.urlsplit(request.full_url).path.rsplit('/', 1)[-1]
            if method == 'subscriptions':
                return Response({'items': [{'snippet': {'resourceId': {'channelId': channel}}}]})
            if method == 'channels':
                return Response({'items': [{'id': channel, 'contentDetails': {'relatedPlaylists': {'uploads': 'UU' + channel[2:]}}}]})
            if method == 'playlistItems': return Response({'items': [{'contentDetails': {'videoId': ident}}]})
            return Response({'items': [video(ident, channel)]})
        with patch('urllib.request.urlopen', side_effect=network):
            source = self.module.SubscriptionSource(self.credentials, clock=lambda: now[0])
            cache = self.cache(source, now)
            cache.get()
            self.finish(cache)
            self.assertEqual(FIRST, cache.get()['videos'][0]['id'])
            saved_a = self.path.read_text()
            revision_a = cache.get()['sourceRevision']
            self.authorize(refresh_token='account-b')
            block_new_account[0] = True
            replaced = cache.get()
            self.assertEqual('loading', replaced['status'])
            self.assertNotEqual(revision_a, replaced['sourceRevision'])
            self.assertEqual([], replaced['videos'])
            self.assertIsNone(replaced['updatedAt'])
            self.assertTrue(entered.wait(1), 'New identity must start collecting immediately')
            # A restart must reject A's still-fresh file, even before B succeeds.
            self.path.write_text(saved_a)
            restarted = self.cache(self.module.SubscriptionSource(self.credentials, clock=lambda: now[0]), now)
            self.assertIsNone(restarted.data)
            self.assertEqual([], restarted.get()['videos'])
            release.set()
            self.finish(cache)
            self.finish(restarted)
            self.assertEqual(SECOND, cache.get()['videos'][0]['id'])
            self.assertEqual(SECOND, restarted.get()['videos'][0]['id'])
            final = self.cache(self.module.SubscriptionSource(self.credentials, clock=lambda: now[0]), now)
            self.assertEqual(SECOND, final.get()['videos'][0]['id'])

    def test_old_worker_cannot_restore_previous_account_or_retain_it_when_new_account_fails(self):
        now = [1000000.0]
        started, release = threading.Event(), threading.Event()
        self.addCleanup(release.set)
        class BoundLoader:
            revision = ['account-a-file-revision']
            slow = False
            def credential_revision(inner): return list(inner.revision)
            def __call__(inner):
                if inner.revision == ['account-b-file-revision']:
                    raise self.module.SubscriptionUnavailable('New account unavailable')
                if inner.slow:
                    started.set()
                    release.wait(3)
                return feed()
        loader = BoundLoader()
        cache = self.cache(loader, now)
        cache.get()
        self.finish(cache)
        now[0] += 901
        loader.slow = True
        cache.get()
        self.assertTrue(started.wait(1))
        loader.revision = ['account-b-file-revision']
        self.assertEqual([], cache.get()['videos'])
        release.set()
        self.finish(cache)
        self.assertIsNone(cache.data, 'Late account A result must be discarded')
        cache.get()
        self.finish(cache)
        result = cache.get()
        self.assertEqual('unavailable', result['status'])
        self.assertEqual([], result['videos'])
        self.assertIsNone(result['updatedAt'])
        self.assertIsNone(self.cache(loader, now).data, 'Account A file must be rejected after restart')

    def test_pagination_cycles_stop_with_bounded_requests(self):
        self.authorize()
        requests = []
        def network(request, timeout):
            if request.get_method() == 'POST': return Response({'access_token': 'access', 'expires_in': 3600})
            requests.append(request)
            return Response({'items': [], 'nextPageToken': 'repeated'})
        with patch('urllib.request.urlopen', side_effect=network):
            with self.assertRaises(self.module.SubscriptionUnavailable):
                self.module.SubscriptionSource(self.credentials)()
        self.assertEqual(2, len(requests))

    def test_playlists_have_at_most_four_requests_in_flight(self):
        self.authorize()
        source = self.module.SubscriptionSource(self.credentials)
        source.catalog = [{'id': 'UC' + str(n).zfill(22), 'uploads': 'UU' + str(n).zfill(22)} for n in range(12)]
        source.catalog_at, source.subscription_count = time.time(), 12
        active, maximum, lock = [0], [0], threading.Lock()
        def network(request, timeout):
            if request.get_method() == 'POST': return Response({'access_token': 'access', 'expires_in': 3600})
            with lock:
                active[0] += 1
                maximum[0] = max(maximum[0], active[0])
            time.sleep(0.01)
            with lock: active[0] -= 1
            return Response({'items': []})
        with patch('urllib.request.urlopen', side_effect=network): source()
        self.assertGreater(maximum[0], 1)
        self.assertLessEqual(maximum[0], 4)

    def test_channel_response_includes_separate_recommendations_and_health(self):
        import sys
        sys.path.insert(0, str(SOURCE.parent))
        self.addCleanup(sys.path.remove, str(SOURCE.parent))
        spec = importlib.util.spec_from_file_location('subscription_channel_integration', SOURCE.parent / 'channel_feed.py')
        channel = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(channel)
        class OwnCache:
            data = {'channel': {'id': 'own'}, 'videos': [{'id': 'own-video', 'views': 42}]}
            def get(self): return copy.deepcopy(self.data)
        class Recommendations:
            def get(self): return feed()
        server = channel.FeedServer(('127.0.0.1', 0), OwnCache(), Recommendations())
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        import urllib.request
        base = 'http://127.0.0.1:' + str(server.server_port)
        with urllib.request.urlopen(base + '/channel', timeout=3) as response:
            result = json.load(response)
        self.assertEqual([{'id': 'own-video', 'views': 42}], result['videos'])
        self.assertEqual(FIRST, result['recommendations']['videos'][0]['id'])
        with urllib.request.urlopen(base + '/health', timeout=3) as response:
            self.assertEqual('ready', json.load(response)['recommendationsStatus'])


if __name__ == '__main__': unittest.main()
