# Order Service

Manages orders: who ordered, which products, quantities, prices, totals, and status, with a full status
history. It takes part in the **order saga** with stock-service, entirely over **Kafka** (no REST calls
between services).

| | |
|---|---|
| Port | `8084` |
| Database | Own MySQL container `mysql-order` (localhost:**3310**), database `order_db`, Flyway |
| Publishes | `order.events.v1`: `OrderCreated`, `OrderConfirmed`, `OrderCancelled` |
| Consumes | `stock.events.v1` (saga replies), `product.catalog.v1` (product catalogue copy) |
| Avro schemas | **Reused** from `event-schemas`, already in the existing Schema Registry. No new schema was needed. |

---

## 1. How an order works

```
 Client                    order-service                  Kafka                    stock-service
   │ POST /orders               │                           │                           │
   ├───────────────────────────►│ tx: order PENDING         │                           │
   │                            │     + history             │                           │
   │                            │     + outbox(OrderCreated)│                           │
   │ ◄── 202 PENDING ───────────┤                           │                           │
   │                            │ OutboxRelay ─────────────►│ order.events.v1 ─────────►│ reserve all lines
   │                            │                           │                           │ (all-or-nothing)
   │                            │◄──────────────────────────│◄─ stock.events.v1 ────────┤ StockReserved
   │                            │ tx: CONFIRMED + history   │   or StockReservationFailed
   │                            │     + outbox(OrderConfirmed) ──────────────────────────►│ deduct stock
   │ GET /orders/{id}           │                           │                           │
   ├───────────────────────────►│ CONFIRMED / REJECTED      │                           │
```

**The availability check is the reservation.** A separate "is it available?" question followed by a
reservation would be a race: another order could take the last unit in between. So order-service asks by
publishing `OrderCreated`, and stock-service answers **and** holds the stock in one atomic step.

| Status | Meaning | Set by |
|---|---|---|
| `PENDING` | Saved, waiting for the stock answer (usually < 1 s) | POST |
| `CONFIRMED` | Stock reserved and deducted | `StockReserved` event |
| `REJECTED` | Not enough stock (reason in `failureReason`); nothing was reserved | `StockReservationFailed` event |
| `CANCELLED` | Cancelled by the customer; stock is given back | POST `/cancel` |

**Where product names and prices come from:** product-write-service publishes a `ProductSnapshot` for every
product to the **compacted** topic `product.catalog.v1`, and order-service keeps a local copy in
`catalog_product`. Orders copy name and price from there, **never from the client**, and creating an order
needs no call to another service.

---

## 2. Event ownership

| Topic | Owner (only producer) | Events | Consumers |
|---|---|---|---|
| `product.catalog.v1` | product-write-service | `ProductSnapshot`, tombstone | order-service |
| `order.events.v1` | **order-service** | `OrderCreated`, `OrderConfirmed`, `OrderCancelled` | stock-service |
| `stock.events.v1` | stock-service | `StockReserved`, `StockReservationFailed`, `StockReleased` | **order-service** |

All schemas come from the `event-schemas` library (Phase A) and are registered in the existing Schema
Registry. Services run with `auto.register.schemas=false`, so a running app can never create a schema
version by itself.

---

## 3. Reliability: the four patterns

**1. Transactional outbox (no lost events).** The order and its event are written in **one** MySQL
transaction (`orders` + `outbox_event`). `OutboxRelay` sends pending events to Kafka every 500 ms and marks
them as published. A crash can never leave "order saved but no event" or "event sent but no order".

**2. Idempotent consumers (no double processing).** Kafka may deliver an event twice. Every handler checks
`processed_event` first and writes the `eventId` there **in the same transaction** as its change.
Duplicates are skipped.

**3. Ordering by key.** Every order event uses the orderId as its Kafka key, so all events of one order sit
in one partition, in order. stock-service always sees `OrderCreated` before `OrderCancelled`, even when a
cancel follows the create within milliseconds.

**4. Retry and dead letter topics.** Transient errors (database or Schema Registry down, optimistic-lock
conflicts) are retried with backoff, and nothing is skipped. Invalid messages go to
`<topic>.order-service.dlt`, and processing continues with the next record.

Plus two supporting rules:

- **State guards:** an event that doesn't fit the current status (for example `StockReserved` for an order
  already cancelled) is ignored and logged, never applied.
