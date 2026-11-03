# Swagger / OpenAPI: one UI for all services

**Open:** http://localhost:8080/swagger-ui.html, then pick a service in the **"Select a definition"** dropdown
(top right).

```
Browser ─► gateway :8080/swagger-ui.html                 (Swagger UI, served by the gateway)
              │  "Orders" selected
              ▼
           gateway /openapi/order-service ──SetPath──► order-service /v3/api-docs   (API description, JSON)
              │
           "Try it out" ─► gateway :8080/api/v1/orders/...  (the NORMAL route: rate limiting,
                                                            bulkhead, circuit breaker, retry - as for any client)
```

**Nothing in the existing functionality changes:**

- **No controller was modified.** springdoc reads the existing `@GetMapping`, `@RequestParam`,
  `@RequestHeader`, `@Valid`, and Jakarta validation annotations directly.
- **No existing setting was changed.** Each service only gains a dependency, a `springdoc:` section, and one
  small `OpenApiConfig` class.
- **The gateway's existing routes are unchanged.** It only gains 7 read-only routes for the API descriptions
  and the UI configuration.

---

## 1. Which services are documented

| Dropdown entry | Service | Paths |
|---|---|---|
| Products - search (read) | product-service | `GET /api/v1/products/**` |
| Products - manage (write) | product-write-service | `POST/PUT/PATCH/DELETE /api/v1/products/**`, `/catalog/republish` |
| Stock | stock-service | `/api/v1/stock/**` (without the internal reservation API) |
| Orders | order-service | `/api/v1/orders/**` |
| Users | user-service | `/api/v1/users/**` |
| Documents (RAG) | rag-service | `/api/v1/rag/**`, including **file upload** |
| Chatbot | mcp-client | `/api/v1/chat/**` |

**Not documented, on purpose:**

- **mcp-server** has no REST API. Its only endpoint, `/mcp`, speaks the MCP protocol (JSON-RPC over HTTP),
  which OpenAPI can't describe as REST operations. You test it with `mcp-tools.http`, as before.
- **eureka-server** has no business API.
- **The stock reservation API** (`/api/v1/stock/reservations/**`) is excluded: the gateway blocks it with 403
  because only the order saga may reserve stock.

---

## 2. What was added

### Each of the 7 services

**`pom.xml`**: a version property and one dependency:

```xml
<springdoc.version>3.0.0</springdoc.version>
...
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

The `-api` artifact only **generates** `/v3/api-docs` (JSON). It has no UI: there is one UI, on the gateway.
springdoc **3.x** is the line for Spring Boot 4. Use the newest 3.0.x release, and the same version everywhere.

**`application.yml`**: a new `springdoc:` section:

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  paths-to-match: /api/v1/**        # only the business API: no /actuator, no /error
  show-actuator: false
  default-produces-media-type: application/json
```

stock-service additionally has `paths-to-exclude: /api/v1/stock/reservations/**`.

**`config/OpenApiConfig.java`**: the title and description, plus one important line:

```java
.servers(List.of(new Server().url("/").description("Current host")))
```

The server URL is **relative**. When the Swagger UI loads the description from the gateway (`localhost:8080`),
"Try it out" sends the requests to `localhost:8080`, through the gateway. Without this line, springdoc would
write the service's own address (e.g. `localhost:8084`) into the document, and "Try it out" would bypass the
gateway.

### The API Gateway

**`pom.xml`**: `springdoc-openapi-starter-webflux-ui`. This is the **WebFlux** variant, because the gateway is
reactive. It serves the Swagger UI.

**`application.yml`**: 7 routes that fetch each service's API description:

```yaml
- id: openapi-order-service
  uri: lb://order-service
  predicates:
    - Path=/openapi/order-service
    - Method=GET
  filters:
    - SetPath=/v3/api-docs        # /openapi/order-service -> /v3/api-docs on order-service
```

…and the UI's list of services:

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    urls:
      - name: "Orders"
        url: /openapi/order-service
      ...
