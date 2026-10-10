# Live Microservices Monitoring Dashboard

## Architecture and evidence flow

The existing `form.` storefront now includes a System Monitor route (`#/monitor`).
It preserves catalogue, checkout, order history and receipt functionality. The page
includes an interactive architecture diagram, genuine event-path animation,
service health, searchable order tracing, event/service filters, timestamped
history, selected-event details and honest success/failure/compensation states.
Motion respects `prefers-reduced-motion` and the event table scrolls within its
panel on mobile.

Order Service hosts the lightweight monitoring API and stores sanitized telemetry
in its existing PostgreSQL database via additive Flyway migration V6. No extra
infrastructure or database volumes were introduced. Business outbox publishing,
idempotency, saga logic, migrations and Kafka groups were preserved.

There are two independent evidence sources:

1. A dedicated raw-string Kafka consumer (`system-monitor-v1`) observes the actual
   `order-created`, `inventory-reserved`, `inventory-rejected`, `payment-result`
   and `order-cancelled` topics. It persists record coordinates before committing
   its own consumer offsets. Database failures retry only this monitoring group.
   It never changes business consumer offsets or claims their acknowledgements.
2. Every service exposes a read-only `/internal/monitoring/evidence` endpoint.
   Outbox insertion/publication timestamps and committed payment, reservation,
   release and order-transition records establish distinct stages. The collector
   reads remote evidence every three seconds with two-second HTTP timeouts and
   reads its own local evidence directly. Collection failures are isolated from
   business processing; no business transaction writes telemetry.

`InventoryReleased` and `OrderConfirmed` are names for state observations, not new
Kafka event schemas. `InventoryReleased` requires a committed `released_at` row,
written by the same existing transaction that restores stock. A cancellation
without that evidence does not claim completed compensation. Published messages
are explicitly labelled with unverified business consumption. Processing evidence
names the actual input event and the committed outcome; it does not identify a
particular delivery attempt when the database does not preserve one.

## Endpoints and components

- `/monitoring/events`: history; optional exact `orderId`, `type`, `service` filters,
  default 500 and maximum 5,000 observations, returned in observation sequence.
- `/monitoring/stream`: SSE `snapshot`, `events`, `health`, keepalive comments,
  two-second reconnect hint and `Last-Event-ID` resume (`since` query cursor when opening a new browser stream). New connections receive
  500 recent observations. Cursors outside retained history trigger a snapshot.
- `/monitoring/health`: latest checks, cached independently of browser requests.
- `/internal/monitoring/evidence`: per-service sanitized committed evidence,
  bounded to the latest 2,000 records per source table.
- `MonitoringStore`, `MonitoringKafkaObserver`, `MonitoringCollector`,
  `MonitoringController`, `MonitoringScheduling`, and `MonitorEvent` in Order Service.
- `MonitoringEvidenceController` in all three services.
- `SystemMonitor.tsx` and `monitoring.ts` in the frontend, integrated into existing
  navigation and styles. Nginx disables SSE buffering and uses Docker DNS; Vite
  supports the same monitor route for local frontend testing.

## Correlation, ordering, retention and reconnection

All observations use the existing order UUID. Actual event UUIDs are included when
available. Outbox stages have distinct evidence keys; Kafka observations are keyed
by topic/partition/offset so separate genuine duplicate Kafka deliveries remain
visible. Processing/state evidence uses the source row's stable identity. A unique
constraint deduplicates collector/reconnect replays. Telemetry insertion is
serialized in this single Order Service instance so the committed sequence can
serve as an SSE cursor.

The UI deduplicates by telemetry sequence, bounds memory to 5,000 observations and
preserves historical/live classification across replay. Offline/online browser events close and reopen the stream with its last cursor; a 15-second no-message watchdog also recovers stalled sockets. Old evidence discovered
after connection is labelled historical by its event timestamp. Selecting a full
order UUID queries retained server history separately from the live stream.

Observation sequence is collector arrival order, not a guaranteed distributed
business ordering. The UI exposes event/observation timestamps and broker offsets;
only one topic partition has Kafka offset ordering. Database timestamps may be
transaction timestamps and therefore do not precisely measure commit duration.

Retention runs every minute, keeping up to 20,000 observations and seven days of
event time. Older Kafka records are ignored for telemetry storage while still
advancing the independent monitor group. Business history is unaffected.

## Health checks

Service checks use `/actuator/health` at `order-service:8081`,
`inventory-service:8082`, and `payment-service:8083` inside Compose. A successful
`UP` response is healthy, a reachable non-UP response is degraded, and a failed
bounded request is unavailable. Kafka uses Admin `describeCluster` with a
two-second API/request timeout; at least one returned broker is healthy. The
collector polls every ten seconds. The browser independently ages checks older
than thirty seconds to unknown, including when its SSE connection is interrupted.
The initial unknown state does not invent availability.

## Verification

