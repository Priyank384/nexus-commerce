# NexusCommerce

> **Full project guide:** [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md) — setup, tech stack, architecture, API flows, payloads, Redis keys, real-life scenarios, and testing checklists.

NexusCommerce is a **Spring Boot** REST backend for a small e-commerce domain: **categories**, **products** (with stock/inventory), **orders** (with line items), and **reviews** tied to both an order and a product. It is secured with **JWT authentication and role-based authorization** (`USER` / `ADMIN`), backed by **MySQL**, versioned by **Flyway** migrations with `ddl-auto: validate`, cached in **Redis**, and documented with **springdoc-openapi**.

---

## Features

- **Auth & roles** — Register (`POST /api/v1/users`), log in (`POST /api/v1/auth/login`) to receive a stateless **JWT bearer token**; two roles, `USER` and `ADMIN`, gate every endpoint (see [Authentication & roles](#authentication--roles)).
- **Categories** — Create, list (paged), get by id, update, and delete categories. Deleting a category that still holds live products is rejected.
- **Products** — CRUD-style operations, fetch with category details, search by category name, list distinct category names, and per-product **stock** tracking.
- **Inventory** — Placing an order atomically decrements stock (oversell-safe); admins restock via `PUT /api/v1/admin/products/{id}/stock`.
- **Orders** — Create orders from a list of line items (product id + quantity), list/fetch/update/delete orders, fetch an order **summary**, and read **your own orders** (`/me`) or any user's (admin). Line items are **snapshotted** (price, name, image) so history never changes when the catalogue is edited.
- **Reviews** — Create reviews only when the product appears on the given order; at most **one review per (order, product)** pair; list reviews globally, by id, by product, or by order; delete by id (admin).
- **Admin dashboard** — Aggregate business metrics, paged user listing, and user role/enable management (all `ROLE_ADMIN`).
- **Data integrity** — Soft-delete on every entity, `@Version` optimistic locking on orders, and DB-level uniqueness that survives soft-deletes (see [Data integrity & migrations](#data-integrity--migrations)).
- **Consistent JSON envelope** — All controllers return an `ApiResponse<T>` wrapper (`success`, `message`, `error`, `data`); paged endpoints wrap a `PageResponse<T>`.
- **Global error handling** — Mapped HTTP statuses for not found, conflict, bad request, unauthorized, forbidden, and generic server errors.
- **Database migrations** — Flyway applies versioned SQL (`V1`–`V12`) under `src/main/resources/db/migration`.
- **API documentation** — OpenAPI 3 + Swagger UI (springdoc), with a bearer-authorization scheme pre-wired.

---

## Tech stack

| Area | Choice |
|------|--------|
| Runtime | Java **21** |
| Framework | Spring Boot **4.0.2** |
| Persistence | Spring Data JPA, Hibernate |
| Security | **Spring Security** (stateless) + **JWT** (`jjwt`), BCrypt password hashing |
| Database | **MySQL** (Connector/J) |
| Migrations | **Flyway** (MySQL support) |
| API docs | **springdoc-openapi** 2.8.x (Swagger UI) |
| Build | **Gradle** |
| Cache | **Redis** (Spring cache-aside on GET; see [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md#10-redis-caching)) |
| APM / monitoring | **New Relic** Java agent (see [Observability and monitoring](#observability-and-monitoring)) |

---

## Observability and monitoring

NexusCommerce is wired for **production-style visibility** primarily through the **New Relic Java APM agent**, plus standard Spring and JPA logging.

### New Relic (APM)

- **How it runs** — `./gradlew bootRun` starts the JVM with `-javaagent:newrelic/newrelic.jar` (see `build.gradle`). The agent lives under the `newrelic/` directory next to the agent JAR and `newrelic.yml`.
- **Application name** — In `newrelic/newrelic.yml`, `app_name` is **`NexusCommerce`**, so traces and metrics roll up under that name in the New Relic UI.
- **What you get** — Out-of-the-box instrumentation for typical Spring WebMVC + Tomcat request paths, downstream **JDBC/MySQL** calls, JVM and transaction timing, errors, and (depending on account/features) distributed tracing headers compatible with New Relic’s collector.
- **Agent logs** — The agent writes its own file (by default under `newrelic/logs/`, e.g. `newrelic_agent.log`) for startup, collector connectivity, and troubleshooting—separate from your application log output.
- **Secrets** — Configure your **license key** via `newrelic.yml` or environment variables supported by the agent (for example `NEW_RELIC_LICENSE_KEY`). Prefer **environment or secret manager** in shared or production environments and avoid committing real keys to version control.

To run **without** the agent (local-only), use a plain Java launch or override `bootRun` JVM args in your own Gradle snippet or IDE run configuration so the `-javaagent` flag is omitted.

### Application and SQL logging

- **Hibernate SQL** — `application.yml` sets `spring.jpa.show-sql: false` (statement logging goes through the `org.hibernate.SQL` logger instead), keeping the hot path free of synchronized `System.out` writes. Enable `show-sql` only for local debugging.
- **Spring Boot logging** — Default Logback/console logging applies for framework and application classes. Adjust levels via `logging.level.*` in `application.yml` or external config as needed.

### Not included (optional extensions)

The project does **not** currently add **Spring Boot Actuator** or **Micrometer**-exposed endpoints (for example `/actuator/health` or Prometheus scrape targets). You can add `spring-boot-starter-actuator` and secure exposure of endpoints if you want health checks, metrics, or integration with other backends (Prometheus, Grafana, and so on) alongside or instead of New Relic.

---

## Prerequisites

- **JDK 21**
- **MySQL** 8.x or compatible (project has been run against newer MySQL versions; Flyway may log a compatibility notice).
- A database named **`nexuscommerce`** (or adjust the JDBC URL in configuration).
- **Redis** reachable at `localhost:6379` (or `redis_port` in `.env`) — the app uses it for read-through caching of GET endpoints.

---

## Configuration

Configuration lives in `src/main/resources/application.yml`.

- **Datasource** — `jdbc:mysql://localhost:3306/nexuscommerce` by default; credentials and server port are supplied via placeholders:
  - `username` — MySQL user
  - `password` — MySQL password
  - `PORT` — HTTP port for the embedded Tomcat server
  - `redis_port` — Redis port (host defaults to `localhost:6379`)
  - `JWT_SECRET` — HMAC signing secret, **at least 32 bytes** (a dev-only default is baked in; always override outside local dev)
  - `JWT_EXPIRATION_MINUTES` — token lifetime in minutes (default `120`)

The app imports an **optional** env file: `optional:file:.env[.properties]`. You can define the variables there (as Spring relaxed properties), for example:

```properties
username=your_mysql_user
password=your_mysql_password
PORT=3001
redis_port=6379
JWT_SECRET=change-me-to-a-long-random-secret-at-least-32-bytes
JWT_EXPIRATION_MINUTES=120
```

If you do not use `.env`, set the same properties via environment variables or any other [Spring Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) mechanism.

---

## Setup

1. **Create the database** (once):

   ```sql
   CREATE DATABASE nexuscommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Configure** `username`, `password`, and `PORT` as described above.

3. **Run the application** from the project root:

   ```bash
   ./gradlew bootRun
   ```

   On first startup, **Flyway** creates and updates tables (migrations `V1`–`V12`, including `reviews`, `users`, roles, stock, and the order line-item snapshots).

   **Redis** must be running on the host/port in `.env` (`redis_port`, default `6379`).

4. **Run tests** (when you add or extend them):

   ```bash
   ./gradlew test
   ```

5. **OpenAPI / Swagger UI** (default springdoc paths; use your `PORT`):

   - Swagger UI: `http://localhost:<PORT>/swagger-ui/index.html`
   - OpenAPI JSON: `http://localhost:<PORT>/v3/api-docs`

---

## Authentication & roles

The API is **stateless JWT**. Log in to receive a token, then send it as `Authorization: Bearer <token>` on every protected call. Swagger UI has a **Authorize** button wired to the bearer scheme, so you can paste the token once and try any endpoint.

**Getting a token**

1. Register (public): `POST /api/v1/users` with `name`, `email`, `password` (min 8 chars), optional `phone`. New accounts get role `USER`.
2. Log in (public): `POST /api/v1/auth/login` with `email` + `password`. The response carries:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-21T12:00:00Z",
  "user": { "id": 4, "email": "…", "role": "USER", "enabled": true }
}
```

**Token shape** — HS-signed (algorithm chosen from `JWT_SECRET` length; the bundled dev secret yields HS512). Claims: `sub` = email, `uid` = user id, `role`. Expires after `JWT_EXPIRATION_MINUTES` (default 120). Passwords are stored **BCrypt-hashed**; the secret and hash never leave the server.

**Two roles** — `USER` (default) and `ADMIN`. Authorization is enforced by path + method matchers in `SecurityConfig`, plus ownership checks in `OrderController`.

| Access | What |
|--------|------|
| **Public** (no token) | Register, login, Swagger, and **browsing**: `GET` products, categories, reviews. |
| **Any authenticated user** | Place an order, create a review, and read **your own** orders (`/orders/me`, `/orders/{id}`, `/orders/{id}/summary`) — order read/update is scoped so you can only touch orders you own. |
| **`ROLE_ADMIN` only** | All catalogue **writes** (create/update/delete products & categories), review **deletion**, the order **list** (`GET /orders`) and order **deletion**, the whole `/api/v1/admin/**` surface, and viewing any user's orders. |

**Errors** — Missing/invalid token on a protected route → `401` (`"Unauthorized!"`); valid token but wrong role / not the owner → `403` (`"Forbidden!"`).

> **Bootstrapping the first admin:** accounts register as `USER`. Promote an admin by updating that row's `role` to `ADMIN` in the database (or via an existing admin using `PATCH /api/v1/admin/users/{id}`), then log in again — the role is carried in the token.

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

The **Access** column below shows the minimum role required.

### Auth — `/api/v1/auth` & `/api/v1/users`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/users` | Public | Register (`CreateUserRequestDto`: `name`, `email`, `password` ≥8, optional `phone`). |
| `POST` | `/api/v1/auth/login` | Public | Log in (`LoginRequestDto`: `email`, `password`) → JWT bearer token. |

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

### Orders — `/api/v1/orders`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/` | User | Create order (`CreateOrderRequestDto`: `orderItems` — each `OrderItemRequestDto`: `productId`, `quantity`); places it on the authenticated caller and decrements stock. |
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

**Review rules (business logic):**

- `orderId` and `productId` must exist; the **product must be a line item** on that order.
- **Duplicate** reviews for the same `(orderId, productId)` are rejected (**409 Conflict**) — enforced by a DB unique constraint on live reviews.
- `rating` is required, must be **greater than 0** and **at most 10** (service validation).
- `comment` is optional; blank/whitespace-only comments are treated as no comment.

### Typical HTTP status codes (errors)

| Status | When |
|--------|------|
| `401` | Protected route called without a valid token. |
| `403` | Valid token, but wrong role or not the resource owner. |
| `400` | Invalid request (validation / business-rule violations). |
| `404` | Resource not found (e.g. missing id). |
| `409` | Duplicate resource (e.g. duplicate review for same order + product). |
| `500` | Unhandled exceptions (generic handler). |

Create/update endpoints may return **`201 Created`** or **`204 No Content`** where implemented in controllers.

---

## Data integrity & migrations

Several guarantees are worth knowing before you touch the domain:

- **Soft delete everywhere** — Every entity uses `@SQLDelete` + `@SQLRestriction(deleted_datetime IS NULL)`. A "delete" is an UPDATE that stamps `deleted_datetime`; restricted reads then hide the row without losing it.
- **Optimistic locking on orders** — `Order` carries a `@Version` field (added in `V8`), so concurrent updates to the same order collide predictably instead of silently overwriting. Its soft-delete SQL is version-guarded too.
- **Oversell-safe stock** — Placing/adjusting an order decrements stock with an atomic conditional bulk UPDATE (`WHERE stock >= qty`); zero affected rows means insufficient stock and the whole transaction rolls back. Returning stock on delete/remove is likewise atomic.
- **Historical line-item snapshots** — Order line items store `unit_price` (`V11`) plus `product_name` and `product_image` (`V12`). Editing a price, renaming a product, or soft-deleting it therefore **cannot** rewrite historical orders or revenue, and a deleted product's line item still renders.
- **Uniqueness that survives soft-delete** — Flyway generated columns mirror the unique key only for live rows (`V8` unique names, `V10` one live review per `(order, product)`), so a deleted row frees the slot without a partial unique index.
- **Migrations are the source of truth** — `spring.jpa.hibernate.ddl-auto: validate` means Hibernate only checks the schema; **Flyway** (`V1`–`V12` under `src/main/resources/db/migration`) owns it. Add a new `V__` file for any column change — never edit an applied migration.

| Migration | Purpose |
|-----------|---------|
| `V1`–`V4` | Init schema, decimal rating, reviews table, product price index. |
| `V5`–`V6` | `users` table + order→user link; `password_hash` on users. |
| `V7`–`V8` | Lookup indexes; order `version` + soft-delete-safe unique names. |
| `V9` | `users.role` + `products.stock`. |
| `V10` | DB unique on live `(order_id, product_id)` reviews. |
| `V11` | `order_products.unit_price` snapshot (backfilled). |
| `V12` | `order_products.product_name` / `product_image` snapshot (backfilled). |

---

## Project layout (high level)

```
src/main/java/com/example/nexusCommerce/
  adapters/          # Entity ↔ DTO mapping
  configs/           # Security / cache / OpenAPI / auditing / password-encoder beans
  controllers/       # REST endpoints
  dtos/              # Request/response payloads
  exceptions/        # Custom exceptions + global handler
  repositories/      # Spring Data JPA
  schema/            # JPA entities (+ Role/OrderStatus enums)
  security/          # JwtService, JwtAuthFilter, AuthenticatedUser
  services/          # Business logic
  utils/             # ApiResponse wrapper, PageResponse, Pagination

src/main/resources/
  application.yml
  db/migration/      # Flyway SQL (V1–V12)

newrelic/            # New Relic Java agent JAR + newrelic.yml (+ agent logs)
```

---

## License / status

**NexusCommerce** is the application and Gradle project name (`spring.application.name` and New Relic `app_name` align with this). Add your own **LICENSE** and deployment runbooks as needed for how you ship or teach the project.
