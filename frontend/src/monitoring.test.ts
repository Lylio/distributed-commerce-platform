import { expect, it } from 'vitest';
import { communication, matches, mergeEvents } from './monitoring';
import type { MonitorEvent } from './monitoring';
const event: MonitorEvent = { sequence: 1, key: 'a', orderId: 'order-a', type: 'PaymentResultEvent', source: 'payment-service', destination: 'kafka', stage: 'KAFKA_OBSERVED', status: 'FAILED', occurredAt: '2026-10-10T10:00:00Z', observedAt: '2026-10-10T10:00:01Z', eventId: 'e', topic: 'payment-result', partition: 0, offset: 3 };
it('deduplicates replay while preserving history and ordering distinct processing stages', () => {
  const history = mergeEvents([], [event], true);
  const merged = mergeEvents(history, [{ ...event, sequence: 2, stage: 'PROCESSED' }, event], false);
  expect(merged.map(e => e.sequence)).toEqual([1, 2]);
  expect(merged[0].historical).toBe(true);
  expect(merged[1].historical).toBe(false);
});
it('filters by correlated order, actual event name and either endpoint', () => {
  expect(matches(event, 'ORDER-A', 'PaymentResultEvent', 'kafka')).toBe(true);
  expect(matches(event, 'order-b', '', '')).toBe(false);
  expect(matches(event, '', 'OrderCancelledEvent', '')).toBe(false);
  expect(matches(event, '', '', 'inventory-service')).toBe(false);
});
it('does not animate outbox insertion or claim delivery to a consumer from publication', () => {
  expect(communication({ ...event, stage: 'OUTBOX_INSERTED', destination: '' })).toBeNull();
  expect(communication(event)).toEqual(['payment-service', 'kafka']);
  expect(communication({ ...event, stage: 'PROCESSED', source: 'kafka', destination: 'order-service' })).toEqual(['kafka', 'order-service']);
});
it('bounds the display and preserves explicitly historical late evidence', () => {
  const result = mergeEvents([], Array.from({ length: 5001 }, (_, i) => ({ ...event, sequence: i + 1, historical: true })), false);
  expect(result).toHaveLength(5000);
  expect(result[0].sequence).toBe(2);
  expect(result.at(-1)?.historical).toBe(true);
});