```

**Why `/openapi/<service>` and not `/v3/api-docs/<service>`:** the gateway's Swagger UI loads its own
configuration from `/v3/api-docs/swagger-config`. Routing `/v3/api-docs/**` to the services would capture
that request and break the UI.

---

## 3. Apply

The folder contains only the changed and new files, in the same paths as your projects:

```
swagger-openapi/
├── api-gateway/            pom.xml, src/main/resources/application.yml
├── product-service/        pom.xml, application.yml, src/main/java/.../config/OpenApiConfig.java
├── product-write-service/  (same three files)
├── stock-service/  order-service/  user-service/  rag-service/  mcp-client/
```

1. Copy the files into the matching projects. The `application.yml` files already include the
   centralized-logging changes. If you've edited a file since, add only the `springdoc:` section (and, for the
   gateway, the `openapi-*` routes) instead of overwriting it.
2. Rebuild and restart each changed service, then the gateway:
   ```powershell
   .\mvnw.cmd clean spring-boot:run
   ```
   `clean` is needed because of the new dependency.
3. Wait about 30 s until all services are registered in Eureka (http://localhost:8761).

---

## 4. Verify

1. **Each service generates its description.** Directly, without the gateway:
   ```powershell
   curl.exe -s http://localhost:8084/v3/api-docs | Select-String -Pattern '"title":"Order Service API"' -Quiet
   ```
   Expect `True`.
2. **Through the gateway:**
   ```powershell
   curl.exe -s http://localhost:8080/openapi/order-service | Select-String -Pattern '"/api/v1/orders' -Quiet
   ```
   Expect `True`.
3. **The UI:** open http://localhost:8080/swagger-ui.html.
   - The dropdown lists 7 services.
   - Under **Servers** it shows `/ - Current host`.
4. **Try it out** with Products - search (read) → `GET /api/v1/products` → **Try it out** → `q` = `laptop` →
   **Execute**. The **Request URL** shows `http://localhost:8080/api/v1/products?q=laptop`, so the request went
   through the gateway. The response headers include `x-ratelimit-remaining`, which proves the gateway's filters
   applied.
5. **File upload:** Documents (RAG) → `POST /api/v1/rag/documents` → **Try it out** → choose a file →
   **Execute** → `202`.
6. **Idempotency header:** Orders → `POST /api/v1/orders` shows the optional `Idempotency-Key` header field,
   taken from the existing `@RequestHeader`.

---

## 5. Production considerations

- **Restrict access.** API descriptions reveal every endpoint and parameter. Common options are to enable
  Swagger only in non-production environments, or to protect `/swagger-ui.html` and `/openapi/**` once
  security is added. To switch it off without code changes:
  ```yaml
  springdoc:
    api-docs:
      enabled: false
    swagger-ui:
      enabled: false
  ```
  Set this in the gateway, and `springdoc.api-docs.enabled: false` in each service. With
  `SPRINGDOC_API_DOCS_ENABLED=false` as an environment variable, no file change is needed.
- **"Try it out" is a real request.** It creates real orders and deletes real documents, with the same rate
  limits as any client. Use it against development data.
- **When security is added**, configure a security scheme (e.g. bearer JWT) in `OpenApiConfig`; the UI then
  shows an **Authorize** button.
- **Richer descriptions later.** `@Operation(summary = ...)`, `@Tag`, and `@Schema(example = ...)` on
  controllers and DTOs improve the docs. They're optional, and documentation-only.

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Dropdown entry shows "Failed to load API definition" | Service not running or not yet in Eureka | Start it; wait ~30 s; check http://localhost:8761 |
| Same, with `404` | Service built without the new dependency | `.\mvnw.cmd clean spring-boot:run` in that service |
| `/swagger-ui.html` returns 404 on the gateway | Gateway built without `springdoc-openapi-starter-webflux-ui` | Rebuild the gateway with `clean` |
| Service fails at startup with a springdoc class error | springdoc version doesn't match Spring Boot 4 | Use springdoc **3.x** (newest 3.0.x) in all 8 POMs |
| "Try it out" calls `localhost:8084` instead of `8080` | `OpenApiConfig` missing in that service | Copy `config/OpenApiConfig.java` |
| "Try it out" returns `429` | The gateway's rate limiter, working as designed | Wait a second; see RESILIENCE.md |
| A service lists `/actuator/...` operations | `springdoc:` section missing | Add the section from section 2 |