Verification was performed against Java 21, PostgreSQL 17, Kafka 4.1.0 and the
Docker Compose frontend at `http://localhost:5173`. Chrome ran using
`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/google-chrome`.

| Check actually executed | Result |
| --- | --- |
| Final `mvn -B -ntp clean verify` | **PASS**: Order 34, Inventory 14, Payment 5, cross-service workflow 15 = 68 passing tests. The existing opt-in Maven browser wrapper was skipped; Chrome was run directly below. |
| Monitoring-only Maven tests, including real HTTP history API, PostgreSQL deduplication/retention/filtering, independent Kafka observer and healthy/degraded/unavailable health checks | **PASS**: 4 tests; also included in final reactor run. |
| `npm --prefix frontend test` after reconnection changes | **PASS**: 22 tests across 5 files. Includes replay deduplication, distinct stages, filtering, history bounds, offline cursor resume, service failure UI and completed compensation. |
| `npm --prefix frontend run build` | **PASS**: TypeScript and Vite production build. |
| `docker compose up --build -d` after final source changes | **PASS**: all service/frontend images built and services started using existing volumes. |
| `python3 scripts/test-monitoring.py` in normal AUTO simulation | **PASS**: real successful and failed orders; correlated outbox/publication/broker/processing/state stages, stock restoration, health, retained history, filters, SSE Last-Event-ID resume and inventory rejection without invented release. |
| Chrome suite in deterministic successful simulation | **PASS**: 3 tests: live monitor/reconnection/deduplication/history/mobile, existing browse/cart/checkout/receipt/order-dashboard journey, and mobile catalogue. Forced-failure test skipped in this normal-mode run by its explicit prerequisite. |
| Chrome forced-failure test with `DEMO_PAYMENT_FORCE_FAILURE=true` and `MONITOR_FORCE_FAILURE=true` | **PASS**: 1 test, real failed payment, cancellation publication, committed inventory release and exact before/after stock equality. |
| `python3 scripts/test-monitoring.py --force-failure` with the actual Compose failure flag | **PASS**: failed correlated lifecycle, compensation, history/filtering and SSE replay. |
| Stop Payment Service, run `scripts/test-monitor-health.py --expect unavailable`, restore it, run `--expect healthy` | **PASS**: real availability changes, with the other three components healthy. |
| Visual inspection of Chrome screenshots | **PASS**: desktop live monitor, mobile monitor without page overflow, and actual cancellation/compensation trace inspected. |
| `git diff --check` | **PASS**: no whitespace errors. |

Earlier verification exposed and resolved an Nginx duplicate timeout directive,
missing explicit Spring query parameter names, and a browser offline simulation
that left an existing SSE socket open. An initial sandboxed Maven attempt could
not access Docker/Mockito; the final Maven checks ran with the required access and
passed. The original checkout browser test also assumed the ten seeded keyboards
would remain available indefinitely. It now chooses a real product with at least
two available units rather than resetting business data.

Normal `AUTO` payment simulation was restored after deterministic failure and
health-stop testing, with forced failure disabled. Existing database/Kafka volumes
and orders were preserved. Verification added genuine demo orders and consumed
stock on successful orders; it did not replenish, reset or delete inventory.
Final `docker compose ps` confirmed all eight containers healthy, with the frontend
published on port 5173. Payment Service's actual environment was checked:
`PAYMENT_SIMULATION_OUTCOME=AUTO`, `DEMO_PAYMENT_FORCE_FAILURE=false`. The final
normal-mode smoke run also completed successfully after restoration. Nothing was
committed or pushed.

Main changed files are the new monitoring Java package and V6 migration in Order
Service; evidence controllers in Inventory and Payment services; frontend
`SystemMonitor.tsx`, `monitoring.ts`, their tests and monitor browser tests;
`App.tsx`, `styles.css`, the existing shopping browser test, Vite/Nginx configuration,
`docker-compose.yml`, Order Service configuration, both monitoring smoke scripts,
`README.md`, and this results document.

## Practical limits

- The monitor is a demo observability surface, hosted in Order Service. If that
  service is unavailable, the page retains already loaded history, reconnects,
  and ages service checks to unknown; it cannot independently serve new telemetry.
- Backfill from service evidence is limited to 2,000 recent records per table.
  A very long outage or high-volume burst can exceed this window. Kafka retention
  and the seven-day telemetry retention also bound recoverable history. The UI
  does not promise an unlimited audit trail or a complete pre-installation trace.
- Persisted processing outcomes establish completed operations, not individual
  delivery attempts, retries or exact listener acknowledgement times. Some
  intermediate order states are not separately committed by the existing saga.
- SSE connections are capped at 100 per monitor instance. This design targets
  the existing single-instance Compose demo, rather than a distributed telemetry
  cluster. Kafka health is a metadata check, not an end-to-end producer benchmark.
- The existing demo has no authentication. Monitoring follows that deployment
  model and includes only sanitized identifiers and lifecycle metadata.