- **Optimistic locking:** a saga update and a customer cancel on the same order can't overwrite each other.

---

## 4. Project structure

Same layers as the other services, plus two packages for messaging:

```
order-service/
├── http/order.http                     ← 27 requests with fake test data
├── pom.xml
└── src/main/
    ├── java/com/example/order/
    │   ├── OrderServiceApplication.java
    │   ├── config/      KafkaConfig (wiring only), KafkaErrorHandlingProperties, WebConfig
    │   ├── controller/  OrderController, OrderCatalogController
    │   ├── dto/         CreateOrderRequest, OrderItemRequest, CancelOrderRequest, OrderSearchRequest,
    │   │                OrderResponse, OrderItemResponse, OrderStatusHistoryResponse,
    │   │                CatalogProductResponse, PageResponse
    │   ├── exception/   OrderNotFound, UnknownProduct, InvalidOrderRequest, InvalidOrderState,
    │   │                GlobalExceptionHandler (ProblemDetail)
    │   ├── filter/      CorrelationIdFilter
    │   ├── mapper/      OrderMapper (entities → DTOs), OrderEventMapper (entities → Avro events)
    │   ├── messaging/   StockEventsListener, ProductCatalogListener, AvroEventDecoder,
    │   │                ProcessedEvent(+Repository), InvalidEventException
    │   ├── model/       Order, OrderItem, OrderStatus, CancellationReason, OrderStatusHistory, CatalogProduct
    │   ├── outbox/      OutboxEvent(+Repository), OutboxWriter, OutboxRelay, OutboxProperties
    │   ├── repository/  OrderRepository, OrderSpecifications, OrderStatusHistoryRepository,
    │   │                CatalogProductRepository
    │   ├── service/     OrderService, OrderSagaService, ProductCatalogService
    │   │   └── impl/    their implementations
    │   └── util/        AppConstants, StringToEnumIgnoreCaseConverterFactory
    └── resources/
        ├── application.yml             ← ALL settings, including Kafka
        └── db/migration/V1__create_order_tables.sql
```

| Package | Role (compared with the REST layers) |
|---|---|
| `messaging` | Like `controller`, but for Kafka: receives events, delegates to services |
| `outbox` | Like `repository`, but for outgoing events |
| `OrderSagaService` | The saga rules: what order-service does when a stock event arrives |

---

## 5. API

| Method | URL | Purpose | Success |
|---|---|---|---|
| POST | `/api/v1/orders` | Place an order. Optional header `Idempotency-Key` | **202** PENDING (200 for an idempotent replay) |
| GET | `/api/v1/orders/{id}` | One order | 200 |
| GET | `/api/v1/orders?userId=&status=&page=&size=` | Orders of a user / by status, newest first | 200 |
| GET | `/api/v1/orders/{id}/history` | Status history (tracking) | 200 |
| POST | `/api/v1/orders/{id}/cancel` | Cancel (PENDING or CONFIRMED) | 200 |
| GET | `/api/v1/orders/catalog`, `/catalog/{sku}` | Local product catalogue (for checking the replication) | 200 |

**Why no "update status" endpoint?** Status changes are **business events**, not free edits. CONFIRMED and
REJECTED come from stock-service, CANCELLED comes from the customer, and later SHIPPED comes from a delivery
service. Each has its own endpoint or event, with its own rules.

**Why `?userId=` instead of `/users/{id}/orders`?** The gateway routes `/api/v1/users/**` to user-service.

**Request:**

```json
{ "userId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "items": [ { "sku": "LAP-APL-001", "quantity": 1 }, { "sku": "AUD-APL-001", "quantity": 2 } ] }
```

**Response (202):**

```json
{
  "id": "5e1f0c3a-8b2d-4f6e-9a7c-3d2b1e0f4a5b",
  "userId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "PENDING",
  "currency": "USD",
  "totalAmount": 1597.00,
  "items": [
    { "sku": "AUD-APL-001", "productName": "Apple AirPods Pro 2", "unitPrice": 249.00, "quantity": 2, "lineTotal": 498.00 },
    { "sku": "LAP-APL-001", "productName": "Apple MacBook Air 13 M3", "unitPrice": 1099.00, "quantity": 1, "lineTotal": 1099.00 }
  ],
  "failureReason": null,
  "cancellationReason": null,
  "version": 0,
  "createdAt": "2026-09-28T10:41:07.114Z",
  "updatedAt": "2026-09-28T10:41:07.114Z"
}
```

