import { useEffect, useRef, useState } from 'react';
import { communication, matches, mergeEvents, serviceName, services, stageName } from './monitoring';
import type { Health, MonitorEvent } from './monitoring';

const positions: Record<string, [number, number]> = { 'order-service': [170, 95], kafka: [500, 185], 'inventory-service': [830, 95], 'payment-service': [830, 285] };
const time = (value: string) => new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 });
export function SystemMonitor() {
  const [events, setEvents] = useState<MonitorEvent[]>([]);
  const [health, setHealth] = useState<Health[]>([]);
  const [connection, setConnection] = useState('Connecting');
  const [order, setOrder] = useState('');
  const [type, setType] = useState('');
  const [service, setService] = useState('');
  const [selected, setSelected] = useState<MonitorEvent | null>(null);
  const [activity, setActivity] = useState<MonitorEvent[]>([]);
  const [traceError, setTraceError] = useState('');
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 5000); return () => clearInterval(timer); }, []);
  const mountedAt = useRef(Date.now());
  useEffect(() => {
    let stream: EventSource | undefined;
    let cursor = 0;
    let lastMessage = Date.now();
    let animation: ReturnType<typeof setTimeout>;
    let stopped = false;
    function connect() {
      if (stopped || !navigator.onLine) { setConnection('Reconnecting'); return; }
      stream?.close();
      lastMessage = Date.now();
      stream = new EventSource(`/api/monitoring/stream${cursor ? `?since=${cursor}` : ''}`);
      stream.onopen = () => setConnection('Live');
      stream.onerror = () => setConnection('Reconnecting');
      function receive(message: Event, historical: boolean) {
        try {
          const incoming = JSON.parse((message as MessageEvent).data) as MonitorEvent[];
          const id = Number((message as MessageEvent).lastEventId);
          cursor = id || incoming.at(-1)?.sequence || cursor;
          lastMessage = Date.now();
          setEvents(current => mergeEvents(current, incoming.map(event => ({ ...event, historical: historical || new Date(event.occurredAt).getTime() < mountedAt.current })), historical));
          const live = historical ? [] : incoming.filter(event => new Date(event.occurredAt).getTime() >= mountedAt.current);
          if (live.length) { setActivity(live); clearTimeout(animation); animation = setTimeout(() => setActivity([]), 2800); }
        } catch { setConnection('Invalid telemetry'); }
      }
      stream.addEventListener('snapshot', message => receive(message, true));
      stream.addEventListener('events', message => receive(message, false));
      stream.addEventListener('health', message => {
        try { setHealth(JSON.parse((message as MessageEvent).data) as Health[]); lastMessage = Date.now(); }
        catch { /* Keep the last known check. */ }
      });
    }
    const offline = () => { stream?.close(); setConnection('Reconnecting'); };
    window.addEventListener('offline', offline);
    window.addEventListener('online', connect);
    // Some network failures leave a TCP stream open. Reopen using its last cursor.
    const watchdog = setInterval(() => {
      if (navigator.onLine && Date.now() - lastMessage > 15000) { setConnection('Reconnecting'); connect(); }
    }, 5000);
    connect();
    return () => { stopped = true; stream?.close(); clearTimeout(animation); clearInterval(watchdog); window.removeEventListener('offline', offline); window.removeEventListener('online', connect); };
  }, []);
  useEffect(() => {
    setTraceError('');
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(order.trim())) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      void fetch(`/api/monitoring/events?orderId=${encodeURIComponent(order.trim())}&limit=5000`, { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(12000)]) })
        .then(response => { if (!response.ok) throw new Error(); return response.json() as Promise<MonitorEvent[]>; })
        .then(history => setEvents(current => mergeEvents(current, history, true)))
        .catch(() => { if (!controller.signal.aborted) setTraceError('Order history could not be loaded. Live updates will continue; reselect the order to retry.'); });
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [order]);
  const visible = events.filter(event => matches(event, order, type, service));
  const orderIds = [...new Set(events.map(event => event.orderId))].reverse();
  const active = selected ? [selected] : activity;
  const latest = active.at(-1);
  const selectedOrder = orderIds.find(id => id === order.trim());
  const trace = selectedOrder ? events.filter(event => event.orderId === selectedOrder) : [];
  const released = trace.some(event => event.stage === 'COMPENSATION_COMMITTED');
  const cancelled = trace.some(event => event.stage === 'STATE_COMMITTED' && event.status === 'CANCELLED');
  const confirmed = trace.some(event => event.stage === 'STATE_COMMITTED' && event.status === 'CONFIRMED');
  return <main className="page monitor-page">
    <div className="section-heading"><div><span className="eyebrow">Behind every considered purchase</span><h1>System monitor<span>.</span></h1><p>Follow real orders through the services that make them happen.</p></div><div className={`monitor-connection ${connection === 'Live' ? 'connected' : ''}`} role="status"><i/>{connection}<small>{connection === 'Live' ? 'Receiving server updates' : 'History remains available'}</small></div></div>
    <section className="monitor-panel" aria-label="Microservices architecture"><div className="monitor-panel-heading"><div><span className="eyebrow">01 / The architecture</span><h2>A connected system.</h2></div><button className="text-button" onClick={() => { setSelected(null); setService(''); }}>Clear selection ↗</button></div>
      <div className="architecture"><svg viewBox="0 0 1000 385" role="img" aria-label="Order, Inventory and Payment services communicate through Kafka"><defs><marker id="monitor-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor"/></marker></defs>{['order-service', 'inventory-service', 'payment-service'].map(id => {
        const [x, y] = positions[id]; const highlighted = active.some(event => communication(event)?.includes(id));
        return <path key={id} className={`architecture-path ${highlighted ? 'path-active' : ''}`} d={`M ${x} ${y} L 500 185`} />;
      })}{active.map(event => { const route = communication(event); if (!route) return null; const start = positions[route[0]], end = positions[route[1]]; if (!start || !end) return null; return <path key={event.sequence} className={`architecture-flow ${event.status === 'FAILED' || event.type.includes('Cancelled') ? 'flow-failed' : ''}`} d={`M ${start[0]} ${start[1]} L ${end[0]} ${end[1]}`} markerEnd="url(#monitor-arrow)"/>; })}</svg>
      {services.map(id => { const [x, y] = positions[id]; const check = health.find(item => item.service === id); const state = check && now - Date.parse(check.checkedAt) < 30000 ? check.state : 'unknown'; const highlighted = active.some(event => event.source === id || event.destination === id); return <button key={id} style={{ left: `${x / 10}%`, top: `${y / 3.85}%` }} className={`architecture-node ${id === 'kafka' ? 'kafka-node' : ''} ${highlighted ? 'node-active' : ''}`} onClick={() => setService(service === id ? '' : id)} aria-pressed={service === id}><span className="eyebrow">{id === 'kafka' ? 'Event broker' : 'Spring Boot · Java 21'}</span><strong>{serviceName(id)}</strong><span className={`health-state health-${state}`} title={check?.detail}><i/>{state}</span></button>; })}</div>
      <div className="architecture-caption">{latest ? <><strong>{latest.type}</strong><span>{serviceName(latest.source)}{latest.destination ? ` → ${serviceName(latest.destination)}` : ''} · {stageName(latest.stage)}</span></> : <><strong>Ready to observe.</strong><span>Place an order in the Collection, or select a recorded event below.</span></>}</div>
      <p className="monitor-note">Paths animate only for observed publication or committed processing. Published messages do not imply consumer acknowledgement.</p>
    </section>
    <div className="monitor-health-grid">{services.map(id => { const check = health.find(item => item.service === id); const stale = !check || now - Date.parse(check.checkedAt) >= 30000; return <div key={id}><span className="eyebrow">{serviceName(id)}</span><span className={`health-state health-${stale ? 'unknown' : check.state}`}><i/>{stale ? 'unknown' : check.state}</span><p>{stale ? 'Awaiting a current health check' : check.detail}</p><small>{check ? `Checked ${time(check.checkedAt)}` : 'Checks run every 10 seconds'}</small></div>; })}</div>
    <section className="monitor-panel" aria-label="Event history"><div className="monitor-panel-heading"><div><span className="eyebrow">02 / Event journey</span><h2>Every step, in view.</h2></div><span className="monitor-count">{visible.length} / {events.length} observations</span></div>
      <div className="monitor-filters"><label>Order ID<input list="monitor-orders" placeholder="Select or paste an order ID" value={order} onChange={event => { setOrder(event.target.value); setSelected(null); }}/><datalist id="monitor-orders">{orderIds.map(id => <option key={id} value={id}/>)}</datalist></label><label>Event type<select value={type} onChange={event => setType(event.target.value)}><option value="">All event types</option>{[...new Set(events.map(event => event.type))].sort().map(name => <option key={name}>{name}</option>)}</select></label><label>Service<select value={service} onChange={event => setService(event.target.value)}><option value="">All services</option>{services.map(id => <option key={id} value={id}>{serviceName(id)}</option>)}</select></label><button className="text-button" onClick={() => { setOrder(''); setType(''); setService(''); setSelected(null); }}>Reset filters</button></div>
      {traceError && <p role="alert" className="monitor-warning">{traceError}</p>}
      {selectedOrder && <div className={`trace-summary ${cancelled ? 'trace-cancelled' : ''}`}><div><span className="eyebrow">Selected order</span><strong>{confirmed ? 'Order confirmed' : cancelled ? 'Order cancelled' : 'Journey in progress'}</strong><code>{selectedOrder}</code></div><p>{released ? 'Inventory release committed. Reserved stock was restored.' : cancelled ? 'No completed stock release observed. Inventory rejection may not require compensation.' : 'The timeline shows the committed evidence available for this order.'}</p><a className="text-button" href={`#/orders/${selectedOrder}`}>View order ↗</a></div>}
      <p className="monitor-note">Shown in monitor observation order, not a global Kafka order. Event time comes from the originating record; only offsets within one topic partition establish Kafka ordering. Recent history loads first; selecting a full order ID loads its retained journey.</p>
      {!visible.length ? <div className="empty-state"><h3>{events.length ? 'No matching observations.' : 'The next order starts a story.'}</h3><p>{events.length ? 'Try another order, event type or service.' : 'Place an order to see publication, processing and compensation appear here.'}</p><a className="text-button" href="#/shop">Explore the collection ↗</a></div> : <div className="monitor-table-wrap"><table className="monitor-table"><thead><tr><th>Event time / observation</th><th>Event / status</th><th>Communication</th><th>Stage</th><th>Order</th></tr></thead><tbody>{[...visible].reverse().map(event => <tr key={event.sequence} className={selected?.sequence === event.sequence ? 'selected-event' : ''}><td><button onClick={() => setSelected(event)} aria-label={`Inspect observation ${event.sequence}`}>{time(event.occurredAt)}<small>#{event.sequence} · {event.historical ? 'History' : 'Live'}</small></button></td><td><strong>{event.type}</strong><span className={`event-status ${['FAILED', 'CANCELLED', 'REJECTED'].includes(event.status) || event.type.includes('Rejected') ? 'event-failure' : ''}`}>{event.status}</span></td><td>{serviceName(event.source)}<small>{event.destination ? `→ ${serviceName(event.destination)}` : 'Local committed record'}{['PUBLISHED', 'KAFKA_OBSERVED'].includes(event.stage) ? ' · consumer unverified' : ''}</small></td><td>{stageName(event.stage)}{event.topic && <small>{event.topic} · p{event.partition} / offset {event.offset}</small>}</td><td><button className="monitor-order-id" title={event.orderId} onClick={() => { setOrder(event.orderId); setSelected(event); }}>{event.orderId.slice(0, 8)}… ↗</button></td></tr>)}</tbody></table></div>}
      {selected && <div className="monitor-event-detail"><strong>{selected.type} · observation #{selected.sequence}</strong><p>{stageName(selected.stage)} · {serviceName(selected.source)}{selected.destination ? ` → ${serviceName(selected.destination)}` : ''}</p><code>Order {selected.orderId}</code><code>Event {selected.eventId || 'No message ID recorded for this state evidence'}</code><p>Event time {new Date(selected.occurredAt).toLocaleString()} · Observed {new Date(selected.observedAt).toLocaleString()}</p>{selected.topic && <p>{selected.topic} / partition {selected.partition} / offset {selected.offset}</p>}<button className="text-button" onClick={() => setSelected(null)}>Close detail</button></div>}
      <p className="monitor-note">History retains up to seven days and approximately 20,000 observations. This view holds up to 5,000 observations. State evidence is read after commit; InventoryReleased is a completed operation, not a new Kafka event.</p>
    </section>
  </main>;
}
