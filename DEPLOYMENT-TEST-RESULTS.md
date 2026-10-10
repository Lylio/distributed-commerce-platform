# Docker deployment verification

Verified on 10 October 2026 with Docker Engine 29.8.1 and Compose 5.5.1.

- `docker compose up --build -d` successfully built the frontend and three Java 21 executable service images and started all eight containers.
- All eight health checks passed: frontend, order-service, inventory-service, payment-service, order-db, inventory-db, payment-db and kafka.
- Existing PostgreSQL and Kafka volume keys were preserved.
- An isolated `commerce-deployment-fresh` project with new volumes also started all eight containers healthy. Flyway created all schemas and seeded the six catalogue and inventory records without requiring a dev profile.
- `scripts/test-compose.py` passed against both the existing-data and fresh-volume stacks through Nginx: catalogue and inventory reads, SPA fallback, successful payment/reservation, failed payment/cancellation with exact stock compensation, inventory rejection without payment or stock loss, request validation, and persisted order history.
- `mvn -B -ntp clean verify`: 64 tests passed, no failures/errors; the opt-in Maven browser harness was skipped. The executable JAR classifier preserves plain JAR dependencies for this verification module.
- Frontend Vitest: 15 tests passed.
- Playwright against the fresh stack's built frontend and Nginx using installed Chrome: both desktop shopping/cart/checkout/dashboard and mobile filtering/layout tests passed. Payment was forced to SUCCEEDED for that browser journey.
- A full `docker compose down` followed by `docker compose up --build -d` preserved exact API snapshots of catalogue, available/reserved stock, and the latest 200 orders with their payment outcomes and transition histories.

The isolated test containers were stopped afterward; their four test volumes were retained. Main deployment containers remain running with AUTO simulated payments. Test orders remain in their respective databases; successful orders reserve demo stock. No volumes were deleted, databases reset, or Git commits/pushes performed.

No unresolved deployment failures were found. Existing application limitations remain: payment is simulated, confirmed orders retain reserved inventory until fulfillment is implemented, and legacy reservations without line items cannot be automatically compensated. Local demo credentials/plaintext Kafka and unauthenticated APIs require hardening before public hosting.
