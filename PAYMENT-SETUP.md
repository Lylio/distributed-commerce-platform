# Payment simulator

Payment is integrated into the Maven reactor and Compose now includes its PostgreSQL database. Follow [README.md](README.md) for the complete three-service startup and deterministic success/failure examples; the old ZIP patch installation steps are no longer needed.

The service records one immutable simulated decision per order. That decision and its `payment-result` outbox event commit together. Duplicated reservation events do not create new decisions or event IDs. Publication retries use the original payload and event ID.

`payment.simulation.outcome` accepts `AUTO` (default), `SUCCEEDED` or `FAILED`; it can also be set with `PAYMENT_SIMULATION_OUTCOME`. Forced outcomes apply to new decisions only. AUTO uses the order UUID hash modulo 10, not randomness.

No amount, currency, payment instrument or actual gateway is handled. Payment failure cancels the order and triggers durable inventory compensation. Real payment integration remains a separate development batch.
