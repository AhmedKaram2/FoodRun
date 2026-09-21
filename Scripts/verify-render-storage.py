#!/usr/bin/env python3
"""Read-only deployment preflight. Never print API keys or environment values."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('service_id')
args = parser.parse_args()
if not re.fullmatch(r'srv-[a-z0-9]+', args.service_id):
    sys.exit('Invalid Render service ID.')
key = os.environ.get('RENDER_API_KEY')
if not key:
    config = (Path.home() / '.render/cli.yaml').read_text()
    match = re.search(r'^\s+key:\s*(.+)$', config, re.M)
    if not match:
        sys.exit('Sign in to the Render CLI first.')
    key = match.group(1).strip('\"\'')

def get(path):
    try:
        req = urllib.request.Request('https://api.render.com/v1/' + path,
                                     headers={'Authorization': 'Bearer ' + key})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        sys.exit(f'Render read failed ({error.code}); deployment is not cleared.')

service = get('services/' + args.service_id)
if service.get('serviceDetails', {}).get('plan') in (None, 'free'):
    sys.exit('STOP: FoodRun SQLite cannot survive deployments on the Free plan. Configure a paid instance and persistent disk first.')
# Fail closed if pagination prevents proving the required disk is present.
disks = get('disks?limit=100')
matched = [value.get('disk', value) for value in disks if value.get('disk', value).get('serviceId') == args.service_id]
envs = get('services/' + args.service_id + '/env-vars?limit=100')
variables = {value['envVar']['key']: value['envVar']['value'] for value in envs}
data_path = variables.get('FOODRUN_DATA', '')
if not data_path.startswith('/') or not any(data_path == disk.get('mountPath') or data_path.startswith(disk.get('mountPath', '').rstrip('/') + '/') for disk in matched if disk.get('mountPath')):
    sys.exit('STOP: FOODRUN_DATA is not covered by a persistent disk. Deployment is not cleared.')
print('PASS: paid instance and persistent disk cover FOODRUN_DATA. Back up the database and encryption key before deploying.')