---

## 6. Setup (in this order)

### 6.1 Infrastructure

Replace `infrastructure/docker-compose.yml` and `.env` with the new versions (they add `mysql-order`), then:

```powershell
cd product-platform\infrastructure
docker compose up -d
docker compose ps mysql-order        # wait for "healthy"
```

MySQL creates `order_db` and `order_user` from `ORDER_DB_*` in `.env` on first start. If you ran the old
`02-create-order-db.sql` on the shared `mysql`, drop it there:
`docker exec -it mysql mysql -uroot -proot_dev_password -e "DROP DATABASE IF EXISTS order_db; DROP USER IF EXISTS 'order_user'@'%';"`

### 6.2 Event schemas (library + registry)

```powershell
cd ..\event-schemas
mvn clean install                      # the services depend on com.example:event-schemas:1.0.0
mvn schema-registry:register           # safe to repeat: identical schemas are not duplicated
curl.exe http://localhost:18081/subjects
```

The subject list must contain `order.events.v1-...OrderCreated`, `stock.events.v1-...StockReserved`,
`product.catalog.v1-...ProductSnapshot`, and the rest. If a subject is missing, publishing that event fails
with `Schema not found`.

### 6.3 product-write-service (publishes the catalogue)

Replace it with the new version: `outbox/`, `KafkaConfig`, `ProductSnapshotMapper`, `CatalogController`,
migration `V2__create_outbox_event_table.sql`, and Kafka dependencies. Replace the **whole `src` folder**.
Then start it and publish the existing products once:

```powershell
.\mvnw.cmd clean spring-boot:run
curl.exe -X POST http://localhost:8089/api/v1/products/catalog/republish
```

Expect `{"queued":30}`. From now on, every create, update, or delete publishes automatically.

### 6.4 stock-service (joins the saga)

Replace it with the new version: `outbox/`, `messaging/`, `KafkaConfig`, `StockSagaService`,
`StockEventMapper`, `ReservationService.cancelForOrder`, migration `V2__create_saga_tables.sql`, and Kafka
dependencies. Again, replace the whole `src` folder. Start it, and look for:
`stock-order-events: partitions assigned: [order.events.v1-0, ...]`.

### 6.5 order-service

Generate a skeleton on start.spring.io (Boot 4.0.8, Maven, Java 21, YAML, Artifact `order-service`,
Package `com.example.order`) with **Spring Web, Validation, Spring Data JPA, Flyway Migration, MySQL
Driver, Spring for Apache Kafka, Actuator, Eureka Discovery Client**. Copy `pom.xml` and `src` over it,
then run:

```powershell
cd ..\order-service
.\mvnw.cmd clean spring-boot:run
```

Expect:

```
Successfully applied 1 migration to schema `order_db`
order-stock-events: partitions assigned: [stock.events.v1-0, stock.events.v1-1, stock.events.v1-2]
order-product-catalog: partitions assigned: [product.catalog.v1-0, product.catalog.v1-1, product.catalog.v1-2]
Tomcat started on port 8084
```

Check the catalogue arrived with `curl.exe "http://localhost:8084/api/v1/orders/catalog?size=1"`: expect
`"totalElements":30`.

### 6.6 Gateway route

In `api-gateway/src/main/resources/application.yml`, add under `routes:`:

```yaml
            # ---------------- Order Service ----------------
            - id: order-service
              uri: lb://order-service
              predicates:
                - Path=/api/v1/orders/**
              filters:
                - name: Retry
                  args:
                    retries: 2
                    methods: GET          # never retry POST (it would create two orders)
                    backoff:
                      firstBackoff: 100ms
                      maxBackoff: 500ms
                      factor: 2
                      basedOnPreviousValue: false
```

---

## 7. Test

Run `http/order.http` **top to bottom**. The key checks:

| Requests | What they prove |
|---|---|
| 1–2 | Catalogue replicated through Kafka |
| 3–6 | Happy path: 202 PENDING → CONFIRMED; history; stock deducted |
| 7 | Idempotency key: the retry returns the same order (200) |
| 8–11 | Out of stock / too much → REJECTED with reason; **all-or-nothing** (nothing reserved) |
| 12–17 | Duplicate lines merged; cancel → CANCELLED; stock returned; cancel twice is safe |
| 18 | Business rule: a rejected order can't be cancelled (409) |
| 19–22 | Orders of a user, filter by status, paging |
| 23–27 | Validation and error responses |

