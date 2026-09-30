#!/usr/bin/env python3
"""Authorize only FoodRun's sender. Save secrets locally; never print tokens or send mail."""
import argparse
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import secrets
import sys
import time
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen
import webbrowser

SENDER = 'foodruncollection@gmail.com'
SCOPE = 'https://www.googleapis.com/auth/gmail.send'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, default=Path('.local/gmail-oauth-client.json'))
    parser.add_argument('--output', type=Path, default=Path('.local/gmail.env'))
    parser.add_argument('--web-port', type=int, default=8765,
                        help='Local callback port for Web application clients (default: 8765). Register the exact redirect URI in Google Cloud first.')
    args = parser.parse_args()
    if args.output.exists():
        sys.exit('Output already exists. Choose a new --output path to preserve existing credentials.')
    document = json.loads(args.client.read_text())
    web_client = 'installed' not in document and 'web' in document
    client = document.get('web' if web_client else 'installed', {})
    if not client.get('client_id') or not client.get('client_secret'):
        sys.exit('Download a Desktop app or Web application OAuth client JSON from Google Cloud first.')
    if not 1 <= args.web_port <= 65535:
        sys.exit('The web callback port must be between 1 and 65535.')
    state, verifier = secrets.token_urlsafe(32), secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    result = {}

    class Callback(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Callback URLs contain authorization codes.

        def do_GET(self):
            parsed = urlsplit(self.path)
            query = parse_qs(parsed.query)
            valid = parsed.path == '/' and secrets.compare_digest(query.get('state', [''])[0], state)
            self.send_response(200 if valid else 400)
            self.send_header('Content-Type', 'text/plain; charset=utf-8')
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            if valid:
                result.update(code=query.get('code', [''])[0], error=query.get('error', [''])[0])
            self.wfile.write(b'You can return to Terminal to finish FoodRun email setup.' if valid else b'Invalid authorization callback.')

    with HTTPServer(('127.0.0.1', args.web_port if web_client else 0), Callback) as server:
        server.timeout = 1
        redirect = f'http://127.0.0.1:{server.server_port}/'
        if web_client:
            print(f'This Web client must have {redirect} registered as an authorized redirect URI in Google Cloud.', flush=True)
        url = 'https://accounts.google.com/o/oauth2/v2/auth?' + urlencode({
            'client_id': client['client_id'], 'redirect_uri': redirect, 'response_type': 'code',
            'scope': f'openid email {SCOPE}', 'state': state, 'code_challenge': challenge,
            'code_challenge_method': 'S256', 'access_type': 'offline', 'prompt': 'consent', 'login_hint': SENDER,
        })
        print(f'Authorize {SENDER} in your browser. This does not send any emails.', flush=True)
        if not webbrowser.open(url):
            sys.exit('Could not open the browser. Run this helper on your desktop.')
        deadline = time.monotonic() + 300
        while not result and time.monotonic() < deadline:
            server.handle_request()
    if not result.get('code') or result.get('error'):
        sys.exit('Authorization was declined or timed out. No credentials saved.')
    request = Request('https://oauth2.googleapis.com/token', data=urlencode({
        'client_id': client['client_id'], 'client_secret': client['client_secret'],
        'code': result['code'], 'code_verifier': verifier, 'grant_type': 'authorization_code', 'redirect_uri': redirect,
    }).encode(), headers={'Content-Type': 'application/x-www-form-urlencoded'})
    with urlopen(request, timeout=20) as response:
        token = json.load(response)
    if SCOPE not in token.get('scope', '').split():
        sys.exit('Google did not grant the Gmail send permission. Retry and select "Send email on your behalf" on the consent screen. No credentials saved.')
    if not token.get('refresh_token'):
        sys.exit('Google granted sending but did not return an offline refresh token. Reauthorize the sender with offline access. No credentials saved.')
    with urlopen(Request('https://openidconnect.googleapis.com/v1/userinfo',
                        headers={'Authorization': 'Bearer ' + token['access_token']}), timeout=20) as response:
        identity = json.load(response)
    if identity.get('email', '').lower() != SENDER or identity.get('email_verified') is not True:
        sys.exit('The authorized account is not the verified FoodRun sender. No credentials saved.')
    values = {'FOODRUN_GMAIL_CLIENT_ID': client['client_id'], 'FOODRUN_GMAIL_CLIENT_SECRET': client['client_secret'],
              'FOODRUN_GMAIL_REFRESH_TOKEN': token['refresh_token']}
    if any('\n' in value or '\r' in value for value in values.values()):
        sys.exit('Invalid credential format. No credentials saved.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with os.fdopen(os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as output:
        output.write(''.join(f'{key}={value}\n' for key, value in values.items()))
    print('Sender verified. Credentials saved privately. Import this file into Render environment settings; do not commit or share it.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError):
        sys.exit('Setup failed. Check the client file, Gmail API configuration and network. Credential details were not logged.')
