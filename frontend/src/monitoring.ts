export type MonitorEvent = {
  sequence: number; key: string; orderId: string; type: string; source: string; destination: string;
  stage: string; status: string; occurredAt: string; observedAt: string; eventId: string | null;
  topic: string | null; partition: number | null; offset: number | null; historical?: boolean;
};
export type Health = { service: string; state: 'healthy' | 'degraded' | 'unavailable' | 'unknown'; detail: string; checkedAt: string };
export const services = ['order-service', 'inventory-service', 'payment-service', 'kafka'];
export const serviceName = (id: string) => ({ 'order-service': 'Order Service', 'inventory-service': 'Inventory Service', 'payment-service': 'Payment Service', kafka: 'Kafka' })[id] || id;
export const stageName = (stage: string) => ({ OUTBOX_INSERTED: 'Outbox inserted', PUBLISHED: 'Published · broker acknowledged', KAFKA_OBSERVED: 'Published · observed in Kafka', PROCESSED: 'Processing outcome committed', STATE_COMMITTED: 'Order state committed', COMPENSATION_COMMITTED: 'Stock release committed' })[stage] || stage;
export function mergeEvents(current: MonitorEvent[], incoming: MonitorEvent[], historical: boolean): MonitorEvent[] {
  const byId = new Map(current.map(event => [event.sequence, event]));
  for (const event of incoming) if (!byId.has(event.sequence)) byId.set(event.sequence, { ...event, historical: event.historical ?? historical });
  return [...byId.values()].sort((a, b) => a.sequence - b.sequence).slice(-5000);
}
export function matches(event: MonitorEvent, order: string, type: string, service: string) {
  return event.orderId.toLowerCase().includes(order.trim().toLowerCase()) && (!type || event.type === type) && (!service || event.source === service || event.destination === service);
}
export function communication(event: MonitorEvent): [string, string] | null {
  if (['PUBLISHED', 'KAFKA_OBSERVED', 'PROCESSED', 'COMPENSATION_COMMITTED'].includes(event.stage) && event.destination) return [event.source, event.destination];
  return null;
}
