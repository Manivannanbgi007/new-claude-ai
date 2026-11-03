# API Gateway Resilience

The gateway protects the platform with five patterns. They are applied to every API route **in this
order**: the cheapest check first, and the backend only called when everything else allows it.

```
client ─► CorrelationId ─► AccessLog ─► ① RequestRateLimiter ─► ② Bulkhead ─► ③ CircuitBreaker ─► ④ Retry ─► backend
                                          │ 429                   │ 503         │ open/timeout/5xx   (GET only)
                                          ▼                       ▼             ▼
                                     "too many requests"     "service busy"  ⑤ /fallback/<name>
```

| # | Pattern | Question it answers | Answer when "no" |
|---|---|---|---|
| ① | Rate limiting | Is this **client** sending too much? | `429 Too Many Requests` |
| ② | Bulkhead | Does this **route** have free capacity right now? | `503 Service busy` + `Retry-After: 2` |
| ③ | Circuit breaker + timeout | Is this **backend** healthy and fast enough? | Fallback |
| ④ | Retry | Was that failure just a short glitch? | Tries again (GET only) |
| ⑤ | Fallback routing | Can we still give a useful answer? | Alternative source, or a clear 503 |

**Why this order?**

- **The rate limiter comes first.** It costs one Redis call, and rejecting floods before they take capacity
  protects everything behind it.
- **The bulkhead comes before the circuit breaker.** A full bulkhead is *our* capacity limit, not a backend
  failure, so it must never open a circuit.
- **Retry sits *inside* the circuit breaker.** When the circuit is open, the breaker answers immediately, and a
  dead service is never hammered with retries. While it's closed, the breaker sees the *final* result after
  retries.

---

## 1. What was added

| File | Purpose |
|---|---|
| `pom.xml` | `spring-cloud-starter-circuitbreaker-reactor-resilience4j`, `spring-boot-starter-data-redis-reactive` |
| `application.yml` | All routes with the 4 filters, timeouts, and circuit breaker values (`app.resilience.*`) |
| `resilience/ResilienceProperties` | Typed `app.resilience.*` values |
| `resilience/ResilienceConfig` | Builds Resilience4j circuit breakers + time limiters from the YAML values |
| `resilience/RateLimitKeyConfig` | `clientKeyResolver`: one bucket per user (`X-User-Id`) or per IP |
| `resilience/BulkheadRegistry`, `filter/BulkheadGatewayFilterFactory` | The `Bulkhead` route filter |
| `controller/FallbackController` | `/fallback/<name>`: fallback routing and clear 503s |
| `exception/GatewayRejectionException` + handler update | Bulkhead rejections as ProblemDetail + `Retry-After` |
| `monitoring/ResilienceEndpoint` | `GET /actuator/resilience`: live circuit breaker and bulkhead state |
| `scripts/resilience-tests.ps1`, `http/resilience.http` | Local tests |
| infrastructure-v12 | **Redis** container |

---

## 2. Rate limiting

**What:** each client gets a **token bucket** per route. Every request takes one token; the bucket refills at
`replenishRate` tokens per second up to `burstCapacity`. An empty bucket means `429`.

**Why:** one client (a bug, a script, a crawler, an attacker) can't consume capacity meant for everyone. It
protects expensive backends like the LLM and embeddings, and it keeps costs predictable.

**Who counts as "one client":** `RateLimitKeyConfig` uses `X-User-Id` when present (per user), otherwise
the client IP.

**Why Redis:** the buckets live in Redis, so **all gateway instances share them**. With in-memory buckets,
3 instances would allow 3× the limit, and a client could bypass it by being load-balanced elsewhere.

| Route | Rate (req/s) | Burst | Reasoning |
|---|---|---|---|
| product-service (read) | 50 | 100 | Search is cheap and frequent |
| product-write-service | 5 | 10 | Admin writes are rare |
| stock-service | 30 | 60 | Frequent availability checks |
| order-service | 10 | 20 | Placing orders is rare per user |
| user-service | 20 | 40 | |
| rag-service | 5 | 10 | Each search needs an embedding (CPU) |
| mcp-client-api (chat) | **1** | **5** | Each message runs the LLM (seconds of CPU) |

**Response headers** on every request: `X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`,
`X-RateLimit-Replenish-Rate`, `X-RateLimit-Requested-Tokens`. A rejected request gets `429` with an **empty
body**; that's the gateway's standard behaviour.

**If Redis is down:** the rate limiter **fails open**, so requests are allowed and an error is logged.
Availability beats strict limiting here, and the Redis health check is disabled so a Redis outage doesn't
mark the gateway DOWN.

