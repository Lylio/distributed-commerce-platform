# FORM frontend

React + TypeScript + Vite storefront for the existing Spring Boot platform. It includes responsive catalogue/product dialogs, search/category/stock filters, price sorting, a persisted cart, real checkout, polling receipts and a shared order/inventory dashboard. All catalogue, stock, orders and outcomes come from the backend. Product illustrations are local SVG components and need no external image service.

## Start with Docker

From the repository root run `docker compose up --build -d`, then open http://127.0.0.1:5173. The frontend image builds with Node and serves through Nginx, with same-origin API proxies to the three services. No host Node/Java installation is needed. Stop with `docker compose down`; volumes and stock persist.

## Frontend development


Use Node.js 22.12+ and npm. Follow the repository [README](../README.md) to start PostgreSQL/Kafka and these backend processes:

```bash
mvn -pl order-service spring-boot:run
mvn -pl inventory-service spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl payment-service spring-boot:run -Dspring-boot.run.arguments="--payment.simulation.outcome=SUCCEEDED"
```

Run each command in its own terminal from the repository root. Then start this frontend:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open http://127.0.0.1:5173. The dev server is local and refuses to silently choose another port. Its API requests are same-origin through Vite's proxy; no permissive backend CORS policy is needed.

The catalogue has six products. Mechanical Keyboard is £79.99 with 100 initial units; Studio Headphones starts at zero to demonstrate unavailable stock. Inventory Flyway V6 seeds missing demo stock without resetting existing quantities. Forced payment outcomes apply to new payment decisions. Restart Payment with `FAILED` to demonstrate cancellation and asynchronous stock restoration; no money is charged in either mode.

## Configuration

Server-side Vite environment overrides:

| Variable | Default |
|---|---|
| `ORDER_API_TARGET` | `http://localhost:8081` |
| `INVENTORY_API_TARGET` | `http://localhost:8082` |
| `FRONTEND_PORT` | `5173` |

These can be exported in the shell before launch or set in `frontend/.env` using `.env.example`. Shell values take precedence. Do not expose database credentials to browser code. Hash routes make reloads work without an SPA rewrite requirement.

Cart storage contains only product IDs and quantities (`form.cart.v1`). A generated customer reference is retained per browser. Prices/names are never submitted by the frontend; Spring Boot resolves them from its own catalogue. Checkout clears the cart only after successful order creation. A failed payment is an actual saved/cancelled order, not a failed HTTP submission. Stock failures disable known-unavailable cart quantities; the asynchronous backend still handles stock races safely.

The dashboard is intentionally shared and unauthenticated. It displays the latest 200 orders, real committed state changes and Kafka-derived payment decisions. Confirmed stock remains reserved because fulfillment is not implemented. Payment-failure releases can lag cancellation status. Receipts poll until a terminal outcome or two minutes, and remain accessible through the dashboard afterward.

## Verification

From the repository root:

```bash
npm --prefix frontend run test
npm --prefix frontend run build
```

Vitest checks cart recovery/persistence, search, unavailable stock, details, checkout acknowledgement/error handling and retry states. Tests disable Node's experimental web storage to use jsdom's browser storage.

For isolated real-backend browser verification:

```bash
cd frontend
npx playwright install chromium
cd ..
mvn clean verify -Dcommerce.browser-tests=true
```

Docker is required. The Java test harness starts three PostgreSQL containers, Kafka and three HTTP services on random ports, configures Vite's targets and launches Playwright on port 5179. It does not mock browser network requests. Tests verify browsing/details, persisted quantity edits, checkout payloads, confirmed PostgreSQL-backed orders, dashboard stock updates and mobile layout/filtering. Screenshots and failure traces are written under `frontend/test-results/` and ignored by Git. Each run uses isolated databases and leaves development data alone.

With an already-running backend you can also run `npm --prefix frontend run test:e2e`, with successful payments forced and adequate keyboard stock. Stock assertions compare against the starting quantity. Set `COMMERCE_BASE_URL=http://127.0.0.1:5173` to test the deployed Nginx frontend without starting Vite.

`npm --prefix frontend run preview` serves a production build with the same local API proxies. Deploying `dist/` alone does not supply a backend: production hosting must route `/api/orders` and `/api/products` to Order and `/api/inventory` to Inventory, stripping `/api`. Authentication/access control, actual payments, shipping, checkout idempotency remain separate work; the Docker deployment includes Nginx hosting and API routing.
