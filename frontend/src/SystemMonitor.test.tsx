import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { SystemMonitor } from './SystemMonitor';
class Stream {
  static instance: Stream;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  handlers = new Map<string, (event: MessageEvent) => void>();
  close = vi.fn();
  constructor(public url: string) { Stream.instance = this; }
  addEventListener(name: string, handler: (event: MessageEvent) => void) { this.handlers.set(name, handler); }
  send(name: string, value: unknown) { this.handlers.get(name)?.({ data: JSON.stringify(value) } as MessageEvent); }
}
const event = { sequence: 1, key: 'test', orderId: '11111111-1111-1111-1111-111111111111', type: 'PaymentResultEvent', source: 'payment-service', destination: 'kafka', stage: 'KAFKA_OBSERVED', status: 'FAILED', occurredAt: new Date().toISOString(), observedAt: new Date().toISOString(), eventId: 'e', topic: 'payment-result', partition: 0, offset: 4 };
beforeEach(() => { vi.stubGlobal('EventSource', Stream); vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [event] })); });
afterEach(() => vi.unstubAllGlobals());
it('keeps history through reconnection, deduplicates replay and shows actual health failure', async () => {
  const rendered = render(<SystemMonitor/>);
  expect(screen.getByText('The next order starts a story.')).toBeVisible();
  act(() => { Stream.instance.onopen?.(); Stream.instance.send('snapshot', [event]); });
  expect(screen.getByText('Live')).toBeVisible();
  expect(screen.getByText('Published · observed in Kafka')).toBeVisible();
  expect(screen.getByText(/consumer unverified/)).toBeVisible();
  act(() => { Stream.instance.onerror?.(); Stream.instance.send('events', [event]); Stream.instance.send('health', [{ service: 'payment-service', state: 'unavailable', detail: 'Connection refused', checkedAt: new Date().toISOString() }]); });
  expect(screen.getByText('Reconnecting')).toBeVisible();
  expect(screen.getByText('1 / 1 observations')).toBeVisible();
  expect(screen.getByText('Connection refused')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Service'), { target: { value: 'inventory-service' } });
  expect(screen.getByText('No matching observations.')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Service'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Order ID'), { target: { value: event.orderId } });
  await waitFor(() => expect(fetch).toHaveBeenCalled());
  expect(screen.getByText('1 / 1 observations')).toBeVisible();
  rendered.unmount(); expect(Stream.instance.close).toHaveBeenCalledOnce();
});
it('shows compensation only after a committed release is observed', () => {
  render(<SystemMonitor/>);
  act(() => Stream.instance.send('snapshot', [{ ...event, type: 'OrderCancelled', status: 'CANCELLED', stage: 'STATE_COMMITTED', source: 'order-service', destination: '' }]));
  fireEvent.change(screen.getByLabelText('Order ID'), { target: { value: event.orderId } });
  expect(screen.getByText(/No completed stock release observed/)).toBeVisible();
  act(() => Stream.instance.send('events', [{ ...event, sequence: 2, type: 'InventoryReleased', status: 'RELEASED', stage: 'COMPENSATION_COMMITTED', source: 'kafka', destination: 'inventory-service' }]));
  expect(screen.getByText('Inventory release committed. Reserved stock was restored.')).toBeVisible();
});

it('closes an offline stream and resumes from the last observed cursor on return online', () => {
  render(<SystemMonitor/>);
  act(() => Stream.instance.send('snapshot', [event]));
  const previous = Stream.instance;
  act(() => window.dispatchEvent(new Event('offline')));
  expect(previous.close).toHaveBeenCalledOnce();
  expect(screen.getByText('Reconnecting')).toBeVisible();
  act(() => window.dispatchEvent(new Event('online')));
  expect(Stream.instance.url).toBe('/api/monitoring/stream?since=1');
  act(() => { Stream.instance.onopen?.(); Stream.instance.send('events', [event]); });
  expect(screen.getByText('1 / 1 observations')).toBeVisible();
});