---

## 3. Bulkhead

**What:** at most **N requests in flight at the same time** per route. The next one is rejected
**immediately** with `503 Service busy` and `Retry-After: 2`.

**Why:** rate limiting counts requests *per second*. A bulkhead counts requests *in progress*. If the LLM
needs 60 s per answer, even 1 chat request per second piles up to 60 waiting requests, which hold connections
and memory, until the gateway itself slows down for **every** route. The bulkhead keeps that pile inside the
chatbot's "compartment", so product search and orders are unaffected.

| Route | Max concurrent | Reasoning |
|---|---|---|
| product-service | 100 | Fast; Elasticsearch handles concurrency well |
| stock-service | 50 | |
| order-service | 30 | |
| user-service | 30 | |
| product-write-service | 20 | |
| rag-service | 10 | Embedding + reranking are CPU-bound |
| mcp-client-api (chat) | **5** | Ollama answers few requests at a time on CPU |

**Why reject instead of queue?** A queue would just move the pile-up into the gateway. A fast "busy, retry in
2 s" is the honest answer.

**Implementation:** `BulkheadGatewayFilterFactory` uses a `java.util.concurrent.Semaphore`, the same idea
as Resilience4j's semaphore bulkhead, in about 20 readable lines. The permit is released exactly once
(`doFinally`), also when the client disconnects.

Usage in YAML: `- Bulkhead=<name>, <maxConcurrentCalls>`.

---

## 4. Circuit breaker

**What:** the breaker watches the last 20 calls of a backend. It has three states:

```
CLOSED ──(≥ 50 % failed or ≥ 80 % slow, after ≥ 10 calls)──► OPEN ──(15 s)──► HALF_OPEN ──(5 test calls ok)──► CLOSED
   ▲                                                          │ calls are NOT sent:            │
   │                                                          │ fallback in ~1 ms              └─(tests fail)─► OPEN
   └──────────────────────────────────────────────────────────┘
```

**Why:** without it, every request to a dead service waits for a timeout, holds a connection, and fails anyway.
Under load that ties up all gateway resources: a **cascading failure**. An open circuit answers in
milliseconds and gives the backend time to recover instead of being overloaded during its restart.

**What counts as a failure:**

- connection errors,
- timeouts,
- `500`, `502`, `503`, `504` (`statusCodes` on the filter).

