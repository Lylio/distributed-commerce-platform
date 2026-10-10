#!/usr/bin/env python3
"""Verify real deployed telemetry, SSE resume, and order outcomes without resetting data."""
import argparse
import json
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import URLError

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', default='http://127.0.0.1:5173')
parser.add_argument('--force-failure', action='store_true')
args = parser.parse_args()


def request(path, body=None):
    req = Request(args.url + path, data=None if body is None else json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
    with urlopen(req, timeout=15) as response:
        return json.load(response)


def wait(check):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            value = check()
        except URLError:
            value = None
        if value:
            return value
        time.sleep(.5)
    raise AssertionError('Timed out waiting for real lifecycle evidence')


def stock(product):
    return next(i for i in request('/api/inventory') if i['productId'] == product)


def stream_batch(last=None):
    headers = {} if last is None else {'Last-Event-ID': str(last)}
    with urlopen(Request(args.url + '/api/monitoring/stream', headers=headers), timeout=15) as response:
        event = None
        while True:
            line = response.readline().decode().strip()
            if line.startswith('event:'):
                event = line[6:].strip()
            if line.startswith('data:') and event in ('snapshot', 'events'):
                return event, json.loads(line[5:])


health = wait(lambda: request('/api/monitoring/health'))
assert len(health) == 4 and all(h['state'] == 'healthy' for h in health), health
print('PASS: real Actuator and Kafka metadata health checks', flush=True)
initial = request('/api/monitoring/events?limit=5000')
cursor = initial[-1]['sequence'] if initial else 0
product = max(request('/api/inventory'), key=lambda i: i['availableQuantity'])['productId']
observed = set()
for _ in range(100):
    before = stock(product)
    assert before['availableQuantity'] > 0
    order = request('/api/orders', {'customerId': str(uuid.uuid4()), 'items': [{'productId': product, 'quantity': 1}]})['id']
    def finished():
        detail = request(f'/api/orders/{order}/details')
        return detail if detail['order']['status'] in ('CONFIRMED', 'CANCELLED') else None
    detail = wait(finished)
    outcome = detail['paymentStatus']
    assert outcome in ('SUCCEEDED', 'FAILED'), detail
    if args.force_failure:
        assert outcome == 'FAILED', detail
    def journey_complete():
        events = request(f'/api/monitoring/events?orderId={order}&limit=5000')
        stages = {e['stage'] for e in events}
        types = {e['type'] for e in events}
        terminal = 'OrderConfirmed' if outcome == 'SUCCEEDED' else 'OrderCancelled'
        if {'OUTBOX_INSERTED', 'PUBLISHED', 'KAFKA_OBSERVED', 'PROCESSED', 'STATE_COMMITTED'} <= stages and terminal in types and (outcome != 'FAILED' or 'InventoryReleased' in types):
            return events
    events = wait(journey_complete)
    assert all(e['orderId'] == order for e in events)
    assert len({e['sequence'] for e in events}) == len(events)
    assert events == sorted(events, key=lambda e: e['sequence'])
    assert any(e['type'] == 'PaymentResultEvent' and e['status'] == outcome for e in events)
    assert any(e['destination'] == 'payment-service' and e['stage'] == 'PROCESSED' for e in events)
    if outcome == 'FAILED':
        wait(lambda: stock(product) == before)
        assert any(e['type'] == 'InventoryReleased' and e['stage'] == 'COMPENSATION_COMMITTED' for e in events)
    else:
        assert stock(product)['availableQuantity'] == before['availableQuantity'] - 1
    filtered = request(f'/api/monitoring/events?orderId={order}&type=PaymentResultEvent&service=payment-service')
    assert filtered and all(e['type'] == 'PaymentResultEvent' for e in filtered)
    assert all('customerId' not in e and 'items' not in e for e in events)
    observed.add(outcome)
    print(f'PASS: {outcome} correlated lifecycle, distinct stages, history, filtering; order {order}', flush=True)
    if args.force_failure or len(observed) == 2:
        break
assert args.force_failure or len(observed) == 2
name, replay = stream_batch(cursor if cursor else None)
assert replay and (not cursor or all(e['sequence'] > cursor for e in replay)), replay
name, resumed = stream_batch(replay[-1]['sequence']) if len(replay) == 200 else ('events', [])
assert not resumed or all(e['sequence'] > replay[-1]['sequence'] for e in resumed)
print('PASS: SSE delivers retained updates after Last-Event-ID without replaying acknowledged observations', flush=True)
if not args.force_failure:
    before = stock(product)
    order = request('/api/orders', {'customerId': str(uuid.uuid4()), 'items': [{'productId': product, 'quantity': before['availableQuantity'] + 1}]})['id']
    events = wait(lambda: (lambda es: es if any(e['type'] == 'InventoryRejectedEvent' and e['stage'] == 'KAFKA_OBSERVED' for e in es) and any(e['type'] == 'OrderCancelled' for e in es) else None)(request(f'/api/monitoring/events?orderId={order}')))
    assert not any(e['type'] == 'InventoryReleased' for e in events)
    assert stock(product) == before
    print('PASS: real inventory rejection and cancellation without invented compensation', flush=True)
