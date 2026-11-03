# Stock Service

Stock / inventory service of the product platform. It answers **"is this product available?"**
and **reserves stock for orders** so two customers can never buy the same last unit.

| | |
|---|---|
| Port | `8082` |
| Database | MySQL 8.4 (`stock_db`), schema managed by Flyway |
| Registers with | Eureka (`stock-service`) |
| Called by | API Gateway (availability, stock management), order-service (reservations) |

---

## 1. How stock works

Every product (identified by its **SKU**, e.g. `LAP-APL-001`) has one row in `stock_item`:

| Field | Meaning |
|---|---|
| `quantityOnHand` | Units physically in the warehouse |
| `quantityReserved` | Units held for open orders |
| `quantityAvailable` | `onHand - reserved`: what can still be sold |
| `reorderLevel` | At or below this, the status becomes `LOW_STOCK` |

**Status:** `IN_STOCK` → `LOW_STOCK` (available ≤ reorder level) → `OUT_OF_STOCK` (0 available).
`NOT_STOCKED` means no stock record exists for the SKU.

The SKU links stock to products: it is the same business key product-service uses.
Stock lives in its own database, owned only by this service, and product-service
never reads it directly.

### Checking vs. reserving

| | Availability check | Reservation |
|---|---|---|
| When | A product is **selected** / added to a cart | An order is **placed** |
| Holds units back? | No: a snapshot of "right now" | Yes, until confirmed or released |
| Endpoint | `GET /{sku}/availability`, `POST /availability` | `POST /reservations` |

A check can say "available" and a second later another order takes the last unit.
Only a reservation **guarantees** the units, which is why the order-service must reserve, not just check.

### Reservation life cycle

```
            reserve                confirm (order completed)
  order ──────────────► RESERVED ─────────────────────────► CONFIRMED   onHand -= qty, reserved -= qty
                           │
                           │ release (order cancelled)
                           └──────────────────────────────► RELEASED    reserved -= qty
```

- **All-or-nothing:** if any line of an order is short, nothing is reserved (HTTP 409 with a `shortages` list).
- **Idempotent:** sending the same reservation, confirm, or release again is safe. This is important because
  the order-service may retry after a timeout.

---

## 2. Project structure

```
stock-service/
├── http/stock-service.http              ← example requests (IntelliJ HTTP Client)
├── pom.xml
└── src/main/
    ├── java/com/example/stock/
    │   ├── StockServiceApplication.java
    │   ├── bootstrap/    SampleStockLoader, SampleStockData   ← fake stock for local dev
    │   ├── controller/   StockController, ReservationController
    │   ├── dto/          request/response records + validation rules
    │   ├── exception/    custom exceptions + GlobalExceptionHandler (ProblemDetail)
    │   ├── filter/       CorrelationIdFilter
    │   ├── mapper/       StockMapper
    │   ├── model/        StockItem, StockReservation (JPA entities), enums
    │   ├── repository/   StockItemRepository, StockReservationRepository
    │   ├── service/      StockService, AvailabilityService, ReservationService
    │   │   └── impl/     their implementations (@Transactional)
    │   └── util/         AppConstants, Skus
    └── resources/
        ├── application.yml
        └── db/migration/V1__create_stock_tables.sql   ← Flyway
```

---

## 3. Setup

### 3.1 Prerequisites

- JDK 21, Docker Desktop (WSL 2 backend)
- `infrastructure/docker-compose.yml` and `.env` **with the `mysql` service** (see the updated files)

### 3.2 Start MySQL

```powershell
cd product-platform\infrastructure
docker compose up -d mysql
docker compose ps mysql          # wait for "healthy" (about 20-40 s on first start)
```

On the **first** start (empty volume), MySQL creates automatically:

- database `stock_db`
- user `stock_user` / `stock_dev_password`, with rights **only** on `stock_db`

These come from `STOCK_DB_*` in `.env`. They are applied only when the data volume is empty.
If you change them later, see "Reset the database" below.

MySQL listens on **localhost:3307** (not 3306), so it does not clash with a MySQL installed on Windows.

### 3.3 Verify MySQL

```powershell
docker exec -it mysql mysql -u stock_user -pstock_dev_password -e "SELECT VERSION(); SHOW DATABASES;"
```

Expect version `8.4.x` and `stock_db` in the list.

---

## 4. Run

Start order: MySQL → `eureka-server` → `stock-service`.

```powershell
cd product-platform\stock-service
.\mvnw.cmd clean spring-boot:run
```

What happens at startup, in this order:

1. **Flyway** runs `V1__create_stock_tables.sql` (first start only) and records it in `flyway_schema_history`.
2. **Hibernate validates** that the entities match the tables (`ddl-auto: validate`). A mismatch stops startup
   with a clear error, instead of failing later at runtime.
3. **SampleStockLoader** inserts stock for the 30 sample products (only if `stock_item` is empty).

Look for:

```
Successfully applied 1 migration to schema `stock_db`
Sample stock loaded: 30 items
Tomcat started on port 8082
```

Then check that `STOCK-SERVICE` is UP on the Eureka dashboard at http://localhost:8761.

### Configuration

All database settings can be overridden with environment variables (no code changes):

| Variable | Default (local dev) |
|---|---|
| `STOCK_DB_URL` | `jdbc:mysql://localhost:3307/stock_db?connectionTimeZone=UTC` |
| `STOCK_DB_USERNAME` | `stock_user` |
| `STOCK_DB_PASSWORD` | `stock_dev_password` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |

---

## 5. Test

Open `http/stock-service.http` in IntelliJ and run the requests **top to bottom**.
Each request's comment says what to expect. The key ones:

| # | What it shows |
|---|---|
| 2-6 | Availability of single products: in stock, out of stock, low stock, case-insensitive SKU, unknown SKU |
| 7 | A whole cart in one call; duplicate lines merged; unknown SKU reported as `NOT_STOCKED` |
| 8-13 | Reserve → idempotent retry (200, not 201) → confirm → on-hand stock reduced |
| 15-17 | Reserve → release (cancel) → cannot confirm afterwards |
| 18-19 | **All-or-nothing**: one short line → 409 with `shortages`, and the other line is **not** reserved |
| 22-25 | Low-stock report, delivery (+20), write-off rejected, stock-count correction |
| 30-32 | Validation errors (400 with an `errors` map) |

Example: request 18's response.

```json
{
  "type": "about:blank",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Insufficient stock for 1 item(s); nothing was reserved",
  "instance": "/api/v1/stock/reservations",
  "timestamp": "2026-09-24T10:12:44.918Z",
  "shortages": [
    { "sku": "LAP-HP-002", "requestedQuantity": 5, "availableQuantity": 2 }
  ]
}
```

### Look at the data in MySQL

```powershell
docker exec -it mysql mysql -u stock_user -pstock_dev_password stock_db
```

```sql
SELECT sku, quantity_on_hand, quantity_reserved, reorder_level, version FROM stock_item ORDER BY sku;
SELECT order_id, sku, quantity, status, updated_at FROM stock_reservation ORDER BY order_id, sku;
SELECT version, description, success FROM flyway_schema_history;
exit
```

Watch `version` increase on every change. That is the optimistic-locking counter.

---

## 6. Design decisions (why it is built this way)

**Atomic conditional updates instead of "read, check, write".**
A naive implementation reads `available = 1`, checks `1 >= 1`, then writes. Two orders doing this at the
same moment both see `1` and both succeed: an oversold product. Here the check and the change are
**one SQL statement**:

```sql
UPDATE stock_item SET quantity_reserved = quantity_reserved + 1
 WHERE sku = 'X' AND quantity_on_hand - quantity_reserved >= 1
```

MySQL locks the row while the statement runs, so the second order sees the updated numbers and changes
0 rows. The service then reports "insufficient stock".

**Lines are processed in SKU order.** If order A locks X then Y while order B locks Y then X, they wait
for each other forever (a deadlock). Sorting guarantees every request locks rows in the same order.

**Optimistic locking (`@Version`) for absolute updates.** `PUT /{sku}` reads the row, changes it, and saves
it. If a reservation changed the row in between, Hibernate's `WHERE version = ?` matches nothing, and the
client gets **409 "Concurrent modification"** instead of silently overwriting newer numbers.

**Database CHECK constraints** (`quantity_on_hand >= 0`, `reserved <= on_hand`) are a second safety net:
even a future bug cannot store impossible stock.

**Flyway owns the schema; Hibernate only validates it.** Schema changes are reviewed SQL files with a
history, never automatic `ddl-auto: update` surprises in production.

**Sample data in code, not in a migration.** Migrations run in every environment. Sample stock must never
reach production, so it lives in a loader that is switched off with one property.

**The database user can only access `stock_db`.** When the order-service arrives, it gets its own database
and user. Services never share tables.

---

## 7. Security considerations

- The gateway exposes availability and stock management but **blocks `/api/v1/stock/reservations/**`**:
  only the order-service may reserve stock, calling this service directly via Eureka. When authentication is
  added, stock management (create/adjust/delete) should also require an admin role.
- Credentials come from environment variables; the defaults are for local development only. Never commit a
  `.env` with real passwords; commit a `.env.example` instead.
- MySQL is bound to `127.0.0.1` only.
- SQL uses bound parameters (JPA/JPQL), never string concatenation: no SQL injection.
- Error responses contain no SQL, stack traces, or table names; details go to the log only.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Communications link failure` / `Connection refused` at startup | MySQL not running or not healthy yet | `docker compose ps mysql`; wait for `healthy` |
| `Access denied for user 'stock_user'` | Credentials in `.env` changed after the first start (users are only created on an empty volume) | Use the original password, or reset the database (below) |
| `Public Key Retrieval is not allowed` | Connection without TLS and MySQL 8 authentication | Add `&allowPublicKeyRetrieval=true` to `STOCK_DB_URL` (**local dev only**) |
| `Schema-validation: missing table [stock_item]` | Flyway didn't run | Check that `spring-boot-starter-flyway` and `flyway-mysql` are in the pom |
| `Unsupported Database: MySQL 8.4` from Flyway | `flyway-mysql` module missing | Add the `flyway-mysql` dependency |
| `Schema-validation: wrong column type` | Entity and SQL disagree | Fix the entity, or add a new migration `V2__...` (**never edit V1** after it ran) |
| `Validate failed: Migration checksum mismatch` | An applied migration file was edited | Undo the edit, or (local dev only) reset the database |
| Port 3307 already in use | Another program uses it | Change `MYSQL_PORT` in `.env` **and** the port in `STOCK_DB_URL` |
| 503 "Database unavailable" | MySQL stopped, or no free connection within 5 s | Start MySQL; check the logs for leak-detection warnings |
| 409 "Stock busy" | MySQL lock timeout or deadlock under heavy load | Retry; that is safe for all stock operations |

### Reset the database (local dev only: deletes all stock data)

```powershell
cd product-platform\infrastructure
docker compose stop mysql
docker compose rm -f mysql
docker volume rm product-platform-mysql-data
docker compose up -d mysql
```

Restart stock-service: Flyway recreates the tables and the sample stock is loaded again.
