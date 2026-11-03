# User Service

Manages the users (customers) of the platform: create, read, search, update, suspend or
re-activate, and delete.

| | |
|---|---|
| Port | `8092` |
| Database | Its own MySQL container `mysql-user` (localhost:**3309**), database `user_db`; schema by Flyway |
| Registers with | Eureka (`user-service`) |
| Security | **Not yet.** Authentication, roles, and passwords come in the security phase (section 7) |

---

## 1. Project structure

Same layout as stock-service and product-write-service:

```
user-service/
├── http/user-service.http            ← 10 POSTs + the full life cycle (34 requests)
├── pom.xml
└── src/main/
    ├── java/com/example/user/
    │   ├── UserServiceApplication.java
    │   ├── config/      WebConfig (case-insensitive enum query parameters)
    │   ├── controller/  UserController
    │   ├── dto/         UserCreateRequest, UserUpdateRequest, UserStatusUpdateRequest,
    │   │                UserSearchRequest, UserResponse, PageResponse
    │   ├── exception/   UserNotFound, DuplicateEmail, StaleVersion, InvalidUserState,
    │   │                GlobalExceptionHandler (ProblemDetail)
    │   ├── filter/      CorrelationIdFilter
    │   ├── mapper/      UserMapper
    │   ├── model/       User (entity), UserStatus, UserSortField, SortDirection
    │   ├── repository/  UserRepository, UserSpecifications (dynamic search filters)
    │   ├── service/     UserService
    │   │   └── impl/    UserServiceImpl (@Transactional)
    │   └── util/        AppConstants, StringToEnumIgnoreCaseConverterFactory
    └── resources/
        ├── application.yml
        └── db/migration/V1__create_users_table.sql
```

---

## 2. API

