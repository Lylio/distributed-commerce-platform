#!/usr/bin/env python3
"""Smoke test a real Compose deployment without resetting data (requires AUTO payments)."""
import argparse
import json
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:5173')
    args = parser.parse_args()

    def request(path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = Request(args.url + path, data=data, headers={'Content-Type': 'application/json'})
        with urlopen(req, timeout=15) as response:
            return json.load(response)

    def stock(product):
        return next(item for item in request('/api/inventory') if item['productId'] == product)

    def wait_for(check, description):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            result = check()
            if result:
                return result
            time.sleep(.5)
        raise AssertionError('Timed out: ' + description)

    def place(product, quantity=1):
        created = request('/api/orders', {'customerId': str(uuid.uuid4()),
                          'items': [{'productId': product, 'quantity': quantity}]})
        assert created['status'] == 'PENDING', created
        order_id = created['id']
        def terminal():
            detail = request(f'/api/orders/{order_id}/details')
            return detail if detail['order']['status'] in ('CONFIRMED', 'CANCELLED') else None
        return wait_for(terminal, f'order {order_id}')

    products = request('/api/products')
    assert len(products) == 6, products
    inventory = request('/api/inventory')
    assert {p['id'] for p in products} <= {i['productId'] for i in inventory}
    with urlopen(args.url + '/orders', timeout=15) as response:
        assert b'<div id="root"' in response.read(), 'SPA fallback failed'
    print('PASS: catalogue, seeded inventory, Nginx API routes and SPA fallback', flush=True)

    product = max(inventory, key=lambda i: i['availableQuantity'])['productId']
    assert stock(product)['availableQuantity'] > 0, 'No stock for smoke tests'
    observed = set()
    for _ in range(100):
        before = stock(product)
        assert before['availableQuantity'] > 0, 'Stock exhausted before observing both AUTO outcomes'
        detail = place(product)
        if detail['paymentStatus'] == 'FAILED':
            assert detail['order']['status'] == 'CANCELLED', detail
            wait_for(lambda: stock(product) == before, 'payment failure inventory compensation')
            observed.add('FAILED')
            print('PASS: failed payment, cancellation and exact inventory compensation', flush=True)
        else:
            assert detail['paymentStatus'] == 'SUCCEEDED', detail
            assert detail['order']['status'] == 'CONFIRMED', detail
            after = stock(product)
            assert after['availableQuantity'] == before['availableQuantity'] - 1, (before, after)
            assert after['reservedQuantity'] == before['reservedQuantity'] + 1, (before, after)
            observed.add('SUCCEEDED')
            print('PASS: successful payment and stock reservation', flush=True)
        statuses = [t['status'] for t in detail['transitions']]
        assert statuses[0] == 'PENDING' and statuses[-1] == detail['order']['status'], detail
        if len(observed) == 2:
            break
    assert len(observed) == 2, 'Expected AUTO mode to produce both outcomes; check Payment environment'

    before = stock(product)
    detail = place(product, before['availableQuantity'] + 1)
    assert detail['order']['status'] == 'CANCELLED' and detail['paymentStatus'] == 'NOT_REQUESTED', detail
    assert stock(product) == before, 'Inventory rejection changed stock'
    print('PASS: inventory rejection with no payment or stock loss', flush=True)
    try:
        request('/api/orders', {'customerId': str(uuid.uuid4()), 'items': [{'productId': product, 'quantity': 0}]})
    except HTTPError as error:
        assert error.code == 400, error
    else:
        raise AssertionError('Invalid quantity was accepted')
    print('PASS: checkout validation and persisted order history', flush=True)


if __name__ == '__main__':
    main()