**Watch the saga in Kafka UI** (http://localhost:8088):

- Topics → `order.events.v1` → Messages: `OrderCreated` (header `eventType`), then `OrderConfirmed` /
  `OrderCancelled`.
- Topics → `stock.events.v1`: `StockReserved`, `StockReservationFailed`, `StockReleased`.
- Consumers: `order-service` and `stock-service`, lag 0.

**Follow one order through the logs:** the correlation id of the POST (response header `X-Correlation-Id`)
travels inside every event, so the same id appears in the order-service **and** stock-service logs.

### Resilience tests

1. **stock-service down:** stop it, then place an order. The order stays `PENDING`, and
   `order.events.v1` holds `OrderCreated`. Start stock-service: the order becomes CONFIRMED within seconds.
   No REST timeout, no lost order.
2. **Kafka down:** `docker compose stop kafka`, then place an order. The POST still succeeds, because the
   event waits in `outbox_event`:
   `docker exec -it mysql-order mysql -uorder_user -porder_dev_password order_db -e "SELECT event_type, published_at FROM outbox_event ORDER BY id DESC LIMIT 5;"`
   shows `published_at = NULL`. `docker compose start kafka`: the relay publishes, and the saga continues.
3. **Duplicate delivery:** reset the stock-service group to re-read old order events (stock-service
   stopped):
   `docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group stock-service --reset-offsets --to-earliest --topic order.events.v1 --execute`.
   Start stock-service: its log shows `Duplicate event ... ignored`, and stock numbers don't change.

---

## 8. Extending later

| Feature | How it fits |
|---|---|
| **Payment** | payment-service consumes `StockReserved` and publishes `PaymentCompleted`/`PaymentFailed`. In `OrderSagaServiceImpl`, `StockReserved` → `AWAITING_PAYMENT`; `PaymentCompleted` → CONFIRMED + `OrderConfirmed`; `PaymentFailed` → CANCELLED + `OrderCancelled` (`PAYMENT_FAILED`). Stock-service doesn't change. |
| **Order timeout** | A scheduled job cancels orders stuck in PENDING / AWAITING_PAYMENT for too long, publishing `OrderCancelled` as usual |
| **Tracking / delivery** | New statuses (SHIPPED, DELIVERED) in `OrderStatus`, set from delivery events; `order_status_history` already records every change |
| **Notifications** | notification-service consumes `order.events.v1`; order-service doesn't change |
| **Users** | With security, `userId` comes from the JWT instead of the request body |

New fields in events: add them **with defaults** in `event-schemas`, so FULL_TRANSITIVE compatibility keeps
all services working (see `event-schemas/README.md`).

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| POST → 400 "Unknown product" for existing SKUs | Catalogue not replicated | `POST http://localhost:8089/api/v1/products/catalog/republish`; check `GET /api/v1/orders/catalog` |
| POST → 503 "Event infrastructure unavailable" | Schema Registry down, or schemas not registered | `docker compose ps schema-registry`; `mvn schema-registry:register` in event-schemas |
| Log: `Schema not found; error code: 40403` | The event's schema isn't registered (auto-register is off on purpose) | `mvn schema-registry:register`, then retry |
| Orders stay `PENDING` | stock-service not running or not consuming | Start it; check that group `stock-service` has lag 0 in Kafka UI; check its log for errors |
| Outbox rows stay unpublished | Kafka unreachable | `docker compose ps kafka`; the relay retries automatically |
| `Could not find artifact com.example:event-schemas:jar:1.0.0` | Library not installed | `mvn clean install` in event-schemas |
| `Could not resolve io.confluent:kafka-avro-serializer` | Confluent repository unreachable | Check proxy / network access to packages.confluent.io |
| Records in `stock.events.v1.order-service.dlt` | A message could not be processed | Open it in Kafka UI; the headers contain the exception and original offset |
| `Access denied for user 'order_user'` | `mysql-order` was first started with other `ORDER_DB_*` values | Reset its volume (only local): `docker compose rm -sf mysql-order`, `docker volume rm product-platform-mysql-order-data`, `docker compose up -d mysql-order` |
| 409 "Concurrent modification" on cancel | The saga updated the order at the same moment | Reload the order and cancel again |
