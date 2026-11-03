# Deployment runbook

Production topology (free-tier services unless noted):

```
Browser ──HTTPS──> Vercel (Next.js BFF) ──HTTPS──> Render (Spring Boot Docker)
                                                        │
                          ┌─────────────────────────────┼──────────────────────────┐
                          ▼                             ▼                          ▼
                  Supabase PostgreSQL           Upstash Redis            Supabase Storage
                  (schema nexuscommercedb)     (cache, refresh,         (S3 API, bucket
                                                rate limits)             nexus-commerce)
```

The browser only ever talks to the Vercel origin. The BFF holds the refresh token in an
httpOnly cookie and proxies every `/api/v1/**` call server-side, so no cross-site cookie
policy is involved. Render's URL is reachable directly (Swagger UI, Razorpay webhook).

## 1. Prerequisites

| Service | What you need | Where it goes |
|---|---|---|
| Supabase project | Project ref, database password, pooler host | `DB_URL` (pooler + `currentSchema`), `DB_USER`, `DB_PASSWORD` |
| Supabase Storage | Bucket, S3 endpoint, S3 access key/secret, public base URL | `S3_*` |
| Upstash Redis | Full `rediss://` URL (port included) | `REDIS_URL` |
| Render | GitHub access to `nexus-commerce` | Blueprint deploy |
| Vercel | GitHub access to `nexus-commerce-frontend` | Project import |
| Razorpay | Test key id/secret, webhook secret | `RAZORPAY_*` |

Never put real values in `render.yaml`, `.env.example`, a commit or a chat. The Render and
Vercel dashboards are where secrets are typed.

## 2. Managed services

### Supabase PostgreSQL

The app owns schema **`nexuscommercedb`** inside the project's `postgres` database, so an
unrelated schema in the same project is untouched. Flyway applies
`classpath:db/migration-postgres` from V1 on first boot.

**Connect through the Supavisor session pooler, not the direct host.** Supabase's direct
hostname (`db.<ref>.supabase.co`) publishes an IPv6-only address; many networks (including
macOS dev machines and some PaaS containers) cannot reach it. The pooler is IPv4.

In the Supabase console → **Connect** → *Session pooler*, copy the host and use the
`postgres.<project-ref>` username:

```properties
DB_URL=jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=nexuscommercedb&connectTimeout=15&socketTimeout=120&tcpKeepAlive=true
DB_USER=postgres.<project-ref>
DB_PASSWORD=<rotated database password>
DB_DRIVER=org.postgresql.Driver
FLYWAY_LOCATIONS=classpath:db/migration-postgres
DB_POOL_SIZE=10
DB_MIN_IDLE=1
```

Why the extra parameters matter:

- `currentSchema=nexuscommercedb` — Flyway and Hibernate must both resolve into that schema
  (the history table lands at `nexuscommercedb.flyway_schema_history`).
- `connectTimeout` / `socketTimeout` / `tcpKeepAlive` — a pooler can close a session while
  the JDBC driver is blocked on a read; without a socket timeout that read never returns and
  boot hangs mid-migration.
- `DB_MIN_IDLE=1` — Hikari's default of five idle connections gets reaped by the pooler and
  produces a stream of "Failed to validate connection … closed" warnings.

The pooler also caps concurrent sessions per project; ten is enough for one instance and
leaves room for Flyway and a psql session. Session mode is used (not transaction mode)
because Flyway and Hibernate advisories rely on session state.

### Upstash Redis

Create a Regional database (free tier), TLS enabled by default. Copy the single connection
URL and keep the `:6379` port — the console's URL sometimes omits it, and Boot cannot infer
it (the app fails to start with `Port out of range: -1`).

```properties
REDIS_URL=rediss://default:<password>@<name>.upstash.io:6379
REDIS_TIMEOUT=3s
REDIS_CONNECT_TIMEOUT=5s
```

Lettuce applies `timeout` to connection initialisation (TLS handshake included), not just to
commands. The local-dev default of 500 ms is too short for a Redis on another continent and
shows up as `RedisConnectionException: Unable to connect` plus cache misses that silently
fall back to the database.

Redis holds refresh-token state and the cache. Losing it logs every session out; it is not
the source of truth for anything else.

### Supabase Storage

Supabase exposes an S3-compatible endpoint. The bucket is public because it serves product
photography; do not put anything private in it.

1. Storage → **New bucket** → `nexus-commerce`, public.
2. Storage → **S3 access keys** → create a key pair. The secret is shown once.
3. Endpoint: `https://<project-ref>.storage.supabase.co/storage/v1/s3`
4. Region: the project's region, e.g. `ap-northeast-1`.
5. Public base URL: `https://<project-ref>.supabase.co/storage/v1/object/public/nexus-commerce`

