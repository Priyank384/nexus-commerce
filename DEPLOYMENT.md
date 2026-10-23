# Deployment runbook

Production topology (all free-tier services except where noted):

```
Browser ──HTTPS──> Vercel (Next.js BFF) ──HTTPS──> Render (Spring Boot Docker)
                                                        │
                          ┌─────────────────────────────┼──────────────────────────┐
                          ▼                             ▼                          ▼
                  Aiven PostgreSQL              Upstash Redis              Cloudflare R2
                  (catalogue, orders)         (sessions, cache,         (product images,
                                                rate limits)              public bucket)
```

The browser only ever talks to the Vercel origin. The BFF holds the refresh token in an
httpOnly cookie and proxies every `/api/v1/**` call server-side, so no cross-site cookie
policy is involved. Render's URL is reachable directly (Swagger UI, Razorpay webhook).

## 1. Prerequisites

| Service | What you need | Where it goes |
|---|---|---|
| Aiven PostgreSQL | Service URL, user, password, CA optional | `DB_URL` (with `sslmode=require`), `DB_USER`, `DB_PASSWORD` |
| Upstash Redis | Full `rediss://` URL (port included) | `REDIS_URL` |
| Cloudflare R2 | Bucket, S3 endpoint, access key/secret, public base URL | `S3_*` |
| Render | GitHub access to `nexus-commerce` | Blueprint deploy |
| Vercel | GitHub access to `nexus-commerce-frontend` | Project import |
| Razorpay | Test key id/secret, webhook secret | `RAZORPAY_*` |

Never put real values in `render.yaml`, `.env.example`, a commit or a chat. The flows below
assume the Render dashboard is where secrets are typed.

## 2. Managed services

### Aiven PostgreSQL

Reuse the existing service. The app applies pending Flyway migrations from
`classpath:db/migration-postgres` on boot, so an instance last migrated at V21 will pick up
V22–V26 automatically.

Take the JDBC URL from the Aiven console and append `?sslmode=require` (Aiven terminates
TLS). Pinning the CA instead means shipping `service-ca.pem` into the image — not worth it
for this deployment.

```properties
DB_URL=jdbc:postgresql://<host>:<port>/defaultdb?sslmode=require
DB_USER=avnadmin
DB_PASSWORD=<rotated password>
DB_DRIVER=org.postgresql.Driver
FLYWAY_LOCATIONS=classpath:db/migration-postgres
DB_POOL_SIZE=10
```

The Aiven `avnadmin` password is still the one that has appeared outside the console —
rotate it before pointing production at the service.

### Upstash Redis

Create a Regional database (free tier), TLS enabled by default. Copy the single connection
URL and keep the `:6379` port — the console's URL sometimes omits it, and Boot cannot infer
it (the app fails to start with `Port out of range: -1`).

```properties
REDIS_URL=rediss://default:<password>@<name>.upstash.io:6379
```

Redis holds refresh-token state and the cache. Losing it logs every session out; it is not
the source of truth for anything else.

### Cloudflare R2

1. Create a bucket (e.g. `plain-thread-media`).
2. Settings → Public access: enable the `r2.dev` subdomain, or attach a custom domain.
3. Manage R2 API Tokens → create a token with **Object Read & Write** on that bucket.
4. Note the S3 endpoint: `https://<account-id>.r2.cloudflarestorage.com`.

```properties
STORAGE_TYPE=s3
S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
S3_REGION=auto
S3_BUCKET=plain-thread-media
S3_ACCESS_KEY=<token access key id>
S3_SECRET_KEY=<token secret>
S3_PUBLIC_BASE_URL=https://pub-xxxxxxxx.r2.dev
S3_KEY_PREFIX=products
```

Uploads are written with `Cache-Control: public, max-age=31536000, immutable`; keys are
UUIDs, so a URL never changes content. Gallery deletes also delete the object.

## 3. Render (backend)

1. Render Dashboard → **New → Blueprint**, pick `Priyank384/nexus-commerce`, branch `dev`.
   Render reads `render.yaml` and creates the `nexus-commerce-api` web service.
2. Fill every `sync: false` variable. Two that are easy to get wrong:
   - `JWT_SECRET` — generate a fresh one: `openssl rand -base64 48`.
   - `CORS_ALLOWED_ORIGINS` — comma-separated **Vercel URL and the Render URL itself**:
     `https://plain-thread.vercel.app,https://nexus-commerce-api.onrender.com`.
     Swagger UI posts from the API's own origin and is subject to the same filter.
