#!/usr/bin/env python3
"""Create a separate read-only YouTube credential through a local PKCE callback."""
import argparse
import base64
import hashlib
import hmac
import http.client
import json
import os
import pathlib
import secrets
import threading
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

SCOPE = 'https://www.googleapis.com/auth/youtube.readonly'
TOKEN_URL = 'https://oauth2.googleapis.com/token'


class AuthorizationError(Exception):
    pass


class OAuthSession:
    def __init__(self, client, redirect):
        self.client, self.redirect = client, redirect
        self.verifier, self.state = secrets.token_urlsafe(64), secrets.token_urlsafe(32)

    def authorization_url(self):
        challenge = base64.urlsafe_b64encode(hashlib.sha256(self.verifier.encode()).digest()).rstrip(b'=').decode()
        return 'https://accounts.google.com/o/oauth2/v2/auth?' + urllib.parse.urlencode({
            'client_id': self.client['client_id'], 'redirect_uri': self.redirect,
            'response_type': 'code', 'scope': SCOPE, 'access_type': 'offline',
            'prompt': 'consent select_account', 'include_granted_scopes': 'false',
            'state': self.state, 'code_challenge': challenge, 'code_challenge_method': 'S256'})

    def callback_code(self, path):
        if len(path) > 8192:
            raise AuthorizationError('Invalid callback')
        parsed = urllib.parse.urlsplit(path)
        params = urllib.parse.parse_qs(parsed.query)
        state = params.get('state', [''])
        codes = params.get('code', [''])
        if (parsed.path != '/oauth2callback' or len(state) != 1
                or not hmac.compare_digest(state[0], self.state) or 'error' in params
                or len(codes) != 1 or not codes[0]):
            raise AuthorizationError('Authorization was not completed')
        return codes[0]

    def credentials(self, result):
        if not isinstance(result, dict) or result.get('scope', '').split() != [SCOPE]:
            raise AuthorizationError('Only the read-only scope is accepted')
        token = result.get('refresh_token')
        if not isinstance(token, str) or not token:
            raise AuthorizationError('Offline authorization is required')
        return {'type': 'authorized_user', 'client_id': self.client['client_id'],
                'client_secret': self.client['client_secret'], 'refresh_token': token, 'scope': SCOPE}

    def exchange(self, code):
        request = urllib.request.Request(TOKEN_URL, data=urllib.parse.urlencode({
            'code': code, 'client_id': self.client['client_id'],
            'client_secret': self.client['client_secret'], 'redirect_uri': self.redirect,
            'grant_type': 'authorization_code', 'code_verifier': self.verifier}).encode(),
            headers={'Content-Type': 'application/x-www-form-urlencoded'})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read(65537)
            if len(body) > 65536:
                raise AuthorizationError('Invalid authorization response')
            return self.credentials(json.loads(body))
        except (urllib.error.URLError, TimeoutError, http.client.HTTPException, OSError, ValueError):
            raise AuthorizationError('Could not complete authorization') from None


def save_credentials(target, credentials):
    target = pathlib.Path(target)
    if not target.parent.exists():
        target.parent.mkdir(parents=True, mode=0o700)
    if target.parent.is_symlink() or target.is_symlink():
        raise AuthorizationError('Private credential path is required')
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(credentials, output)
        output.write('\n')


class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            code = self.server.session.callback_code(self.path)
            credentials = self.server.session.exchange(code)
            save_credentials(self.server.target, credentials)
            self.server.success = True
            self.reply(200, 'Autorização concluída. Pode fechar esta aba e voltar ao Codex.')
            threading.Thread(target=self.server.shutdown, daemon=True).start()
        except (AuthorizationError, FileExistsError, OSError):
            self.reply(400, 'Não foi possível concluir. Volte ao Codex para tentar novamente.')

    def reply(self, status, text):
        body = text.encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'text/plain; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--url-file', required=True)
    args = parser.parse_args()
    try:
        client = json.loads(pathlib.Path(args.client).read_text())['installed']
        if not all(isinstance(client.get(k), str) and client[k] for k in ('client_id', 'client_secret')):
            raise AuthorizationError('Invalid desktop client')
        if pathlib.Path(args.output).exists():
            raise AuthorizationError('Credential output already exists')
        server = HTTPServer(('127.0.0.1', 0), CallbackHandler)
        server.target, server.success = args.output, False
        server.session = OAuthSession(client, 'http://127.0.0.1:' + str(server.server_port) + '/oauth2callback')
        save_credentials(args.url_file, {'url': server.session.authorization_url()})
        print('Read-only authorization is ready in the local URL file.', flush=True)
        timer = threading.Timer(1800, server.shutdown)
        timer.daemon = True
        timer.start()
        try:
            server.serve_forever(poll_interval=0.25)
        finally:
            timer.cancel()
            server.server_close()
        print('Read-only credential saved.' if server.success else 'Authorization timed out.', flush=True)
        return 0 if server.success else 1
    except (OSError, ValueError, KeyError, TypeError, AuthorizationError):
        print('Could not prepare read-only authorization.', flush=True)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
