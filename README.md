# NexusCommerce

NexusCommerce is a **Spring Boot** REST backend for a small e-commerce domain: **categories**, **products**, **orders** (with line items), and **reviews** tied to both an order and a product. It uses **MySQL**, **Flyway** for schema migrations, **JPA/Hibernate** with `ddl-auto: validate`, and **springdoc-openapi** for interactive API docs.

---

## Features

- **Categories** — Create, list, get by id, update, and delete categories.
- **Products** — CRUD-style operations, fetch with category details, search by category name, and list distinct category names from the catalogue.
- **Orders** — Create orders from a list of line items (product id + quantity), list/fetch/update/delete orders, and fetch an order **summary** (aggregated view).
- **Reviews** — Create reviews only when the product appears on the given order; at most **one review per (order, product)** pair; list reviews globally, by id, by product, or by order; delete by id.
- **Consistent JSON envelope** — All controllers return an `ApiResponse<T>` wrapper (`success`, `message`, `error`, `data`).
- **Global error handling** — Mapped HTTP statuses for not found, conflict, bad request, and generic server errors.
- **Database migrations** — Flyway applies versioned SQL under `src/main/resources/db/migration`.
- **API documentation** — OpenAPI 3 + Swagger UI (springdoc).

---

## Tech stack

| Area | Choice |
|------|--------|
| Runtime | Java **21** |
| Framework | Spring Boot **4.0.2** |
| Persistence | Spring Data JPA, Hibernate |
| Database | **MySQL** (Connector/J) |
| Migrations | **Flyway** (MySQL support) |
| API docs | **springdoc-openapi** 2.8.x (Swagger UI) |
| Build | **Gradle** |
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

- **Hibernate SQL** — `application.yml` sets `spring.jpa.show-sql: true`, so generated SQL appears in the application logs. This is useful for development; consider turning it **off** in production to reduce noise and avoid leaking query shape.
- **Spring Boot logging** — Default Logback/console logging applies for framework and application classes. Adjust levels via `logging.level.*` in `application.yml` or external config as needed.

### Not included (optional extensions)

The project does **not** currently add **Spring Boot Actuator** or **Micrometer**-exposed endpoints (for example `/actuator/health` or Prometheus scrape targets). You can add `spring-boot-starter-actuator` and secure exposure of endpoints if you want health checks, metrics, or integration with other backends (Prometheus, Grafana, and so on) alongside or instead of New Relic.

---

## Prerequisites

- **JDK 21**
- **MySQL** 8.x or compatible (project has been run against newer MySQL versions; Flyway may log a compatibility notice).
- A database named **`nexuscommerce`** (or adjust the JDBC URL in configuration).

---

## Configuration

Configuration lives in `src/main/resources/application.yml`.

- **Datasource** — `jdbc:mysql://localhost:3306/nexuscommerce` by default; credentials and server port are supplied via placeholders:
  - `username` — MySQL user
  - `password` — MySQL password
  - `PORT` — HTTP port for the embedded Tomcat server

The app imports an **optional** env file: `optional:file:.env[.properties]`. You can define the variables there (as Spring relaxed properties), for example:

```properties
username=your_mysql_user
password=your_mysql_password
PORT=8080
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

   On first startup, **Flyway** creates and updates tables (`V1`–`V3` migrations, including `reviews`).

4. **Run tests** (when you add or extend them):

   ```bash
   ./gradlew test
   ```

5. **OpenAPI / Swagger UI** (default springdoc paths; use your `PORT`):

   - Swagger UI: `http://localhost:<PORT>/swagger-ui/index.html`
   - OpenAPI JSON: `http://localhost:<PORT>/v3/api-docs`

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

### Categories — `/api/v1/categories`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/` | Create category (`CreateCategoryRequestDto`: `name`). |
| `GET` | `/` | List all categories. |
| `GET` | `/{id}` | Get category by id. |
| `PUT` | `/{id}` | Update category. |
| `DELETE` | `/{id}` | Delete category. |

### Products — `/api/v1/products`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | List products (summary DTOs). |
| `GET` | `/{id}` | Get product by id. |
| `GET` | `/{id}/details` | Get product with related category/details. |
| `POST` | `/` | Create product (`CreateProductRequestDto`: `title`, `description`, `price`, `image`, `categoryId`, `rating`). |
| `DELETE` | `/{id}` | Delete product. |
| `GET` | `/search?categoryName=…` | Find products by category name. |
| `GET` | `/uniqueCategories` | List unique category names present on products. |

### Orders — `/api/v1/orders`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | List orders. |
| `POST` | `/` | Create order (`CreateOrderRequestDto`: `orderItems` — each `OrderItemRequestDto`: `productId`, `quantity`). |
| `GET` | `/{id}` | Get order by id. |
| `PUT` | `/{id}` | Update order (`updateOrderRequestDto`). |
| `DELETE` | `/{id}` | Delete order. |
| `GET` | `/{id}/summary` | Order summary DTO. |

### Reviews — `/api/v1/reviews`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | List all reviews. |
| `POST` | `/` | Create review (`CreateReviewRequestDto`: `orderId`, `productId`, `rating`, optional `comment`). |
| `GET` | `/{id}` | Get review by id. |
| `DELETE` | `/{id}` | Delete review. |
| `GET` | `/product/{productId}` | Reviews for a product. |
| `GET` | `/order/{orderId}` | Reviews for an order. |

**Review rules (business logic):**

- `orderId` and `productId` must exist; the **product must be a line item** on that order.
- **Duplicate** reviews for the same `(orderId, productId)` are rejected (**409 Conflict**).
- `rating` is required, must be **greater than 0** and **at most 10** (see service validation).
- `comment` is optional; blank/whitespace-only comments are treated as no comment.

### Typical HTTP status codes (errors)

| Status | When |
|--------|------|
| `404` | Resource not found (e.g. missing id). |
| `409` | Duplicate resource (e.g. duplicate review for same order + product). |
| `400` | Invalid request (validation / business rule violations). |
| `500` | Unhandled exceptions (generic handler). |

Create/update endpoints may return **`201 Created`** or **`204 No Content`** where implemented in controllers.

---

## Project layout (high level)

```
src/main/java/com/example/nexusCommerce/
  adapters/          # Entity ↔ DTO mapping
  configs/           # OpenAPI / Swagger beans
  controllers/       # REST endpoints
  dtos/              # Request/response payloads
  exceptions/        # Custom exceptions + global handler
  repositories/      # Spring Data JPA
  schema/            # JPA entities
  services/          # Business logic
  utils/             # ApiResponse wrapper

src/main/resources/
  application.yml
  db/migration/      # Flyway SQL

newrelic/            # New Relic Java agent JAR + newrelic.yml (+ agent logs)
```

---

## License / status

**NexusCommerce** is the application and Gradle project name (`spring.application.name` and New Relic `app_name` align with this). Add your own **LICENSE** and deployment runbooks as needed for how you ship or teach the project.
