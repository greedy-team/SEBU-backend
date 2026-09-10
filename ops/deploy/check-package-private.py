#!/usr/bin/env python3
"""CI guard: never publish application/SQL layers into an existing public package."""
import argparse
import json
import os
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--allow-missing', action='store_true')
args = parser.parse_args()
request = urllib.request.Request(
    'https://api.github.com/orgs/greedy-team/packages/container/sebu-backend',
    headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
             'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'})
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        package = json.load(response)
except urllib.error.HTTPError as error:
    if error.code == 404 and args.allow_missing:
        print('No accessible existing package; first publish creates a private package by default.')
        raise SystemExit(0)
    raise SystemExit(f'Cannot verify package privacy (HTTP {error.code}); publishing stopped.')
if package.get('visibility') != 'private':
    raise SystemExit('Package is not private. STOP: use a reviewed private package, never change visibility here.')
print('Verified: GHCR package visibility is private.')
