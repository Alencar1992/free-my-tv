import base64
import hashlib
import importlib.util
import os
import pathlib
import stat
import tempfile
import unittest
import urllib.parse

path = pathlib.Path(__file__).resolve().parents[1] / 'tools' / 'authorize_subscriptions.py'


class BootstrapTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if path.exists():
            spec = importlib.util.spec_from_file_location('bootstrap', path)
            cls.module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.module)
        else:
            cls.module = None

    def bootstrap(self):
        self.assertIsNotNone(self.module, 'readonly OAuth bootstrap is not implemented')
        return self.module

    def test_authorization_uses_readonly_pkce_and_no_secret(self):
        m = self.bootstrap()
        session = m.OAuthSession({'client_id': 'test-client', 'client_secret': 'private-client-value'}, 'http://127.0.0.1:32123/oauth2callback')
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(session.authorization_url()).query)
        self.assertEqual(params['scope'], [m.SCOPE])
        self.assertEqual(params['code_challenge_method'], ['S256'])
        expected = base64.urlsafe_b64encode(hashlib.sha256(session.verifier.encode()).digest()).rstrip(b'=').decode()
        self.assertEqual(params['code_challenge'], [expected])
        self.assertGreaterEqual(len(session.state), 32)
        self.assertNotIn('private-client-value', session.authorization_url())
        self.assertEqual(params['access_type'], ['offline'])
        self.assertEqual(params['include_granted_scopes'], ['false'])

    def test_callback_rejects_wrong_state_or_missing_code(self):
        m = self.bootstrap()
        session = m.OAuthSession({'client_id': 'test', 'client_secret': 'private'}, 'http://127.0.0.1:1234/oauth2callback')
        with self.assertRaises(m.AuthorizationError):
            session.callback_code('/oauth2callback?state=wrong&code=never')
        with self.assertRaises(m.AuthorizationError):
            session.callback_code('/oauth2callback?state=' + session.state)
        self.assertEqual(session.callback_code('/oauth2callback?' + urllib.parse.urlencode({'state': session.state, 'code': 'valid-code'})), 'valid-code')
        with self.assertRaises(m.AuthorizationError):
            session.callback_code('/other?' + urllib.parse.urlencode({'state': session.state, 'code': 'valid'}))

    def test_token_must_be_readonly_and_refreshable(self):
        m = self.bootstrap()
        session = m.OAuthSession({'client_id': 'test', 'client_secret': 'private'}, 'http://127.0.0.1:1234/oauth2callback')
        for token in ({'scope': m.SCOPE}, {'scope': m.SCOPE + ' https://www.googleapis.com/auth/youtube', 'refresh_token': 'r'}, {'refresh_token': 'r'}):
            with self.assertRaises(m.AuthorizationError):
                session.credentials(token)
        token = session.credentials({'scope': m.SCOPE, 'refresh_token': 'r'})
        self.assertEqual(token['scope'], m.SCOPE)
        self.assertEqual(token['type'], 'authorized_user')
        self.assertNotIn('access_token', token)

    def test_credentials_are_stored_owner_only_without_overwriting_existing(self):
        m = self.bootstrap()
        with tempfile.TemporaryDirectory() as temporary:
            target = pathlib.Path(temporary) / 'private' / 'oauth.json'
            m.save_credentials(target, {'refresh_token': 'private-value'})
            self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(target.parent.stat().st_mode), 0o700)
            with self.assertRaises(FileExistsError):
                m.save_credentials(target, {'refresh_token': 'other'})
            self.assertIn('private-value', target.read_text())


if __name__ == '__main__':
    unittest.main()