| Method | URL | Purpose | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/users` | Create a user (status ACTIVE) | 201 + Location | 400, 409 duplicate email |
| GET | `/api/v1/users` | Search, filter, sort, page | 200 `PageResponse` | 400 |
| GET | `/api/v1/users/{id}` | One user (also deleted ones) | 200 | 400, 404 |
| PUT | `/api/v1/users/{id}` | Full profile update (needs `version`) | 200 | 400, 404, 409 |
| PUT | `/api/v1/users/{id}/status` | Suspend / re-activate (needs `version`) | 200 | 400, 404, 409 |
| DELETE | `/api/v1/users/{id}` | Soft delete (idempotent) | 204 | 404 |

**Search parameters** (all optional):

| Parameter | Meaning |
|---|---|
| `q` | Text contained in first name, last name, or email (case-insensitive) |
| `email` | Exact email (case-insensitive) |
| `status` | `active`, `suspended`, or `deleted`. Without it, deleted users are **hidden** |
| `sortBy` | `created_at` (default, newest first), `last_name`, `email` |
| `sortDirection` | `asc` or `desc` |
| `page`, `size` | Zero-based page; size 1–100 (default 20) |

**Example response:**

```json
{
  "id": "3f0c5b0e-8d1e-4a57-9b6e-2c7f5d1a9e44",
  "email": "aarav.sharma@example.com",
  "firstName": "Aarav",
  "lastName": "Sharma",
  "fullName": "Aarav Sharma",
  "phoneNumber": "+919876543210",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-09-28T09:15:42.118Z",
  "updatedAt": "2026-09-28T09:15:42.118Z",
  "deletedAt": null
}
```

Errors use the same ProblemDetail format as every other service, for example
`409 {"title": "Duplicate email", "detail": "A user with email '...' already exists", ...}`.

---

## 3. Business rules (and where they live)

| Rule | Where |
|---|---|
| Email is unique (case-insensitive) across **all** users, including deleted ones | `UserServiceImpl` check + `uk_users_email` unique key as a safety net |
| Email stored lower-case and trimmed | `UserMapper.normalizeEmail` |
| New users start **ACTIVE** | `User` constructor |
| ACTIVE ⇄ SUSPENDED; both → DELETED; DELETED is final | `UserStatus.canChangeTo` |
| Updates must send the current `version` (no lost updates) | `UserServiceImpl.ensureVersion` + JPA `@Version` |
| Deleted users can't be changed | `UserServiceImpl.ensureNotDeleted` |
| `status` and `deleted_at` always agree | `ck_users_deleted` CHECK constraint |

**Why soft delete?** Orders and payments will store the user id. Deleting the row would leave that history
pointing to nothing. The row stays with status `DELETED` and a `deletedAt` timestamp, and is hidden from
normal lists. When privacy requirements such as GDPR "right to be forgotten" apply, a later step can
**anonymise** deleted users (replace name, email, and phone), still without breaking references.

**Why no password field?** Storing a password without hashing, even temporarily, is a real risk.
Credentials arrive with the security phase, hashed from the start and kept in their own table (section 7).

---

## 4. Setup

### 4.1 Database (database per service)

The User Service has its **own MySQL container**, `mysql-user` (host port **3309**). On its first start,
MySQL creates the database `user_db` and user `user_user` from `USER_DB_*` in `infrastructure/.env`, so
no manual SQL step is needed.

```powershell
cd product-platform\infrastructure
docker compose up -d mysql-user
docker compose ps mysql-user        # wait for "healthy"
docker exec -it mysql-user mysql -uuser_user -puser_dev_password -e "SHOW DATABASES;"
```

The last command must list `user_db`.

The `USER_DB_*` values are applied **only on the first start** (empty volume). If you change them later,
reset the volume: `docker compose rm -sf mysql-user`, `docker volume rm product-platform-mysql-user-data`,
then `docker compose up -d mysql-user`.

### 4.2 Project

Generate a skeleton on start.spring.io (Boot 4.0.8, Maven, Java 21, YAML, Artifact `user-service`,
Package `com.example.user`) with **Spring Web, Validation, Spring Data JPA, Flyway Migration, MySQL
Driver, Spring Boot Actuator, Eureka Discovery Client**. Then copy `pom.xml` and the whole `src` folder
from this project over it.

### 4.3 Gateway route

In `api-gateway/src/main/resources/application.yml`, add under `routes:`:

```yaml
            # ---------------- User Service ----------------
            - id: user-service
              uri: lb://user-service
              predicates:
                - Path=/api/v1/users/**
              filters:
                - name: Retry
                  args:
                    retries: 2
                    methods: GET
                    backoff:
                      firstBackoff: 100ms
                      maxBackoff: 500ms
                      factor: 2
                      basedOnPreviousValue: false
```

---

## 5. Run

Start order: `docker compose up -d` → `eureka-server` → `user-service`, then restart the gateway.

```powershell
cd product-platform\user-service
.\mvnw.cmd clean spring-boot:run
```

Expect:

```
Successfully applied 1 migration to schema `user_db`
Tomcat started on port 8092
```

Then check that **USER-SERVICE** is UP on the Eureka dashboard at http://localhost:8761.

---

## 6. Test

Run `http/user-service.http` **top to bottom**:

| Requests | What they show |
|---|---|
| 1–10 | Create 10 users: email normalisation (#3), optional phone (#4, #10), international names (#8–#10) |
| 11–16 | Get, list, sort, page, text search, exact email |
| 17–19 | Update with the right version; stale version (409); duplicate email (409) |
| 20–23 | Suspend → filter by status → re-activate; delete via the status endpoint is rejected |
| 24–29 | Soft delete: still readable, hidden from lists, not changeable, idempotent |
| 30–34 | Validation and error responses |

Run the POSTs **once**. Running them a second time returns 409 "Duplicate email", which is correct. To start
over, reset the table:

```powershell
docker exec -it mysql-user mysql -uuser_user -puser_dev_password user_db -e "DELETE FROM users;"
```

To look at the data directly:

```powershell
docker exec -it mysql-user mysql -uuser_user -puser_dev_password user_db -e "SELECT email, first_name, last_name, status, version FROM users ORDER BY created_at;"
```

---

## 7. Designed for later extensions

| Next feature | How it fits |
|---|---|
| **Security** (auth, roles, JWT) | A new `user_credential` table (user id, password **hash**, failed attempts) and a `user_role` table, added with `V2__`/`V3__` migrations. Profile code stays unchanged; the gateway validates JWTs. |
| **Addresses** (needed for delivery) | A `user_address` table (many per user) with its own DTOs and `/api/v1/users/{id}/addresses` endpoints |
| **User events for other services** | Same pattern as the product module: an outbox table written in the same transaction, and Debezium publishes `UserRegistered` / `UserUpdated` Avro events. Order-service can then keep a local copy of customer data instead of calling this service. |
| **More search filters** | One more `if` block in `UserSpecifications` and one field in `UserSearchRequest` |
| **Anonymising deleted users** | A scheduled job or a `DELETE ...?erase=true` variant that overwrites personal data on DELETED rows |

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Access denied for user 'user_user'` | `mysql-user` was first started with different `USER_DB_*` values, or the app points to the wrong port | Check that the URL uses port 3309; otherwise reset the volume (4.1) |
| `Unknown database 'user_db'` | Same | Same |
| `Schema-validation: missing table [users]` | Flyway didn't run | `spring-boot-starter-flyway` and `flyway-mysql` must be in the pom |
| 409 "Duplicate email" when re-running the POSTs | Users already exist | Expected; reset with the `DELETE FROM users` command above |
| 400 on `status`: "JSON parse error" | Status value in the body isn't an exact enum name | Use `ACTIVE` or `SUSPENDED` in upper case in request bodies (query parameters accept any case) |
| 503 "Database unavailable" | MySQL not running | `docker compose ps mysql-user` |
| `Port 8092 was already in use` | Another instance is running | Stop it: `netstat -ano \| findstr :8092` |
