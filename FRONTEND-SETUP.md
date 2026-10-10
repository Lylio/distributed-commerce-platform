# Frontend setup

The integrated storefront uses the persisted catalogue and server-authoritative pricing.
See [the root README](README.md) for all three backend services, and
[the frontend README](frontend/README.md) for installation, proxy configuration and browser tests.

Legacy `/api/store` endpoints now use the same persisted catalogue as `/products`.
Initial inventory is seeded by Flyway V6 without resetting existing stock.
Payment remains a simulation; this demo has no authentication or fulfillment.
