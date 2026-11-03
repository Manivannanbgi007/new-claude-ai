# event-schemas

The **shared event contract** of the platform: every Kafka event is defined here, once.

It is a plain **JAR library**. Services add it as a Maven dependency and get:

- the Avro schemas (`src/main/avro/**/*.avsc`), the single source of truth,
- Java classes generated from them (`OrderCreated`, `StockReserved`, ...),
- `EventTopics` with all topic names.

> **Why a JAR and not a WAR?** A WAR is a deployable web application. Its classes sit in
> `WEB-INF/classes` and are **not** put on the classpath of projects that depend on it, so services
> could not use the generated classes. A library has no runtime of its own, so it is a JAR.

---

## 1. What's inside

```
event-schemas/
├── pom.xml
├── docs/evolution-examples/        ← hands-on compatibility exercise (section 5)
└── src/main/
    ├── avro/
    │   ├── order/    OrderCreated, OrderConfirmed, OrderCancelled     (owner: order-service)
    │   ├── stock/    StockReserved, StockReservationFailed, StockReleased (owner: stock-service)
    │   └── product/  ProductSnapshot                                   (owner: product-service)
    └── java/com/example/events/EventTopics.java
```

| Topic | Owner (only producer) | Event types | Key |
|---|---|---|---|
| `order.events.v1` | order-service | OrderCreated, OrderConfirmed, OrderCancelled | orderId |
| `stock.events.v1` | stock-service | StockReserved, StockReservationFailed, StockReleased | orderId |
| `product.catalog.v1` (compacted) | product-service | ProductSnapshot / tombstone | sku |
| `payment.events.v1` | payment-service (next phase) | (to be added) | orderId |

Every event carries `eventId` (for duplicate detection), `occurredAt`, and `correlationId` (for tracing).

---

## 2. Build and install

```powershell
cd product-platform\event-schemas
mvn clean install
```

This generates the classes into `target/generated-sources/avro`, compiles them, and installs
`com.example:event-schemas:1.0.0` into your local Maven repository (`~/.m2`).

In IntelliJ, after the first build, right-click `target/generated-sources/avro` →
**Mark Directory as → Generated Sources Root** if the classes show as red.

Use it in a service:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>event-schemas</artifactId>
    <version>1.0.0</version>
</dependency>
```

In a team, you would `mvn deploy` it to a shared repository (Nexus, Artifactory, GitHub Packages)
instead of each developer installing it locally.

---

## 3. Register the schemas in Schema Registry

Services are configured with `auto.register.schemas=false`: **a producer can never invent a new
schema version at runtime.** Schemas are registered deliberately, from this project, after the
compatibility check passes. This is the production practice.

Start the infrastructure first (`docker compose up -d` in `infrastructure/`), then:

```powershell
mvn schema-registry:test-compatibility    # 1. is every schema compatible with what is registered?
mvn schema-registry:register              # 2. register new versions
```

On the very first run, `test-compatibility` reports that subjects don't exist yet. That's fine: just
run `register`.

Check the result:

```powershell
curl.exe http://localhost:18081/subjects
curl.exe http://localhost:18081/config                 # {"compatibilityLevel":"FULL_TRANSITIVE"}
curl.exe "http://localhost:18081/subjects/order.events.v1-com.example.events.order.OrderCreated/versions/latest"
```

Or open **Kafka UI** at http://localhost:8088 → *Schema Registry*.

**Subject names** use `TopicRecordNameStrategy`: `<topic>-<record full name>`. This lets one topic carry
several event types. That matters because all events of one order must be on **one topic** to stay in
order, and each type still gets its own version history.

---

## 4. Compatibility: the rules

The registry runs with **FULL_TRANSITIVE**: every new version must be **backward and forward**
compatible with **all** previous versions.

| Direction | Meaning | Why you need it |
|---|---|---|
| **Backward** | A consumer with the NEW schema reads OLD events | Consumers can upgrade first; old events in the topic stay readable |
| **Forward** | A consumer with the OLD schema reads NEW events | Producers can upgrade first; services not yet redeployed keep working |
| **Transitive** | ...against *every* earlier version, not just the last one | A consumer that is two versions behind still works |

With FULL compatibility, **services can be deployed in any order**, which is essential when each
microservice has its own release schedule.

### Allowed and forbidden changes

| Change | Allowed? | How |
|---|---|---|
| Add a field | ✅ | **Must have a default**, e.g. `{"name":"note","type":["null","string"],"default":null}` |
| Remove a field | ✅ only if it **had a default** | Old consumers fall back to the default |
| Add an enum symbol | ✅ only if the enum has a **`default` symbol** | Old consumers read the unknown symbol as the default (`UNKNOWN`) |
| Rename a field | ❌ | Add the new field (with default), migrate, later remove the old one |
| Change a field's type | ❌ (except promotions like int → long) | Add a new field instead |
| Add a required field (no default) | ❌ | New consumers could not read old events |
| Breaking change you cannot avoid | New topic version | `order.events.v2`, with both topics running in parallel during migration |

This is why every enum in these schemas starts with an `UNKNOWN` symbol and declares
`"default": "UNKNOWN"`: new reasons can be added later without breaking anyone.

### Releasing a schema change

1. Edit the `.avsc` following the rules above.
2. `mvn schema-registry:test-compatibility`; stop if it reports `false`.
3. Bump the library version (`1.0.0` → `1.1.0`), then run `mvn clean install` (or `deploy`).
4. `mvn schema-registry:register`.
5. Update services to the new library version **in any order**.

---

## 5. Try it yourself (10 minutes)

`docs/evolution-examples/` contains three modified schemas. I checked their behaviour with Avro schema
resolution:

| File | Change | Result |
|---|---|---|
| `OrderCreated-v2-compatible.avsc` | adds optional `customerNote` (default null) | ✅ compatible both ways |
| `OrderCreated-v2-breaking.avsc` | adds required `shippingAddress` (no default) | ❌ new consumers can't read old events |
| `OrderCancelled-v2-new-enum-symbol.avsc` | adds `FRAUD_SUSPECTED` | ✅ old consumers read it as `UNKNOWN` |

Exercise (after the first `register`):

1. Copy `OrderCreated-v2-breaking.avsc` over `src/main/avro/order/OrderCreated.avsc`.
2. Run `mvn schema-registry:test-compatibility` and expect **false** for the OrderCreated subject.
3. Copy `OrderCreated-v2-compatible.avsc` over it instead and run the check again: **true**.
4. **Restore the original file** (`git checkout src/main/avro/order/OrderCreated.avsc`) and don't register
   the test versions.

---

## 6. Common problems

| Symptom | Fix |
|---|---|
| `Could not resolve io.confluent:kafka-schema-registry-maven-plugin` | The Confluent repository must be reachable (`https://packages.confluent.io/maven/`); check your proxy |
| `No plugin found for prefix 'schema-registry'` | Run the command inside the `event-schemas` folder (the plugin is declared in this pom) |
| `Connection refused` on register | Schema Registry isn't running: `docker compose ps schema-registry` |
| `Schema being registered is incompatible with an earlier schema` (409) | The change breaks a rule in section 4 |
| Service fails with `Schema not found` when sending | That schema version isn't registered yet: run `mvn schema-registry:register` |
| Generated classes are red in IntelliJ | Run `mvn clean install` once and mark `target/generated-sources/avro` as a generated sources root |