```properties
STORAGE_TYPE=s3
S3_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
S3_REGION=ap-northeast-1
S3_BUCKET=nexus-commerce
S3_ACCESS_KEY=<S3 access key id>
S3_SECRET_KEY=<S3 secret>
S3_PUBLIC_BASE_URL=https://<project-ref>.supabase.co/storage/v1/object/public/nexus-commerce
S3_KEY_PREFIX=products
```

Uploads are written with `Cache-Control: public, max-age=31536000, immutable`; keys are
UUIDs, so a URL never changes content. Deleting a gallery image deletes the object after the
transaction commits. The public CDN can still answer a deleted URL from cache for a short
window; that is expected.

## 3. Render (backend)

1. Render Dashboard → **New → Blueprint**, pick `Priyank384/nexus-commerce`, branch `dev`.
   Render reads `render.yaml` and creates the `nexus-commerce-api` web service.
2. Fill every `sync: false` variable. Two that are easy to get wrong:
   - `JWT_SECRET` — generate a fresh one: `openssl rand -base64 48`. It must be **≥ 32
     bytes**; JJWT rejects shorter HMAC-SHA keys and the app aborts at startup with a
     `JWT_SECRET must be at least 32 bytes` message. Rotating it signs every user out.
   - `CORS_ALLOWED_ORIGINS` — comma-separated **Vercel URL and the Render URL itself**:
     `https://nexus-commerce.vercel.app,https://nexus-commerce-api.onrender.com`.
     Swagger UI posts from the API's own origin and is subject to the same filter.
3. Leave `PORT` unset; Render injects it and `server.port: ${PORT}` picks it up.
4. Deploy. First boot runs the Flyway Postgres migrations, then `ddl-auto: validate` proves
   the entities match.

**Free-plan caveats:** the service idles after 15 minutes (~50 s cold start), has 512 MB of
RAM and 0.1 CPU. The blueprint sets `JAVA_TOOL_OPTIONS` for serial GC, a 70 % heap cap and
`-Djava.net.preferIPv4Stack=true` (the JVM otherwise prefers IPv6 records the container
cannot route). Move to `plan: starter` before sharing the URL with real shoppers.

### First-boot ADMIN

The admin surface needs an ADMIN account, and the bootstrap runner is deliberately not
something to leave configured:

1. Set `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` and (optionally)
   `ADMIN_BOOTSTRAP_NAME` in the Render dashboard, then deploy.
2. Log in through the storefront or Swagger as that account and confirm `GET /api/v1/admin/metrics`
   answers 200.
3. **Blank all three variables and redeploy.** The runner is inert unless both email and
   password are non-empty, so a blank pair can never resurrect the account.

### Transactional email

Order confirmations and password-reset links are the only two things this backend mails, and
both are **silently logged instead of sent** until a relay is configured — a live storefront
can take paid orders and deliver nothing to anyone's inbox without any error surfacing.

1. Pick an SMTP-capable provider (Resend, Postmark, SES, Mailgun all speak SMTP) and verify
   the sending **domain**, not just the address. `MAIL_FROM` must be on that domain or the
   relay accepts the message and spam filters do not.
2. In Render, set `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT` (`587`), `MAIL_USERNAME`,
   `MAIL_PASSWORD`, `MAIL_FROM` and `STOREFRONT_BASE_URL`.
3. `STOREFRONT_BASE_URL` must be the **deployed storefront origin**. Every link in every email
   is built from it; leave it at the `http://localhost:3000` default and receipts and reset
   links point at the recipient's own machine.
4. Deploy and read the startup log. `NotificationConfig` states which sender it chose —
   `Transactional email enabled from … to …`, or a warning that `app.mail.enabled=true` but no
   host was found. That line is the only proof the wiring took.
5. Request a reset for an address you control (`POST /api/v1/auth/forgot-password`) and confirm
   the mail arrives and the link completes.

**Why a dead relay does not fail a request:** sending is best-effort. The callers are an
after-commit event listener and the payment-capture path, both of which have already durably
recorded the money, so a transport error is logged and swallowed. Turning that into an
exception would make Razorpay retry a webhook whose settle guard now answers "already
settled" — and the receipt would never send at all. For the same reason the Actuator **mail**
health contributor stays disabled; a probe that flips the app to `DOWN` for a condition the
code deliberately tolerates would page on noise.

## 4. Vercel (frontend)