3. Leave `PORT` unset; Render injects it and `server.port: ${PORT}` picks it up.
4. Deploy. First boot runs the Flyway Postgres migrations, then `ddl-auto: validate` proves
   the entities match.

**Free-plan caveats:** the service idles after 15 minutes (~50 s cold start), has 512 MB of
RAM and 0.1 CPU. The blueprint sets `JAVA_TOOL_OPTIONS` for serial GC and a 70 % heap cap.
Move to `plan: starter` before sharing the URL with real shoppers.

### First-boot ADMIN

The admin surface needs an ADMIN account, and the bootstrap runner is deliberately not
something to leave configured:

1. Set `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` and (optionally)
   `ADMIN_BOOTSTRAP_NAME` in the Render dashboard, then deploy.
2. Log in through the storefront or Swagger as that account and confirm `GET /api/v1/admin/metrics`
   answers 200.
3. **Blank all three variables and redeploy.** The runner is inert unless both email and
   password are non-empty, so a blank pair can never resurrect the account.

## 4. Vercel (frontend)

1. Import `nexus-commerce-frontend`, framework preset **Next.js**, branch `main`.
2. Environment variables (Production + Preview):

   | Name | Value |
   |---|---|
   | `API_BASE_URL` | `https://nexus-commerce-api.onrender.com` |
   | `NEXT_IMAGE_HOSTS` | `pub-xxxxxxxx.r2.dev` (the R2 public hostname, no scheme) |

3. Deploy. `next/image` remote patterns are baked at build time, so changing
   `NEXT_IMAGE_HOSTS` requires a redeploy.

The BFF cookie is `Secure` automatically because Vercel serves HTTPS, and stays on the
Vercel origin — the backend's domain never sees it.

## 5. Razorpay (test mode)

Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` in Render.

In the Razorpay dashboard (Test mode) → Settings → Webhooks, add:

```
https://nexus-commerce-api.onrender.com/api/v1/payments/razorpay/webhook
```

Subscribe to `payment.captured`, `order.paid`, `payment.failed` and `payment.process_failed`
— those are the events the handler settles. The webhook secret must match
`RAZORPAY_WEBHOOK_SECRET` exactly. Test card: `4111 1111 1111 1111`.

Going live later means KYC, live keys and a new webhook secret; nothing in the code changes.

## 6. Smoke checklist

Run after every deploy, against the deployed URLs:

1. `GET https://<render>/actuator/health` → `{"status":"UP"}` (DB + Redis indicators).
2. `GET https://<render>/api/v1/products?size=3` → 200 with catalogue rows.
3. Open the storefront → home renders products with images (proves R2 + `next/image`).
4. Register a shopper, add a variant to the bag, check out with the test card → order shows
   **Paid** in `/orders`.
5. Razorpay dashboard → the test payment appears; webhook delivery shows 2xx.
6. Admin: log in, upload an image on a product, confirm the returned URL is on the R2 host
   and that the storefront renders it; delete a gallery image and confirm the object is gone.
7. Restart the Render service and confirm the catalogue, orders and sessions survive (the
   DB and Redis are external; only the local disk would be lost, and nothing lives there).

## 7. Operations

- **Deploy:** push to `dev` (backend) / `main` (frontend); both platforms auto-deploy.
- **Migrations:** run automatically on boot. Never edit an applied migration — add a new one.
- **Logs:** Render → Logs; New Relic is optional and needs its agent mounted into the image
  plus `NEW_RELIC_LICENSE_KEY` (rotate the key that appears in git history first).
- **Scaling past one instance:** R2 and the external Redis already make the app stateless,
  so multiple instances are safe; nothing depends on local disk.
- **Cache flush:** Redis is disposable — restart Upstash or bump keys, the cache repopulates.

## 8. Known limitations

- Razorpay stays in **test mode**; no real money moves and live keys are not configured.
- Render free tier sleeps; first request after idle is slow.
- No custom domain yet — the `*.vercel.app` / `*.onrender.com` hosts are used as-is.
- The R2 public bucket serves images to anyone with the URL. That is intentional for product
  photography; do not store private files there.
- Deleting a product does not garbage-collect its gallery objects: replacing a thumbnail
  removes the old object, but a soft-deleted product's files stay in the bucket.
