# NexusCommerce

> **Full project guide:** [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md) — setup, tech stack, architecture, API flows, payloads, Redis keys, real-life scenarios, and testing checklists.

NexusCommerce is a **Spring Boot** REST backend for a small e-commerce domain: **categories**, **products** (with stock/inventory and image upload), a persistent **shopping cart**, **orders** (with snapshotted line items), and **reviews** tied to both an order and a product. It is secured with **JWT authentication** (access + rotating refresh token, Redis-backed revocation), **role-based authorization** (`USER` / `ADMIN`), backed by **MySQL**, versioned by **Flyway** migrations with `ddl-auto: validate`, cached in **Redis**, observable through **Actuator + Micrometer/Prometheus** and optionally the **New Relic** APM agent, containerised via a multi-stage **Dockerfile**, and shipped with a **GitHub Actions** CI that boots the app against real MySQL + Redis.

---

## Features

- **Auth & roles** — Register (`POST /api/v1/users`), log in (`POST /api/v1/auth/login`), refresh (`POST /api/v1/auth/refresh`) and log out (`POST /api/v1/auth/logout`). Access tokens are stateless **JWT bearer** tokens; refresh tokens are opaque, single-use values stored in Redis with rotation on every refresh. Logging out blacklists the current `jti` in Redis for the rest of its lifetime. Two roles, `USER` and `ADMIN`, gate every endpoint (see [Authentication & roles](#authentication--roles)).
- **Login rate limiting** — Fixed-window, IP-keyed Redis counter on `POST /api/v1/auth/login` (default **10 / 60 s**, tunable via `LOGIN_RATE_MAX` / `LOGIN_RATE_WINDOW_SECONDS`). Over-limit returns **429** with the standard `ApiResponse` envelope.
- **Categories** — Create, list (paged), get by id, update, and delete categories. Deleting a category that still holds live products is rejected.
- **Products** — CRUD-style operations, fetch with category details, search by category name, list distinct category names, per-product **stock** tracking, and admin image upload (`POST /api/v1/admin/products/{id}/image`) that persists PNG/JPEG/WebP files to disk and rewrites the product's `image` URL to a served path (`/uploads/products/<file>`).
- **Shopping cart** — Persistent per-user cart (V13), backed by its own table so state survives closing the browser. Add / update / remove lines, clear, and **checkout** (which converts the cart into an order through the same stock + snapshot pipeline).
- **Inventory** — Placing an order atomically decrements stock (oversell-safe); admins restock via `PUT /api/v1/admin/products/{id}/stock`.
- **Orders** — Create orders from a list of line items (product id + quantity), list/fetch/update/delete orders, fetch an order **summary**, and read **your own orders** (`/me`) or any user's (admin). Line items are **snapshotted** (price, name, image) so history never changes when the catalogue is edited. Both `POST /orders` and `POST /cart/checkout` accept an optional **`Idempotency-Key`** header so a retried submission can never place a duplicate order.
- **Reviews** — Create reviews only when the product appears on the given order; at most **one review per (order, product)** pair; list reviews globally, by id, by product, or by order; delete by id (admin).
- **Admin dashboard** — Aggregate business metrics, paged user listing, and user role/enable management (all `ROLE_ADMIN`).
- **Domain events** — `OrderPlacedEvent` and `OrderStatusChangedEvent` are published after commit and consumed asynchronously by an `@Async @TransactionalEventListener` — currently wired to a **logging notification stub** (`LoggingNotificationService`) that a real mail/SMS bean can replace without touching callers.
- **Data integrity** — Soft-delete on every entity, `@Version` optimistic locking on orders, and DB-level uniqueness that survives soft-deletes (see [Data integrity & migrations](#data-integrity--migrations)).
- **Consistent JSON envelope** — All controllers return an `ApiResponse<T>` wrapper (`success`, `message`, `error`, `data`); paged endpoints wrap a `PageResponse<T>`.
- **Global error handling** — Mapped HTTP statuses for not found, conflict, bad request, unauthorized, forbidden, method-not-supported, missing required query param, rate-limited, and generic server errors.
- **Database migrations** — Flyway applies versioned SQL (`V1`–`V14`) under `src/main/resources/db/migration`.
- **API documentation** — OpenAPI 3 + Swagger UI (springdoc), with a bearer-authorization scheme pre-wired, alphabetical tag/operation sorters and doc collapsed by default.
- **Observability** — Spring Boot Actuator exposing `health`, `info`, `metrics`, `prometheus`. Liveness/readiness probes are public (for the platform), everything else under `/actuator/**` is `ROLE_ADMIN`. New Relic Java agent attaches automatically when `newrelic/newrelic.jar` is present.
- **Graceful shutdown** — `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s`; combined with the probes, that makes rolling restarts safe.
- **Containerised** — Multi-stage `Dockerfile` (Temurin 21 → non-root `appuser` uid 10001, `/app/uploads/products` writable).
- **CI** — GitHub Actions on `push`/`pull_request` to `main` and `dev`: builds the jar, runs unit tests on H2, then boots the real app against MySQL 8.4 + Redis 7 service containers and polls `/actuator/health` for up to 120 s.

---

## Tech stack

| Area | Choice |
|------|--------|
| Runtime | Java **21** |
| Framework | Spring Boot **4.0.2** |
| Persistence | Spring Data JPA, Hibernate |
| Security | **Spring Security** (stateless) + **JWT** (`jjwt`) + BCrypt password hashing + Redis-backed refresh-token store, jti blacklist and login rate limiter |
| Database | **MySQL** (Connector/J) |
| Migrations | **Flyway** (MySQL support) — `V1`–`V14` |
| API docs | **springdoc-openapi** 2.8.x (Swagger UI) |
| Build | **Gradle** (wrapper) |
| Cache / session-like state | **Redis** (Spring cache-aside on GET + refresh-token store + JWT blacklist + login rate limit + idempotency replay; see [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md#10-redis-caching)) |
| Metrics / health | **Spring Boot Actuator** + **Micrometer** with **Prometheus** registry |
| APM | **New Relic** Java agent (attached conditionally; see [Observability and monitoring](#observability-and-monitoring)) |
| Containers | Multi-stage **Dockerfile** (Temurin 21) |
| CI | **GitHub Actions** (`.github/workflows/ci.yml`) with MySQL + Redis service containers |
| Async / events | Spring `@EnableAsync` + `@TransactionalEventListener(AFTER_COMMIT)` |

---

## Observability and monitoring

NexusCommerce is wired for **production-style visibility** through Actuator + Micrometer (self-hosted metrics) and, optionally, the New Relic Java APM agent.

### Actuator + Micrometer (built in)

- **Exposed endpoints** — `health`, `info`, `metrics`, `prometheus` (see `application.yml`, `management.endpoints.web.exposure.include`). `/actuator/shutdown`, `/env`, and `/configprops` are **not** on the wire.
- **Health details** — `management.endpoint.health.show-details: when-authorized`, so anonymous callers see only `{"status":"UP"}` while an `ADMIN` bearer token sees the DB / Redis component states.
- **Probes** — `management.endpoint.health.probes.enabled: true`; `/actuator/health/liveness` and `/actuator/health/readiness` are `permitAll` for Kubernetes / LB health checks. Everything else under `/actuator/**` requires `ROLE_ADMIN`.
- **Metric tags** — Every Micrometer counter/timer carries `application=NexusCommerce`.
- **Prometheus scrape** — `GET /actuator/prometheus` (admin-only) returns the standard text format; add a scrape job pointing at `http://<host>:<PORT>/actuator/prometheus` with an admin bearer token.

### Graceful shutdown

`server.shutdown: graceful` combined with `spring.lifecycle.timeout-per-shutdown-phase: 30s` means Tomcat stops accepting new connections on SIGTERM and lets in-flight requests finish (bounded by 30 s) before the JVM exits. Pair with the readiness probe so a load balancer drains traffic first.

### New Relic (APM, optional)

- **Attach** — `./gradlew bootRun` starts the JVM with `-javaagent:newrelic/newrelic.jar` **only when that file exists** (see `build.gradle`). The agent JAR is intentionally gitignored, so CI, Docker images and fresh clones run cleanly **without** it. Drop the JAR into `newrelic/` locally to opt in.
- **Configuration** — `newrelic/newrelic.yml`. `app_name` is **`NexusCommerce`**, so traces and metrics roll up under that name in the New Relic UI.
- **Secrets** — Set the license key **only** via the `NEW_RELIC_LICENSE_KEY` environment variable; the `license_key:` line in `newrelic.yml` is commented out and must never be uncommented with a real value.

> **Known issue — historical NR key.** A real New Relic license key was committed to this repository at `newrelic/newrelic.yml` before commit `dcaaaf8`. It has been removed from the tip of every branch, but **it is still in git history** and must be treated as leaked: revoke or rotate the key at the New Relic admin console, and (optionally) run `git filter-repo --invert-paths --path newrelic/newrelic.yml` plus a coordinated force-push to purge history. Until that's done, do not reuse that key anywhere.

- **Agent logs** — Written to `newrelic/logs/newrelic_agent.log` (gitignored).

### Application and SQL logging

- **Hibernate SQL** — `spring.jpa.show-sql: false` (statement logging goes through the `org.hibernate.SQL` logger instead), keeping the hot path free of synchronized `System.out` writes. Enable `show-sql` only for local debugging.
- **Spring Boot logging** — Default Logback/console logging applies for framework and application classes; a `logstash-logback-encoder` TCP appender is configured for the ELK stack defined in `docker-compose.yml` (`localhost:5044`). It falls back with a WARN when Logstash isn't reachable — expected on plain local runs.

---

## Prerequisites

- **JDK 21**
- **MySQL** 8.x (project has been validated against **MySQL 8.4** and **MySQL 9.6**; Flyway logs a compatibility notice above 8.1 but applies cleanly).
- A database named **`nexuscommerce`** (or adjust the JDBC URL in configuration).
- **Redis** reachable at `localhost:6379` (or `redis_port` in `.env`) — used for read-through caching, refresh-token storage, JWT revocation, login rate limiting and idempotency replay.
- **Docker** *(optional)* — for the multi-stage image and the ELK/Redis stack in `docker-compose.yml`.

---

## Configuration

Configuration lives in `src/main/resources/application.yml`.

The app imports an **optional** env file: `optional:file:.env[.properties]`. Every key can be supplied there or through Spring's normal externalised-config channels (env vars, JVM args, `application-<profile>.yml`, …).

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `3001` | Embedded Tomcat HTTP port |
| `username` | — | MySQL user |
| `password` | — | MySQL password |
| `redis_port` | `6379` | Redis port (host defaults to `localhost`) |
| `JWT_SECRET` | dev-only string in yml | HMAC signing secret, **≥ 32 bytes** — always override outside local dev |
| `JWT_EXPIRATION_MINUTES` | `120` | Access-token lifetime |
| `JWT_REFRESH_EXPIRATION_DAYS` | `7` | Refresh-token TTL in Redis |
| `LOGIN_RATE_MAX` | `10` | Login attempts per window (per client IP) |
| `LOGIN_RATE_WINDOW_SECONDS` | `60` | Window length for the login rate limiter |
| `UPLOAD_DIR` | `uploads/products` | Local filesystem directory for product image uploads; mount a volume in Docker |

**Never commit real secrets.** `.env` is gitignored; the `newrelic/newrelic.yml` license key is deliberately commented out.

```properties
# Example .env (do not commit)
PORT=3001
username=your_mysql_user
password=your_mysql_password
redis_port=6379
JWT_SECRET=change-me-to-a-long-random-secret-at-least-32-bytes
JWT_EXPIRATION_MINUTES=120
JWT_REFRESH_EXPIRATION_DAYS=7
LOGIN_RATE_MAX=10
LOGIN_RATE_WINDOW_SECONDS=60
UPLOAD_DIR=uploads/products
```

---

## Setup

1. **Create the database** (once):

   ```sql
   CREATE DATABASE nexuscommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Configure** `username`, `password`, and `PORT` as described above.

3. **Start Redis** (default port `6379`) and, if you want the ELK log pipeline, the compose stack (`docker compose up -d`).

4. **Run the application** from the project root:

   ```bash
   ./gradlew bootRun
   ```

   On first startup, **Flyway** creates and updates tables (migrations `V1`–`V14`, including `reviews`, `users`, roles, stock, order snapshots, shopping carts, and the admin seed).

5. **Run tests**:

   ```bash
   ./gradlew test
   ```

   Tests run against an in-memory H2 database; no external MySQL/Redis required.

6. **Build the container image**:

   ```bash
   docker build -t nexuscommerce:local .
   docker run --rm -p 3001:3001 \
     -e username=root -e password=secret \
     -e JWT_SECRET=replace-me-with-a-32+-byte-secret \
     nexuscommerce:local
   ```

7. **OpenAPI / Swagger UI** (default springdoc paths; use your `PORT`):

   - Swagger UI: `http://localhost:<PORT>/swagger-ui/index.html`
   - OpenAPI JSON: `http://localhost:<PORT>/v3/api-docs`

---

## CI

`.github/workflows/ci.yml` runs on every push and pull request targeting `main` or `dev`. One job, three phases:

1. **Assemble** — `./gradlew --no-daemon assemble` (compile + jar).
2. **Unit tests** — `./gradlew --no-daemon test` against H2.
3. **Boot smoke test** — MySQL 8.4 and Redis 7 are provided as service containers with healthchecks; the workflow runs `./gradlew --no-daemon bootRun` and polls `http://localhost:3001/actuator/health` up to 120 s. If the process dies early the full `bootrun.log` is printed; if it never becomes healthy the tail is printed.

CI deliberately uses an **empty MySQL password** and a fixed **`ci-only-secret-please-rotate-in-real-deployment-0123456789`** `JWT_SECRET` — those are throwaway values scoped to the ephemeral runner.

---

## Authentication & roles

The API is **stateless JWT** with an **opaque, rotating refresh token**. Log in to receive a token, then send it as `Authorization: Bearer <token>` on every protected call. Swagger UI has an **Authorize** button wired to the bearer scheme, so you can paste the token once and try any endpoint.

**Getting a token**

1. Register (public): `POST /api/v1/users` with `name`, `email`, `password` (min 8 chars), optional `phone`. New accounts get role `USER`.
2. Log in (public): `POST /api/v1/auth/login` with `email` + `password`.

```json
{
  "token": "<jwt access>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-22T12:00:00Z",
  "refreshToken": "<opaque, single-use>",
  "user": { "id": 4, "email": "…", "role": "USER", "enabled": true }
}
```

3. Refresh (public): `POST /api/v1/auth/refresh` with `{ "refreshToken": "…" }`. The old refresh token is consumed and a fresh access+refresh pair is issued. Attempting to reuse a consumed token → 401.
4. Log out: `POST /api/v1/auth/logout` with the access token in `Authorization` **and** `{ "refreshToken": "…" }` in the body. The access token's `jti` is blacklisted in Redis for the remainder of its lifetime; the refresh token is deleted.

**Token shape** — HS-signed (algorithm chosen from `JWT_SECRET` length; the bundled dev secret yields HS512). Claims: `sub` = email, `uid` = user id, `role`, `jti` = random UUID used for revocation. Expires after `JWT_EXPIRATION_MINUTES` (default 120). Passwords are stored **BCrypt-hashed**; the secret and hash never leave the server.

**Rate limit** — `POST /api/v1/auth/login` is IP-keyed (honouring `X-Forwarded-For`) and defaults to **10 requests / 60 s** per client. Above that → **429** with `ApiResponse` body. Clear the counter for your own IP during local testing with `redis-cli DEL rate::login::<ip>`.

**Two roles** — `USER` (default) and `ADMIN`. Authorization is enforced by path + method matchers in `SecurityConfig`, plus ownership checks in `OrderController` and `CartController`.

| Access | What |
|--------|------|
| **Public** (no token) | Register, login, refresh, logout, Swagger, health/liveness/readiness probes, and **browsing**: `GET` products, categories, reviews. Uploaded product images served from `/uploads/**`. |
| **Any authenticated user** | Cart surface (`GET /cart`, `POST /cart/items`, `PATCH /cart/items/{id}`, `DELETE /cart/items/{id}`, `DELETE /cart`, `POST /cart/checkout`), place an order directly, create a review, and read **your own** orders (`/orders/me`, `/orders/{id}`, `/orders/{id}/summary`). Order read/update is scoped so you can only touch orders you own. |
| **`ROLE_ADMIN` only** | All catalogue **writes** (create/update/delete products & categories), product **image upload**, review **deletion**, the order **list** (`GET /orders`) and order **deletion**, the whole `/api/v1/admin/**` surface, viewing any user's orders, and everything under `/actuator/**` except the health probes. |

**Errors** — Missing/invalid/revoked token on a protected route → `401` (`"Unauthorized!"`); valid token but wrong role / not the owner → `403` (`"Forbidden!"`); too many failed logins → `429`.

> **Bootstrapping the first admin:** two mechanisms exist.
> 1. **V14 dev seed** — after Flyway runs, register `root-admin@nexuscommerce.test` via `POST /api/v1/users`; the migration already promoted that email's row to `ADMIN` (a no-op if the account doesn't exist yet, so re-run it once you've registered, or use step 2).
> 2. **Manual promote** — set the row's `role` to `ADMIN` in MySQL, or use an existing admin account via `PATCH /api/v1/admin/users/{id}`, then log in again — the role is carried in the token.
>
> Before any real deployment, delete or rotate the seed email in `V14__seed_admin_role.sql`.

---

## API overview

Base path for REST resources: **`/api/v1`**.

Unless noted, responses use:

```json
{
  "success": true,
  "message": "…",
  "error": null,
  "data": { }
}
```

On failure, `success` is `false`, `error` carries detail, and `data` is often `null`.

**Pagination** — List endpoints (`GET /products`, `/categories`, `/reviews`, `/orders`, `/orders/me`, `/reviews/product/{id}`, `/admin/users`) are paged. Query params `page` (default `0`) and `size` (default `20`, clamped server-side). Paged `data` is a `PageResponse`: `{ content, page, size, totalElements, totalPages, last }`.

**Idempotency** — `POST /orders` and `POST /cart/checkout` accept an optional `Idempotency-Key: <uuid>` header. When present, the response body is cached in Redis under `idem::<userId>::<scope>::<key>` for 24 h; a retry with the same key replays the first response with **201 Created** instead of placing a duplicate order. Concurrent requests with the same key see one in-flight and one rejected with **400**. Scope is `"orders"` or `"cart-checkout"`.

The **Access** column below shows the minimum role required.

### Auth — `/api/v1/auth` & `/api/v1/users`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/users` | Public | Register (`CreateUserRequestDto`: `name`, `email`, `password` ≥8, optional `phone`). |
| `POST` | `/api/v1/auth/login` | Public (rate-limited) | Log in (`LoginRequestDto`: `email`, `password`) → JWT access token + single-use refresh token. |
| `POST` | `/api/v1/auth/refresh` | Public | Consume the previous refresh token, receive a fresh access + refresh pair. |
| `POST` | `/api/v1/auth/logout` | Public (needs `Authorization` header) | Blacklist the current access-token `jti` in Redis and delete the paired refresh token. |

### Categories — `/api/v1/categories`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `GET` | `/` | Public | List categories (paged). |
| `GET` | `/{id}` | Public | Get category by id. |
| `POST` | `/` | **ADMIN** | Create category (`CreateCategoryRequestDto`: `name`). |
| `PUT` | `/{id}` | **ADMIN** | Update category. |
| `DELETE` | `/{id}` | **ADMIN** | Delete category (rejected if it still has live products). |

### Products — `/api/v1/products`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `GET` | `/` | Public | List products (paged summary DTOs). |
| `GET` | `/{id}` | Public | Get product by id. |
| `GET` | `/{id}/details` | Public | Get product with related category/details. |
| `GET` | `/search?categoryName=…` | Public | Find products by category name (paged). |
| `GET` | `/uniqueCategories` | Public | List unique category names present on products. |
| `POST` | `/` | **ADMIN** | Create product (`CreateProductRequestDto`: `title`, `description`, `price`, `image`, `categoryId`, `rating`, optional `stock`). |
| `PUT` | `/{id}` | **ADMIN** | Update product (partial merge — only non-null fields change). |
| `DELETE` | `/{id}` | **ADMIN** | Soft-delete product. |

### Cart — `/api/v1/cart`

All endpoints require a valid bearer token and are scoped to the caller.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Fetch (or lazily create) the caller's cart. Response includes `grandTotal`, `itemCount`, and every `CartItemResponseDto` (`productName`, `productImage`, `unitPrice`, `quantity`, `lineTotal`). |
| `POST` | `/items` | Add a product (`AddToCartRequestDto`: `productId`, `quantity` ≥ 1). If the product is already on a line, the quantity is summed. |
| `PATCH` | `/items/{id}` | Replace a line's quantity (`UpdateCartItemRequestDto`: `quantity` ≥ 1). Owner-only. |
| `DELETE` | `/items/{id}` | Remove a single line. Owner-only. |
| `DELETE` | `/` | Empty the cart (keeps the cart row, clears items). |
| `POST` | `/checkout` | Convert the cart into an order via the same atomic stock reservation + snapshot pipeline; **optional** `CreateOrderRequestDto` body to override the lines; **optional `Idempotency-Key`** header (scope `"cart-checkout"`). Returns `201` with the created order. |

### Orders — `/api/v1/orders`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/` | User | Create order (`CreateOrderRequestDto`: `orderItems` — each `OrderItemRequestDto`: `productId`, `quantity`); places it on the authenticated caller, decrements stock. **Optional `Idempotency-Key`** header (scope `"orders"`). |
| `GET` | `/me` | User | **Your** orders (paged). |
| `GET` | `/{id}` | Owner/ADMIN | Get order by id (only if you own it or are admin). |
| `GET` | `/{id}/summary` | Owner/ADMIN | Order summary DTO (aggregated totals from line items). |
| `PUT` | `/{id}` | Owner/ADMIN | Update order (`UpdateOrderRequestDto`: `status`, `orderItems` actions `ADD`/`REMOVE`/`INCREMENT`/`DECREMENT`). Blocked on `DELIVERED`/`CANCELLED`. |
| `GET` | `/` | **ADMIN** | List all orders (paged). |
| `GET` | `/user/{userId}` | Owner/ADMIN | Orders for a user (paged). |
| `DELETE` | `/{id}` | **ADMIN** | Delete order; returns reserved stock and soft-deletes its line items. |

### Reviews — `/api/v1/reviews`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `GET` | `/` | Public | List all reviews (paged). |
| `GET` | `/{id}` | Public | Get review by id. |
| `GET` | `/product/{productId}` | Public | Reviews for a product (paged). |
| `GET` | `/order/{orderId}` | Public | Reviews for an order (paged). |
| `POST` | `/` | User | Create review (`CreateReviewRequestDto`: `orderId`, `productId`, `rating`, optional `comment`). |
| `DELETE` | `/{id}` | **ADMIN** | Delete review. |

### Admin — `/api/v1/admin`

All endpoints below require `ROLE_ADMIN`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/metrics` | Business metrics (`AdminMetricsResponseDto`: totals for users/products/categories/orders/reviews, `totalRevenue`, `ordersByStatus`, `outOfStockProducts`). |
| `GET` | `/users` | List users (paged). |
| `GET` | `/users/{id}` | Get a user. |
| `PATCH` | `/users/{id}` | Update a user's `role` and/or `enabled` (last-active-admin can't be disabled). |
| `PUT` | `/products/{id}/stock` | Set a product's stock (`SetStockRequestDto`: `stock` ≥ 0). |
| `POST` | `/products/{id}/image` | Multipart upload (`file`) — PNG/JPEG/WebP up to 5 MB. Writes under `UPLOAD_DIR` with a UUID filename, updates `product.image` to `/uploads/products/<file>` and returns the URL as the `data` field. |

### Actuator — `/actuator`

| Path | Access | Notes |
|------|--------|-------|
| `GET /actuator/health` | Public (details admin-only) | Standard health probe. |
| `GET /actuator/health/liveness` | Public | Kubernetes liveness. |
| `GET /actuator/health/readiness` | Public | Kubernetes readiness — pair with graceful shutdown. |
| `GET /actuator/info` | **ADMIN** | Application info. |
| `GET /actuator/metrics` / `/metrics/{name}` | **ADMIN** | Micrometer metric registry. |
| `GET /actuator/prometheus` | **ADMIN** | Prometheus text exposition. |

**Review rules (business logic):**

- `orderId` and `productId` must exist; the **product must be a line item** on that order.
- **Duplicate** reviews for the same `(orderId, productId)` are rejected (**409 Conflict**) — enforced by a DB unique constraint on live reviews.
- `rating` is required, must be **greater than 0** and **at most 10** (service validation).
- `comment` is optional; blank/whitespace-only comments are treated as no comment.

### Typical HTTP status codes (errors)

| Status | When |
|--------|------|
| `401` | Protected route called without a valid, un-revoked token, or a consumed/invalid refresh token. |
| `403` | Valid token, but wrong role or not the resource owner. |
| `400` | Invalid request (validation / business-rule violations) **or** missing required query parameter **or** concurrent idempotency-key collision. |
| `404` | Resource not found (e.g. missing id). |
| `405` | Wrong HTTP method for a matched path. |
| `409` | Duplicate resource (e.g. duplicate review for same order + product). |
| `429` | Login rate limit exceeded (default 10/60 s per IP). |
| `500` | Unhandled exceptions (generic handler). |

Create/update endpoints may return **`201 Created`** or **`204 No Content`** where implemented in controllers.

---

## Data integrity & migrations

Several guarantees are worth knowing before you touch the domain:

- **Soft delete everywhere** — Every entity uses `@SQLDelete` + `@SQLRestriction(deleted_datetime IS NULL)`. A "delete" is an UPDATE that stamps `deleted_datetime`; restricted reads then hide the row without losing it.
- **Optimistic locking on orders** — `Order` carries a `@Version` field (added in `V8`), so concurrent updates to the same order collide predictably instead of silently overwriting. Its soft-delete SQL is version-guarded too.
- **Oversell-safe stock** — Placing/adjusting an order decrements stock with an atomic conditional bulk UPDATE (`WHERE stock >= qty`); zero affected rows means insufficient stock and the whole transaction rolls back. Returning stock on delete/remove is likewise atomic.
- **Historical line-item snapshots** — Order line items store `unit_price` (`V11`) plus `product_name` and `product_image` (`V12`). Editing a price, renaming a product, or soft-deleting it therefore **cannot** rewrite historical orders or revenue, and a deleted product's line item still renders.
- **Uniqueness that survives soft-delete** — Flyway generated columns mirror the unique key only for live rows (`V8` unique names, `V10` one live review per `(order, product)`, `V13` one live cart per user and one live cart line per `(cart, product)`), so a deleted row frees the slot without a partial unique index.
- **Migrations are the source of truth** — `spring.jpa.hibernate.ddl-auto: validate` means Hibernate only checks the schema; **Flyway** (`V1`–`V14` under `src/main/resources/db/migration`) owns it. Add a new `V__` file for any column change — never edit an applied migration.
- **Idempotency on writes** — `POST /orders` and `POST /cart/checkout` accept a client-generated `Idempotency-Key` UUID. The first response is stored in Redis under a **user-scoped** key with a 24 h TTL; retries replay the cached body. In-flight locks have a 10 min TTL and are safely re-acquirable after expiry (SETNX on the reacquisition path, not a plain SET), so a client crash cannot wedge a scope for 24 h.
- **Revocation is Redis-backed** — Access tokens are self-verifying JWTs, so "logout" blacklists the current `jti` in Redis for the rest of its lifetime (`jwt:blacklist::<jti>`), and refresh tokens live only as opaque random 32-byte values keyed by `refresh::<token>` with rotation on every refresh.

| Migration | Purpose |
|-----------|---------|
| `V1`–`V4` | Init schema, decimal rating, reviews table, product price index. |
| `V5`–`V6` | `users` table + order→user link; `password_hash` on users. |
| `V7` | Functional lower-case lookup indexes for `*IgnoreCase` queries on `categories.name` / `products.title`, composite `idx_reviews_order_product`, plus **conditional drops** of `fk_review_order` and `idx_product_price` guarded by `information_schema` (see note below). |
| `V8` | Order `version` column (optimistic locking) + soft-delete-safe unique names. |
| `V9` | `users.role`, `users.enabled`, `products.stock`. |
| `V10` | DB unique on live `(order_id, product_id)` reviews. |
| `V11` | `order_products.unit_price` snapshot (backfilled). |
| `V12` | `order_products.product_name` / `product_image` snapshot (backfilled). |
| `V13` | `carts` + `cart_items` — persistent shopping cart, one live cart per user, live `(cart, product)` unique lines. |
| `V14` | Dev seed: promote `root-admin@nexuscommerce.test` to `ADMIN` (rotate or delete before real deployments). |

> **MySQL 8.4 note on V7.** On a fresh MySQL 8.4 DB, `CREATE INDEX idx_reviews_order_product ON reviews (order_id, product_id)` causes the server to drop the FK-supporting index `fk_review_order` automatically. An unconditional `DROP INDEX fk_review_order` then fails with `1091 Can't DROP`. V7 wraps both drops in a stored procedure that checks `information_schema.STATISTICS` before issuing the DDL, so it applies cleanly on **fresh MySQL 8.4** databases and on **older dev databases** that pre-date the composite.

---

## Project layout (high level)

```
src/main/java/com/example/nexusCommerce/
  adapters/          # Entity ↔ DTO mapping
  configs/           # Security / WebMvc / cache / OpenAPI / auditing / password-encoder / async beans
  controllers/       # REST endpoints (Auth, Cart, Category, Product, Order, Review, Admin, User)
  dtos/              # Request/response payloads
  events/            # OrderPlacedEvent, OrderStatusChangedEvent, OrderEventListener (@Async AFTER_COMMIT)
  exceptions/        # Custom exceptions + GlobalExceptionHandler
  repositories/      # Spring Data JPA
  schema/            # JPA entities (User, Category, Product, Order, OrderProducts, Review, Cart, CartItem) + Role/OrderStatus enums
  security/          # JwtService, JwtAuthFilter, AuthenticatedUser, RefreshTokenService, TokenBlacklistService, IdempotencyService, LoginRateLimiterInterceptor
  services/          # Business logic (interface + Impl) — includes CartService, ProductImageUploadService, NotificationService (+ LoggingNotificationService stub)
  utils/             # ApiResponse wrapper, PageResponse, Pagination (clampSize / of / canonicalSort)

src/main/resources/
  application.yml
  db/migration/      # Flyway SQL (V1–V14)

newrelic/            # New Relic Java agent config (JAR is gitignored)
uploads/             # Runtime directory for product image uploads (gitignored)
Dockerfile           # Multi-stage Temurin 21 build
.github/workflows/   # CI (MySQL 8.4 + Redis 7 service containers, bootRun smoke test)
docker-compose.yml   # Local ELK stack + Redis + optional MySQL
```

---

## License / status

**NexusCommerce** is the application and Gradle project name (`spring.application.name` and New Relic `app_name` align with this). Add your own **LICENSE** and deployment runbooks as needed for how you ship or teach the project.
