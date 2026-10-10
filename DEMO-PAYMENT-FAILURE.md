# Deterministic demo payment failure — installation and test

This patch changes only Payment Service. It adds the optional environment variable `DEMO_PAYMENT_FORCE_FAILURE`. Existing orders keep their persisted payment outcome; create a **new order** to test failure.

## Apply
From `~/workspace/distributed-commerce-platform`:

```bash
unzip -o ~/Downloads/demo-payment-failure-patch.zip
mvn -pl payment-service -am clean verify
```

## Run failure mode
Stop the running Payment Service with Ctrl+C, then restart it in its terminal:

```bash
cd ~/workspace/distributed-commerce-platform
DEMO_PAYMENT_FORCE_FAILURE=true mvn -pl payment-service spring-boot:run
```

Keep Order Service, Inventory Service, Kafka and the frontend running. In Chrome, note a product's stock quantity, place a **new** order, and watch the order page for `CANCELLED`. Revisit inventory: the quantity should return to its starting value. If it doesn't, inspect the Order Service log for `Published order-cancelled` and Inventory Service log for `stock released`.

**Important:** This is a demo switch, not a production payment failure simulator. All **new** payment attempts processed while the flag is true fail; preexisting payment records are unchanged. To restore normal mode, stop Payment Service and start it again without the variable (or set it to `false`). Normal mode retains the existing deterministic ~10% failure behavior.

## Architecture caveat
The cancellation event is currently published after commit, but without a durable outbox it can be lost during a crash or broker outage. This demo proves the happy compensation path, not crash-safe guarantees. Existing legacy reservations without item records may require manual reconciliation.
