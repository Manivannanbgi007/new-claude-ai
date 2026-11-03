# Eureka Server: Docker image

Put both files in the **eureka-server project folder**, next to `pom.xml`:

```
eureka-server/
├── Dockerfile
├── .dockerignore
├── pom.xml
└── src/
```

## 1. Build

```powershell
cd product-platform\eureka-server
docker build -t eureka-server:1.0.0 .
```

The first build downloads Maven dependencies, which takes a few minutes. Later builds reuse them through the
cache mount.

## 2. Run

```powershell
docker run -d --name eureka-server -p 8761:8761 --memory 512m eureka-server:1.0.0
```

`--memory 512m` sets the container's memory limit; the JVM uses 75 % of it for the heap.

## 3. Verify

```powershell
docker ps --filter name=eureka-server          # STATUS shows "(healthy)" after ~30-60 s
docker logs -f eureka-server                   # "Started EurekaServerApplication"
curl.exe http://localhost:8761/actuator/health # {"status":"UP",...}
```

Then open http://localhost:8761: the Eureka dashboard.

Check the image size and that the app doesn't run as root:

```powershell
docker images eureka-server                     # roughly 250-300 MB (JRE + Spring Cloud)
docker exec eureka-server id                    # uid=1001(spring) gid=1001(spring)
```

Stop and remove it:

```powershell
docker stop eureka-server; docker rm eureka-server
```

`docker stop` lets Spring Boot shut down gracefully, because Java runs as PID 1 (exec-form `ENTRYPOINT`).

---

## 4. What the Dockerfile does, step by step

| Step | What | Why |
|---|---|---|
| `FROM maven:3.9-eclipse-temurin-21 AS build` | Build stage with Maven + JDK 21 | No need for the Maven wrapper (and no Windows line-ending problems with `mvnw`) |
| `COPY pom.xml` then `COPY src` | Copy only what the build needs | `.dockerignore` keeps `target/`, `logs/`, and the IDE files out |
| `RUN --mount=type=cache ... mvn package -DskipTests` | Build the JAR | The cache mount keeps dependencies between builds without putting them in the image; tests belong in CI |
| `java -Djarmode=tools ... extract --layers` | Split the JAR into 4 layers | Dependencies (large, rarely changed) and your code (small, often changed) become separate image layers |
| `FROM eclipse-temurin:21-jre-alpine` | Runtime stage: JRE only | No JDK, Maven, or source code in the final image: smaller and fewer vulnerabilities |
| `adduser ... -u 1001` + `USER 1001:1001` | Non-root user | If the app is compromised, the attacker isn't root in the container |
| `COPY --from=build` (4 layers) | Copy only the extracted app | Files stay owned by root, so the app can't modify its own code |
| `ENV LOG_DIR=/app/logs` | Log folder for centralized logging | The only folder the app may write to |
| `ENV JAVA_TOOL_OPTIONS=...` | Container-aware JVM | Heap = 75 % of the container limit; exit on out-of-memory so Docker restarts it |
| `EXPOSE 8761` | Documents the port | Publishing still needs `-p 8761:8761` |
| `HEALTHCHECK` | Polls `/actuator/health` | `docker ps` shows healthy/unhealthy; orchestrators can wait for "healthy" |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | Exec form | Java gets `SIGTERM` directly → graceful shutdown |

---

## 5. Using it as a template for the other services

Copy `Dockerfile` and `.dockerignore` into the other project folder and change **only the port**, in two places:

```dockerfile
EXPOSE 8084
HEALTHCHECK ... CMD wget -qO- http://localhost:8084/actuator/health | grep -q '"status":"UP"' || exit 1
```

| Service | Port |
|---|---|
| eureka-server | 8761 |
| api-gateway | 8080 |
| product-service | 8081 |
| stock-service | 8082 |
| order-service | 8084 |
| product-write-service | 8089 |
| mcp-server | 8090 |
| mcp-client | 8091 |
| user-service | 8092 |
| rag-service | 8093 |

Everything else stays the same, because all services use Java 21, Maven, Spring Boot 4, and the actuator.

**Two cases need one extra line in the build stage:**

- **Services that depend on `event-schemas`** (order, stock, product, product-write): that library is in your
  local `~/.m2`, not in a public repository, so a clean Docker build can't download it. Publish it to a shared
  Maven repository, or build it inside the Docker build. Solve this before writing those Dockerfiles.
- **Services with extra Maven repositories** (Confluent for Avro) work unchanged, because they're declared in
  `pom.xml`.

---

## 6. Running with other containers (later)

`application.yml` uses `localhost` for Eureka's own address. Inside Docker, other containers reach Eureka by
its **container name**, so set this when you run it alongside the other services:

```powershell
docker run -d --name eureka-server --network product-platform_platform-net -p 8761:8761 `
  -e EUREKA_INSTANCE_HOSTNAME=eureka-server `
  -e LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs `
  eureka-server:1.0.0
```

- `EUREKA_INSTANCE_HOSTNAME`: Spring maps this to `eureka.instance.hostname`; no file change is needed.
- `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`: JSON logs on stdout, which is what container log collectors (e.g.
  for Loki) read.

The other services then use `EUREKA_URL=http://eureka-server:8761/eureka/`.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `the --mount option requires BuildKit` | Old Docker without BuildKit | Update Docker Desktop (BuildKit is the default), or set `$env:DOCKER_BUILDKIT=1` |
| `COPY src ./src`: not found | Build started outside the project folder | Run `docker build` in `eureka-server`, where `pom.xml` is |
| Build fails downloading dependencies | No internet or a proxy | Configure the proxy in Docker Desktop → Settings → Resources → Proxies |
| `extract: ... not found` / no `extracted/...` folders | Not a Spring Boot executable JAR | Check that `spring-boot-maven-plugin` is in `pom.xml` |
| Container `unhealthy` | App not started or crashed | `docker logs eureka-server` |
| `Port is already allocated` | Eureka still running from your IDE | Stop it, or use `-p 18761:8761` |
| Log message `Picked up JAVA_TOOL_OPTIONS: ...` | The JVM confirming the settings | Expected, not an error |