1. Import `nexus-commerce-frontend`, framework preset **Next.js**, branch `main`.
2. Environment variables (Production + Preview):

   | Name | Value |
   |---|---|
   | `API_BASE_URL` | `https://nexus-commerce-api.onrender.com` |
   | `NEXT_IMAGE_HOSTS` | `<project-ref>.supabase.co` (the Storage public hostname, no scheme) |
   | `NEXT_PUBLIC_SITE_URL` | `https://<your-domain>` — canonical origin for canonicals, Open Graph, `sitemap.ts`, `robots.ts` and JSON-LD |
   | `NEXT_PUBLIC_SUPPORT_EMAIL` | An address someone actually answers. `/contact` publishes it only when set; unset, the page says so plainly instead of printing an inbox nobody reads |

   `STOREFRONT_BASE_URL` on the Render side and `NEXT_PUBLIC_SITE_URL` here must be the **same
   origin** — the backend builds every emailed link from the former, so a mismatch sends
   shoppers to a URL that is not the live store.

3. Deploy. `next/image` remote patterns are baked at build time, so changing
   `NEXT_IMAGE_HOSTS` requires a redeploy. `NEXT_PUBLIC_SITE_URL` falls back to Vercel's
   production URL, but set it explicitly so previews and the sitemap never disagree.

The seed catalogue in V21 points at `picsum.photos` (allow-listed in `next.config.ts`).
Replace those rows with uploads to Supabase Storage before treating the catalogue as real.

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
2. `GET https://<render>/api/v1/products?size=3` → 200 with catalogue rows; a second call
   should be fast enough to prove the Redis cache is working.
3. Open the storefront → home renders products with images (proves Storage + `next/image`).
4. Register a shopper, add a variant to the bag, check out with the test card → order shows
   **Paid** in `/orders`.
5. Razorpay dashboard → the test payment appears; webhook delivery shows 2xx.
6. Admin: log in, upload an image on a product, confirm the returned URL is on
   `<project-ref>.supabase.co/storage/v1/object/public/nexus-commerce/` and that the
   storefront renders it; delete a gallery image and confirm the object is gone
   (cache-bust the URL before judging).
7. Restart the Render service and confirm the catalogue, orders and sessions survive (the
   DB, Redis and Storage are external; only the local disk would be lost, and nothing
   lives there).
8. **Guest path** — sign out, add a variant to the bag, check out with an email you control
   and the test card. The bag must survive the reload, the receipt must arrive, and the
   claim link in it must attach the order to a fresh account. Then reload `/cart`: a guest
   bag belongs to the browser, so it must be empty once that session has been merged.
9. **Mail round trip** — `POST /api/v1/auth/forgot-password` for a real address, click the
   link, sign in with the new password. A second use of the same link must answer 400. If
   nothing arrives, read the startup log for which notification sender was chosen before
   debugging the relay.
10. **Trust pages** — `/returns`, `/shipping`, `/size-guide` and `/contact` answer 200 signed
    out, and `/contact` shows the support address you set (or the "no published inbox" note,
    if you did not). These pages state merchant promises taken from
    `src/lib/content/policy.ts` on the frontend; confirm the numbers are ones the business
    intends to keep.

## 7. Operations

- **Deploy:** push to `dev` (backend) / `main` (frontend); both platforms auto-deploy.
- **Migrations:** run automatically on boot. Never edit an applied migration — add a new one.
- **Schema:** everything lands in `nexuscommercedb`. Rotating projects means changing
  `DB_URL`/`DB_USER` and letting Flyway rebuild the schema.
- **Logs:** Render → Logs; New Relic is optional and needs its agent mounted into the image
  plus `NEW_RELIC_LICENSE_KEY` (rotate the key that appears in git history first).
- **Scaling past one instance:** Supabase, Upstash and Storage are all external, so multiple
  instances are safe; watch the pooler's session cap when raising `DB_POOL_SIZE`.
- **Cache flush:** Redis is disposable — restart Upstash or bump keys, the cache repopulates.

## 8. Known limitations

- Razorpay stays in **test mode**; no real money moves and live keys are not configured.
- Render free tier sleeps; first request after idle is slow.
- No custom domain yet — the `*.vercel.app` / `*.onrender.com` hosts are used as-is.
- The Supabase Storage bucket is public. That is intentional for product photography; do not
  store private files there.
- Soft-deleting a product leaves its gallery objects in the bucket; deleting a gallery row
  does remove the object.
- If the pooler host changes (Supabase occasionally moves poolers between regions), update
  `DB_URL`; the direct host is not a workaround.
