#!/usr/bin/env python3
"""Check a real deployed service's monitored health (e.g. after Compose stop/start)."""
import argparse
import json
import time
from urllib.request import urlopen
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', default='http://127.0.0.1:5173')
parser.add_argument('--service', default='payment-service')
parser.add_argument('--expect', choices=['healthy', 'degraded', 'unavailable', 'unknown'], required=True)
args = parser.parse_args()
deadline = time.monotonic() + 45
while time.monotonic() < deadline:
    with urlopen(args.url + '/api/monitoring/health', timeout=5) as response:
        health = json.load(response)
    service = next((h for h in health if h['service'] == args.service), None)
    if service and service['state'] == args.expect:
        assert all(h['state'] == 'healthy' for h in health if h['service'] != args.service), health
        print(f'PASS: actual {args.service} health changed to {args.expect}: {service["detail"]}', flush=True)
        break
    time.sleep(1)
else:
    raise AssertionError(f'{args.service} did not become {args.expect}: {health}')