**`4xx` responses are NOT failures.** A `404` or `400` is a correct answer from a healthy backend
(`resilience.http` #5 and #6 prove this).

**Values** (`app.resilience.circuit-breaker-defaults`, overridable per breaker):

| Setting | Default | Meaning |
|---|---|---|
| `sliding-window-size` | 20 | The last 20 calls are evaluated |
| `minimum-number-of-calls` | 10 | No decision before 10 calls (one error never opens it) |
| `failure-rate-threshold` | 50 % | Open at ≥ 50 % failures |
| `slow-call-duration-threshold` | 3 s | Slower than this counts as "slow" |
| `slow-call-rate-threshold` | 80 % | Open at ≥ 80 % slow calls (the backend is overloaded) |
| `wait-duration-in-open-state` | 15 s | Stay open for 15 s, then test |
| `permitted-calls-in-half-open-state` | 5 | 5 test calls decide |

There is **one breaker per backend** (`product-read`, `stock`, `orders` ...), so a failing stock service never
opens the circuit for orders.

---

## 5. Timeouts

The **circuit breaker's time limiter is the real per-route timeout**. When it fires, the request is cancelled
and the fallback answers.

| Breaker | Timeout | Why |
|---|---|---|
| product-read, stock, users | 5 s | Fast reads |
| product-write, orders | 10 s | Writes with DB transactions |
| rag | 90 s | Embedding + reranking (+ LLM for `/query`) on CPU |
| chatbot | 120 s | An LLM answer with several tool calls |

**Three timeout layers.** Each must be longer than the one inside it:

```
backend work  <  gateway route timeout (time limiter)  <  httpclient response-timeout (130 s)  <  caller timeout
                       e.g. stock 5 s                         global safety net                (MCP server 20 s ...)
```

`connect-timeout: 2000` ms: a backend that can't even accept a TCP connection fails in 2 s, not 30.

---

## 6. Retry

**What:** a failed **GET** is retried up to **2 times**, with backoff of 100 ms, then 200 ms (max 500 ms),
on 5xx responses and connection errors.

**Why:** many failures are momentary: an instance restarting, a load balancer still routing to a just-stopped
instance, a dropped connection. A quick retry hides them from the user.

**Only GET, never POST/PUT/DELETE.** A retried `POST /orders` whose first attempt actually reached the
service would create **two orders**. Writes are safe to retry only with an idempotency key, and that decision
belongs to the client (order-service supports `Idempotency-Key`), not the gateway. The chatbot API (POST) and
product writes therefore have **no** Retry filter.

**Retry inside the breaker:** the time limiter covers all attempts together, and an open circuit means
**zero** retries.

---

## 7. Fallback routing

Every `CircuitBreaker` filter has `fallbackUri: forward:/fallback/<name>`. `FallbackController` decides:

| Situation | Answer |
|---|---|
| **`product-read`, `GET /api/v1/products/{id}`** | **Fallback routing:** the same product is read from **product-write-service** (MySQL, the source of truth) instead of product-service (Elasticsearch). `200` + header `X-Fallback-Source: product-write-service`. |
| Any other read | `503` ProblemDetail: `service`, `reason`, `Retry-After: 15` |
| A write that failed | `503` + *"Nothing was changed by this request."* |
| A write that **timed out** | `503` + *"it MAY have been processed. Check the result before retrying"*, plus, for orders, *"retry with the same Idempotency-Key"* |

`reason` tells you why the fallback answered:

- **`CIRCUIT_OPEN`**: the backend wasn't called at all.
- **`TIMEOUT`**: the backend was too slow.
- **`BACKEND_ERROR`**: it answered 5xx or the connection failed.

This works well with the CQRS design: the read side is a disposable projection, and the write side can answer
single-item reads when the projection is down. Search can't fall back, because MySQL has no search index, so it
gets an honest 503.

Calling `/fallback/...` directly from outside returns 404: it is only reachable through a circuit breaker.

---

## 8. Setup

1. **Infrastructure:** use infrastructure-v12 (adds `redis`):
   ```powershell
   cd product-platform\infrastructure
   docker compose up -d redis
   docker exec -it redis redis-cli ping          # PONG
   ```
2. **Gateway:** replace `api-gateway` with api-gateway-v4 (the whole `src` folder, `pom.xml`, `scripts`, and
   `http`), then start it:
   ```powershell
   cd product-platform\api-gateway
   .\mvnw.cmd clean spring-boot:run
   ```
   The log should show one line per breaker, e.g.
   `Circuit breaker 'chatbot': timeout=PT2M, failureRate>=50.0%, open for PT15S`.
3. **Check:**
   ```powershell
   curl.exe http://localhost:8080/actuator/resilience
   curl.exe http://localhost:8080/actuator/gateway/routes
   ```
   The bulkheads appear immediately; circuit breakers appear after their first request.

---

## 9. Testing every pattern locally

Start everything as usual (infrastructure, Eureka, all services, gateway). Then, in the `api-gateway` folder:

### Test 1: Rate limiting

```powershell
.\scripts\resilience-tests.ps1 -Test RateLimit
```

**Expect:** the headers of one request (`X-RateLimit-Remaining: 59` ...), then 100 simultaneous requests:
**about 60 × 200 and 40 × 429**. Run it again after 2 s: the bucket has refilled.

To see the per-user buckets, run requests #2 and #3 in `http/resilience.http`. The `X-RateLimit-Remaining`
value is counted separately for the IP and for the user.

**Redis down:** `docker compose stop redis`, run the test again. **All 200** (fail open) and errors in the
gateway log. Then `docker compose start redis`.

### Test 2: Bulkhead

```powershell
.\scripts\resilience-tests.ps1 -Test Bulkhead
```

This sends 30 RAG searches **at the same time**, each from a **different user** (so the per-user rate limit
can't reject them first).

**Expect:** about **10 × 200** and **20 × 503** `Service busy`. `/actuator/resilience` then shows
`rag-service` with `rejectedCalls: 20`. Exact numbers vary with how fast rag-service answers, and nothing is
rejected if every search finishes before the next arrives.

### Test 3: Circuit breaker

```powershell
# stop stock-service (Ctrl+C in its window), then:
.\scripts\resilience-tests.ps1 -Test CircuitBreaker
```

**Expect:**

```
  1  HTTP 503  BACKEND_ERROR     2041 ms     <- backend really tried (connection refused / no instance)
  ...
 10  HTTP 503  BACKEND_ERROR       12 ms
 11  HTTP 503  CIRCUIT_OPEN         3 ms     <- 10 calls, 100 % failed -> OPEN: backend not called anymore
 ...
 25  HTTP 503  CIRCUIT_OPEN         2 ms
```

and the state table shows `stock  OPEN`. **Recovery:** start stock-service, wait about 30 s (Eureka) plus
15 s (HALF_OPEN), then send a few requests (`resilience.http` #2). The state goes
`HALF_OPEN → CLOSED`, which you can watch with `-Test State`.

### Test 4: Fallback routing

```powershell
# stop product-service (the Elasticsearch read side); product-write-service keeps running
.\scripts\resilience-tests.ps1 -Test FallbackRouting
```

**Expect:**

1. Get by id: `HTTP 200` + `X-Fallback-Source: product-write-service`. The product came from MySQL.
2. Search: `503` ProblemDetail with `"service":"product-read"`.

### Test 5: Timeout

Restart the gateway with a tiny timeout for product reads:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.resilience.circuit-breakers.product-read.timeout=50ms"
.\scripts\resilience-tests.ps1 -Test Timeout
```

**Expect:** `503 ... TIMEOUT ~50 ms` for every search. Restart the gateway normally afterwards.

### Test 6: Retry

Set `org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory: DEBUG` in `application.yml`,
restart the gateway, stop **order-service**, and then:

- **GET** `/api/v1/orders/{id}` (`resilience.http`): the log shows the request **retried twice** before the
  fallback.
- **POST** `/api/v1/orders` (`resilience.http` #9): **no retry** (the method isn't GET), and the 503 says
  *"Nothing was changed by this request."*

### Test 7: Errors that must NOT trip anything

Run `resilience.http` #5 (404) and #6 (400) ten times, then `-Test State`. `orders` and `users` stay `CLOSED`
with `failedCalls: 0`. Client errors never open a circuit.

---

## 10. Common mistakes (all avoided here)

| Mistake | Consequence | What this gateway does |
|---|---|---|
| Retrying POST/PUT/DELETE | Duplicate orders or payments | Retry only on GET |
| Retry *outside* the circuit breaker | An open circuit gets hammered with retries | Retry is the inner filter |
| Leaving Resilience4j's **1 s** default timeout | LLM and RAG calls always "fail" | Explicit timeout per breaker |
| Gateway timeout longer than the caller's timeout | The caller gives up first; the gateway keeps working for nothing | 3 timeout layers, each shorter than the outer |
| Counting 4xx as failures | One user's bad input opens the circuit for everyone | Only 5xx, timeouts, and connection errors count |
| One breaker for all backends | Stock failing blocks orders too | One breaker per backend |
| In-memory rate limits with several instances | Limit × instances; easy to bypass | Redis, shared |
| Redis in the gateway health check | Redis blip → gateway marked DOWN and restarted | Redis health disabled; rate limiting fails open |
| Queueing in the bulkhead | The pile-up moves into the gateway | Reject immediately with Retry-After |
| A fallback that pretends success | Clients think an order failed when it didn't (or vice versa) | Timed-out writes say "may have been processed" |
| Rate-limit key from an untrusted header | Users drain other users' buckets | `X-User-Id` today only from trusted callers; set from the JWT once security exists |

---

## 11. Production considerations

- **Tune with data, not guesses.** Start with these defaults, then adjust from metrics: real p99 latencies
  (timeouts ≈ p99 × 2), real traffic (rate limits), and real concurrency (bulkheads). The
  `/actuator/resilience` values belong on a dashboard; Resilience4j can also export Micrometer metrics to
  Prometheus/Grafana.
- **Protect the actuator.** `/actuator/resilience` and `/actuator/gateway` reveal internals. In production,
  expose them on a separate management port or behind authentication.
- **Rate limits per plan.** With authentication, the key resolver can use the user id from the JWT, and limits
  can differ per customer tier.
- **Behind a load balancer**, `getRemoteAddress()` is the load balancer's IP. Read the client IP from
  `X-Forwarded-For`, but only the value added by your own trusted proxy.
- **Redis in production:** a managed or replicated Redis (Sentinel/Cluster), monitored. A Redis outage only
  disables rate limiting; it doesn't stop traffic.
- **Bulkhead sizing** relates to backend capacity: the sum of bulkheads across gateway instances shouldn't
  exceed what the backend's thread or connection pools can serve.
- **Client behaviour:** clients should respect `Retry-After` and use exponential backoff with jitter. The
  gateway's protections are not a replacement for well-behaved clients.
- **Backends stay responsible too:** Product Service's own Elasticsearch timeouts, and Order Service's
  idempotency keys. Resilience is layered, not only in the gateway.
