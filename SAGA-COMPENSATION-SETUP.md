# Inventory compensation and recovery

Follow [README.md](README.md) for current startup and verification. The old patch ZIP installation instructions are obsolete.

A failed payment cancels Order and inserts `order-cancelled` into Order's outbox in the same transaction. The relay retries publication until acknowledged. Inventory locks the reservation and product rows, restores the recorded quantities and marks `released_at` in one transaction. Duplicate cancellation cannot restore quantities twice. Inventory rejection changes no stock and emits no payment request or compensation event.

Cancellation status does not acknowledge stock-release completion. Inspect `inventory_reservations.released_at` and pending Order outbox rows when diagnosing compensation. Automated completion acknowledgement/reconciliation and dead-letter replay are still needed.

## Legacy reservations

Reservations created before Inventory V3 do not have line-item records. Automatic release deliberately fails rather than guessing stock quantities. New migrations do not fabricate those records or recreate missing pre-outbox events.

For a legacy incident: identify the cancelled order and its exact persisted product quantities in the Order database; verify the reservation is active and that no previous manual release occurred; reconcile against current reserved totals and audit records. During controlled maintenance, an operator can insert the verified line items into `inventory_reservation_items`, then arrange replay of the original cancellation. Ambiguous or already-reconciled reservations require manual investigation. Do not blindly replay creation events or reset stock counters.

Use new orders after migrations for the deterministic development demonstrations. Production reconciliation tooling and an audited maintenance procedure remain outstanding.
