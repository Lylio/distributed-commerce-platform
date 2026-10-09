# Inventory idempotency patch — 9 October 2026

This small patch updates only `inventory-service` and adds five unit tests.

- `ReserveInventoryService.reserve()` returns `true` when stock was newly reserved and `false` for an already-claimed order.
- The Kafka listener publishes `InventoryReservedEvent` only after a new reservation.
- Tests cover initial reservation, duplicate delivery, missing product, and Kafka publish/skip paths.

## Apply

From `~/workspace` (with the original project in `~/workspace/distributed-commerce-platform`):

```bash
unzip -o inventory-idempotency-patch.zip -d distributed-commerce-platform
cd distributed-commerce-platform
mvn -pl inventory-service -am test
```

The ZIP contains project-relative paths and will overwrite **two existing Java files**; it also adds two test files. Review `unzip -l` first if desired.

## Important limitations

This fixes redundant publication on a duplicate Kafka delivery, but is **not** a transactional outbox. A crash after committing the stock change but before publishing the Kafka event can still leave an order stuck. A future change should persist an outbox event in the same DB transaction and publish it asynchronously. Tests in this patch are unit tests; PostgreSQL/Kafka integration tests and a real duplicate-delivery test are still needed.

The migration V2 and reservation repository are already present in your uploaded project; this patch does not recreate them.
