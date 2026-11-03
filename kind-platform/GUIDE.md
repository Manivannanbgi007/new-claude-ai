# The complete product platform on Kind

This guide moves the **entire** Docker Compose environment (databases, Kafka, CDC, search, AI, and all Spring
Boot services) into a local Kind cluster, and tests the same business flows there. Docker Compose isn't needed to
run anything afterwards.

**No Java code changes.** Every service already reads its connections from environment variables (`USER_DB_URL`,
`KAFKA_BOOTSTRAP_SERVERS` ...). Kubernetes provides different values for them through ConfigMaps and Secrets.

---

## Contents

| Part | Steps |
|---|---|
| A. Understand | Architecture · what changes vs Compose · deployment order · resources |
| B. Build | 1 cluster · 2 images · 3 load images |
| C. Deploy (in order) | 4–5 MySQL + storage · 6–7 Kafka + topics · event-schemas · search/cache/AI · 8–15 services · CDC |
| D. Expose | 16 networking · 17 Services/Ingress · 18 NGINX Ingress Controller |
| E. Operate | 19–20 test & verify · 21 logs/troubleshooting · 22 scale · 23 restart/update · 24 failure tests · 25 Compose parity |

---

# A. Understand

## Architecture in the cluster

```
Windows ── http://localhost:30080 (NodePort) ── or ── http://platform.localtest.me (Ingress) ──┐
                                                                                                ▼
┌────────────────────────────────── namespace product-platform ──────────────────────────────────┐
│                                       api-gateway (8080) ── redis                                │
│        ┌──────────┬───────────┬──────────┼──────────┬───────────┬────────────┐                    │
│  product-write  product    stock      order       user       rag        mcp-client ── ollama     │
│     (8089)      (8081)     (8082)     (8084)     (8092)     (8093)        (8091)                 │
│       │           │          │          │          │        │   │            │                   │
│  mysql-product  elastic-  mysql-stock mysql-order mysql-user │  reranker    mcp-server (8090)     │
│       │         search       │          │                 postgres-rag      └──► api-gateway      │
│       │ binlog     ▲         └────┬─────┘                                                        │
│  kafka-connect     │              │ order.events.v1 / stock.events.v1 (saga)                     │
│  (Debezium) ──────►kafka◄─────────┘──── product.catalog.v1 (outbox) ◄── product-write            │
│                    │  └── schema-registry ◄── Job: event-schemas registration                    │
│                 kafka-ui                                                                         │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
```

## What changes compared with Docker Compose

| Topic | Docker Compose | Kubernetes (this setup) |
|---|---|---|
| Find another service | Container name + Eureka | **Service DNS name** (`order-service`), no Eureka |
| Gateway `lb://` routes | Eureka | **SimpleDiscoveryClient**: one environment variable maps each name to its Service |
| Kafka address | `kafka:29092` (containers) / `localhost:9092` (apps on Windows) | **`kafka:9092`** for everyone: all apps run in the cluster |
| Schema Registry | `localhost:18081` | `schema-registry:8081` |
| Configuration | `.env` + `environment:` | **ConfigMaps** (settings) + **Secrets** (passwords) |
| Start order | `depends_on` | **init containers** wait for dependencies + **readiness probes** |
| One-off containers (`kafka-init`, `debezium-init`, `ollama-model-init`) | Containers that exit | **Jobs** |
| Volumes | Named Docker volumes | **PersistentVolumeClaims** |
| Health | `healthcheck:` | **startup / liveness / readiness probes** |
| Restart | `restart: unless-stopped` | Controllers (Deployment, StatefulSet) recreate Pods |
| Logs | Files in `product-platform/logs` | **stdout JSON**: `kubectl logs` |

**Two components from your list are deliberately not deployed:**

- **Config Server:** your project doesn't have one. Each service has its own `application.yml`, and Kubernetes
  ConfigMaps now provide the environment-specific values. That is the Kubernetes-native replacement for a
  Config Server.
- **Eureka Server:** Kubernetes Services already do discovery and load balancing. Running Eureka as well would
  mean two registries that can disagree. All services get `EUREKA_CLIENT_ENABLED=false`. If you want to compare
  the two approaches, Eureka can be deployed with the same Deployment pattern (its Dockerfile exists), but nothing
  here depends on it.

Not migrated: **Kibana** and **pgAdmin** (optional UIs). Inspect Elasticsearch and PostgreSQL with
`kubectl exec` (step 20).

## Deployment order and why

```
1 Namespace
2 MySQL ×4 ──────────────────────────────────────────────┐
3 Kafka ─► Schema Registry ─► Job: kafka-topics           │
4 Job: event-schemas registration (needs Schema Registry) │
5 Elasticsearch, Redis                                    │
6 PostgreSQL, Ollama ─► Job: ollama-models, reranker      │ (AI part, optional)
7 product-write, user, stock, order, product ◄────────────┘ (need DBs, Kafka, schemas, topics)
8 rag-service, mcp-server, mcp-client                       (need Postgres, Ollama)
9 api-gateway                                               (routes to all of the above)
10 Kafka Connect ─► Job: Debezium connector                 (product table must exist → after step 7)
11 Exposure (NodePort / Ingress)
```

Kubernetes doesn't *need* this order: init containers make every Pod wait for its dependencies anyway. Following
it means each layer is verified before the next one depends on it, so an error points at **one** layer.

## Folder structure

```
kind-platform/
├── GUIDE.md
├── kind/kind-cluster.yaml                 cluster: 1 control-plane + 2 workers, port mappings
├── docker/
│   ├── Dockerfile.service                 ONE Dockerfile for all 9 Spring Boot services
│   ├── Dockerfile.service.dockerignore
│   └── Dockerfile.event-schemas-registration
├── ingress/nginx-ingress-values.yaml      Helm values for the NGINX Ingress Controller
├── scripts/
│   ├── build-images.ps1                   build + kind load all images
│   ├── deploy-all.ps1                     apply everything in order, wait after each layer
│   └── test-platform.ps1                  end-to-end business test through the gateway
└── manifests/
    ├── 00-namespace.yaml
    ├── 10-databases/      mysql-product, mysql-stock, mysql-order, mysql-user, postgres-rag
    ├── 20-kafka/          kafka, schema-registry, kafka-topics-job, kafka-ui
    ├── 25-event-schemas/  schema-registration-job
    ├── 30-platform/       elasticsearch, redis
    ├── 35-ai/             ollama (+ ai-models ConfigMap), ollama-models-job, reranker
    ├── 40-apps/           one file per service: ConfigMap + Deployment + Service
    ├── 45-cdc/            kafka-connect, debezium-connector-job
    └── 50-exposure/       api-gateway-nodeport, ingress
```

Each file contains **everything one component needs** (Secret, Service, StatefulSet/Deployment ...), separated by
`---`. One component, one file.

**What was validated (without a cluster):**

- All 70 resources pass the Kubernetes 1.33 API schemas.
- Every `localhost` default in each `application.yml` is overridden.
- Ports match `server.port`.
- Every waited-for dependency is a real Service.
- All 7 gateway `lb://` routes resolve.
- The scripts parse, and `test-platform.ps1` passed against a mock of the platform.

**The first real run is on your machine.**

## Memory: what the platform needs

| Part | Components | Memory (approx.) |
|---|---|---|
| Kubernetes itself | 3 Kind nodes | ~1.5 GB |
| Core | 4× MySQL, Kafka, Schema Registry, Connect, Kafka UI, Elasticsearch, Redis | ~5.5 GB |
| Core services | product-write, product, stock, order, user, api-gateway | ~3 GB |
| **AI part** | Ollama + qwen2.5:7b, reranker, PostgreSQL, rag, mcp-server, mcp-client | **~8.5 GB** |
| **Total** | | **~18–19 GB** (~10 GB without AI) |

On Windows, Docker Desktop's memory comes from WSL2. Set it in `C:\Users\<you>\.wslconfig`:

```ini
[wsl2]
memory=20GB
```

Then run `wsl --shutdown` and restart Docker Desktop.

**Less memory available?**

- Deploy without AI: `deploy-all.ps1 -SkipAi`.
- Or use the smaller model `qwen2.5:3b` (one line in `manifests/35-ai/ollama.yaml`).

Stop the Compose environment first to free its memory. `docker compose down` keeps its volumes, so you can go
back to it anytime.

---

# B. Build

## Prerequisites

| Tool | Check | Install |
|---|---|---|
| Docker Desktop | `docker version` | docker.com |
| kind | `kind version` | `winget install Kubernetes.kind` |
| kubectl | `kubectl version --client` | `winget install Kubernetes.kubectl` |
| Helm (only for step 18) | `helm version` | `winget install Helm.Helm` |

Put the `kind-platform` folder **inside** `product-platform`, next to your projects:

```
product-platform/
├── api-gateway/  event-schemas/  infrastructure/  mcp-client/  mcp-server/  order-service/
├── product-service/  product-write-service/  rag-service/  stock-service/  user-service/
└── kind-platform/        ← here
```

## Step 1: Create and configure the Kind cluster

```powershell
cd product-platform\kind-platform
kind delete cluster --name product-platform        # only if the user-service cluster from before exists
kind create cluster --config kind\kind-cluster.yaml
```

`kind\kind-cluster.yaml` creates **3 nodes** (1 control-plane, 2 workers) and maps 3 ports to Windows. That's
only possible at creation time, which is why the old cluster is recreated:

| Windows | → Node port | Used by |
|---|---|---|
| `localhost:30080` | 30080 | api-gateway NodePort Service |
| `localhost:80` | 30100 | NGINX Ingress Controller (HTTP) |
| `localhost:443` | 30443 | NGINX Ingress Controller (HTTPS) |

**Verify:**

```powershell
kubectl get nodes                          # 3 nodes, STATUS Ready
kubectl get storageclass                   # "standard (default)": Kind's local-path provisioner
kubectl cluster-info --context kind-product-platform
```

If creation fails with "port is already allocated", another program (often IIS) uses port 80. Change
`hostPort: 80` to `8000` in `kind-cluster.yaml`, and later use `http://platform.localtest.me:8000`.

## Step 2: Build the Docker images

**The reference first: `event-schemas`.** It isn't a service, so it never runs as a Pod. It's a **library**, and
it has two jobs in Kubernetes:

| When | What | How it's solved here |
|---|---|---|
| **Build time** | order, stock, product-write, and product compile against the JAR | `Dockerfile.service` builds **event-schemas first** and installs it into the build's Maven cache, then builds the service. No `~/.m2` needed. |
| **Runtime** | Its Avro schemas must be registered in Schema Registry | A **Job** runs once (step 7b), using the small `event-schemas-registration` image |

**One Dockerfile for all 9 services:** `docker/Dockerfile.service`, with `--build-arg SERVICE=<folder>`. Its build
context is `product-platform\`, so it can see `event-schemas\`. The runtime stage is identical to the Eureka
reference (JRE Alpine, non-root user 1001, layered JAR); only `HEALTHCHECK` is left out, because Kubernetes probes
replace it.

**Build everything** (from `product-platform\`):

```powershell
cd product-platform
.\kind-platform\scripts\build-images.ps1
```

This builds 11 images:

- the 9 services (`order-service:1.0.0` ...);
- **Kafka Connect with Debezium**: your `infrastructure\kafka-connect\Dockerfile`, tagged
  `product-platform/kafka-connect:8.0.0-debezium-3.2.0.Final`;
- **`event-schemas-registration:1.0.0`**.

The first build takes a while (Maven downloads). Later builds reuse the Maven cache.

**Build one image by hand** (what the script does for each service):

```powershell
docker build -f kind-platform/docker/Dockerfile.service --build-arg SERVICE=order-service --build-arg PORT=8084 -t order-service:1.0.0 .
```

**Verify:**

```powershell
docker images --format "{{.Repository}}:{{.Tag}}  {{.Size}}" | Select-String "1.0.0|kafka-connect"
```

## Step 3: Load the images into Kind

Kind's nodes are Docker containers with their **own** image storage; they can't see your local images.
`build-images.ps1` already ran `kind load` for each image (use `-SkipLoad` to skip that). By hand:

```powershell
kind load docker-image order-service:1.0.0 --name product-platform
```

**Verify** on a node:

```powershell
docker exec product-platform-worker crictl images | Select-String "1.0.0|kafka-connect"
```

Every Deployment uses `imagePullPolicy: IfNotPresent` and a fixed tag, so the loaded image is used and nothing is
downloaded. Public images (mysql, Kafka, Elasticsearch, Ollama ...) are downloaded by the nodes themselves from
the internet. The first deployment therefore needs a good connection and takes longer.

---

# C. Deploy

**Fast path:** `.\scripts\deploy-all.ps1` (from `kind-platform\`) runs steps 4–15 in order and **waits** after
each layer. The manual steps below do the same, one layer at a time, with the explanations and checks. Learn with
the manual steps once, then use the script.

```powershell
cd product-platform\kind-platform
kubectl apply -f manifests\00-namespace.yaml
kubectl config set-context --current --namespace=product-platform     # saves typing "-n product-platform"
```

## Step 4: Deploy MySQL

Each database gets **one file** with three resources:

| Resource | Why |
|---|---|
| **Secret** `<x>-db-credentials` | Database name, user, password, root password. Used by MySQL **and** the app. |
| **Service** (headless, `clusterIP: None`) | DNS name `mysql-order` → the MySQL Pod. A StatefulSet requires a headless Service for its stable Pod names (`mysql-order-0`). |
| **StatefulSet** | Runs MySQL with a **stable identity** and **its own disk**. A Deployment would treat the Pods as interchangeable and couldn't give each its own volume. |

The 4 databases are the same as in Compose ("database per service"). The Compose service `mysql` is now named
**`mysql-stock`**, which is clearer.

| Compose | Kubernetes | Special |
|---|---|---|
| `mysql-product` | `mysql-product` | Binlog on (`--log-bin`, `--binlog-row-image=FULL`) + an init script creating the **Debezium** user (a Secret mounted to `/docker-entrypoint-initdb.d`) |
| `mysql` | `mysql-stock` | |
| `mysql-order` | `mysql-order` | |
| `mysql-user` | `mysql-user` | |

The MySQL server options are exactly the Compose `command:` options, as container `args`.

```powershell
kubectl apply -f manifests\10-databases\mysql-product.yaml -f manifests\10-databases\mysql-stock.yaml `
              -f manifests\10-databases\mysql-order.yaml -f manifests\10-databases\mysql-user.yaml
kubectl rollout status statefulset/mysql-product --timeout=300s     # repeat for the other three
```

**Verify:**

```powershell
kubectl get statefulsets,pods -l app.kubernetes.io/component=database
kubectl exec mysql-order-0 -- sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -e "SHOW DATABASES;"'
kubectl exec mysql-product-0 -- sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "SELECT user FROM mysql.user WHERE user=''debezium''; SHOW VARIABLES LIKE ''log_bin'';"'
```

Expect `order_db` in the list, the `debezium` user, and `log_bin ON`.

## Step 5: MySQL storage (PersistentVolumes and PersistentVolumeClaims)

```
StatefulSet.volumeClaimTemplates ──creates──► PVC data-mysql-order-0 ──bound to──► PV (2 Gi, on a node's disk)
                                                     ▲ created automatically by Kind's StorageClass "standard"
```

| Object | Meaning |
|---|---|
| **PersistentVolumeClaim (PVC)** | "I need 2 Gi of disk": the request. One per StatefulSet Pod, created from `volumeClaimTemplates`. |
| **PersistentVolume (PV)** | The actual disk. Kind's *local-path provisioner* creates it automatically (**dynamic provisioning**): a folder on the node. |
| **StorageClass** `standard` | *How* volumes are created. Kind's default. |

**You never write a PV by hand.** Dynamic provisioning creates it when the PVC appears. That's how production
clusters work too, just with cloud disks instead of node folders.

**Verify:**

```powershell
kubectl get pvc                      # all "Bound", e.g. data-mysql-order-0  2Gi
kubectl get pv                       # the volumes created for them
```

**Behaviour to know:**

- **Deleting a Pod or the StatefulSet keeps the PVC**, so the data survives.
  `kubectl delete pvc data-mysql-order-0` deletes the data for good.
- **Kind's local-path volumes live on one node.** A database Pod can only run on the node that has its data. In
  real clusters, network disks (EBS, Azure Disk ...) can move between nodes. You'll see this in failure test 24.6.
- **Init SQL runs only on an empty volume.** That's the same as Compose.

## Step 6: Deploy Kafka and its dependencies

| File | Resources | Why |
|---|---|---|
| `20-kafka/kafka.yaml` | Headless **Service** + **StatefulSet** (5 Gi PVC) | One broker in KRaft mode (no ZooKeeper). StatefulSet because it stores data and needs a stable name. |
| `20-kafka/schema-registry.yaml` | **Service** + **Deployment** | Stateless (schemas are stored *in* Kafka), so a Deployment |
| `20-kafka/kafka-ui.yaml` | Service + Deployment | Optional UI for topics, messages, consumers, and schemas |

**Same image and settings as Compose** (`confluentinc/cp-kafka:8.0.0`, the same `CLUSTER_ID`, replication factor
1). The difference is the listeners:

- Compose needed `PLAINTEXT://kafka:29092` (containers) **plus** `PLAINTEXT_HOST://localhost:9092` (apps on
  Windows).
- In Kubernetes all clients are in the cluster, so there's **one** listener:
  `PLAINTEXT://kafka-0.kafka.product-platform.svc.cluster.local:9092`.
- **Apps use `kafka:9092` as the bootstrap address.**

Three Kubernetes details in `kafka.yaml` that commonly trip people up:

1. **`publishNotReadyAddresses: true`** on the headless Service. Kafka must resolve its *own* name
   (`kafka-0.kafka...`) while starting, but by default DNS only lists *ready* Pods.
2. **`enableServiceLinks: false`.** Otherwise Kubernetes injects `KAFKA_PORT=tcp://...` into the Pod, and the
   Confluent image turns every `KAFKA_*` variable into a broker setting, so the broker fails to start. The same
   applies to `SCHEMA_REGISTRY_PORT`. **Every** Pod in this setup has it switched off (step 16).
3. **`fsGroup: 1000`.** The image runs as user 1000, which must be allowed to write to the volume.

```powershell
kubectl apply -f manifests\20-kafka\kafka.yaml
kubectl rollout status statefulset/kafka --timeout=300s
kubectl apply -f manifests\20-kafka\schema-registry.yaml
kubectl rollout status deployment/schema-registry --timeout=300s
```

**Verify:**

```powershell
kubectl exec kafka-0 -- kafka-broker-api-versions --bootstrap-server localhost:9092 | Select-Object -First 1
kubectl exec deploy/schema-registry -- curl -s localhost:8081/subjects        # [] (empty, for now)
```

## Step 7: Kafka topics and other Kafka settings

**7a. Topics: a Job (replaces `kafka-init`).** `20-kafka/kafka-topics-job.yaml` has a **ConfigMap** with the
same script as Compose (mounted as a file), and a **Job** that runs it once. `--if-not-exists` makes it safe to
run again.

| Topic | Settings | Purpose |
|---|---|---|
| `order.events.v1`, `stock.events.v1`, `payment.events.v1` | 3 partitions, 7 days | Saga events |
| `product.catalog.v1` | **compacted** | Latest product per key (order-service's catalogue) |
| `cdc.product_db.product` | compacted | Debezium → product-service |
| `*.dlt` (4 topics) | 14 days | Dead letter topics |

Kafka-wide settings live in the broker's environment (`kafka.yaml`): `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`
(topics only from the Job), replication factors of 1 (single broker), and heap 512 MB.

```powershell
kubectl apply -f manifests\20-kafka\kafka-topics-job.yaml
kubectl wait --for=condition=complete job/kafka-topics --timeout=300s
kubectl logs job/kafka-topics                         # ends with the list of topics
kubectl apply -f manifests\20-kafka\kafka-ui.yaml
```

**7b. `event-schemas`: register the Avro schemas (a Job).** `25-event-schemas/schema-registration-job.yaml`
contains:

| Resource | Purpose |
|---|---|
| ConfigMap `event-schemas-config` | `SCHEMA_REGISTRY_URL=http://schema-registry:8081` |
| Job `event-schemas-registration-1-0-0` | Init container waits for Schema Registry → `mvn schema-registry:test-compatibility schema-registry:register -Dschema.registry.url=...` |

`test-compatibility` runs **first**, so an incompatible schema change fails the Job before anything is
registered. The version is in the Job's name: a new event-schemas release means a new Job, and it must run
**before** the services that produce the new events are updated.

```powershell
kubectl apply -f manifests\25-event-schemas\schema-registration-job.yaml
kubectl wait --for=condition=complete job/event-schemas-registration-1-0-0 --timeout=600s
kubectl exec deploy/schema-registry -- curl -s localhost:8081/subjects
```

Expect subjects such as `order.events.v1-...OrderCreated`.

**7c. Search, cache, and AI infrastructure:**

| File | Kind | Why |
|---|---|---|
| `30-platform/elasticsearch.yaml` | StatefulSet + Service, 5 Gi | Product search index; same settings as Compose (single-node, security off, 1 GB heap) |
| `30-platform/redis.yaml` | **Deployment** + Service, **no volume** | Rate-limit counters only live for seconds; nothing to keep |
| `10-databases/postgres-rag.yaml` | StatefulSet + Secret + Service | pgvector for rag-service |
| `35-ai/ollama.yaml` | StatefulSet, **15 Gi** PVC + ConfigMap `ai-models` | The LLM. Models are stored on the PVC, so they survive restarts. |
| `35-ai/ollama-models-job.yaml` | **Job** | Replaces `ollama-model-init`: pulls both models through the Ollama Service |
| `35-ai/reranker.yaml` | StatefulSet, 5 Gi | Downloads its model on the first start (up to 20 min allowed by the startup probe) |

```powershell
kubectl apply -f manifests\30-platform\
kubectl rollout status statefulset/elasticsearch --timeout=600s
kubectl exec elasticsearch-0 -- curl -s "localhost:9200/_cluster/health?pretty"     # "status" : "green"

# AI part (skip without enough memory):
kubectl apply -f manifests\10-databases\postgres-rag.yaml -f manifests\35-ai\ollama.yaml -f manifests\35-ai\reranker.yaml
kubectl rollout status statefulset/ollama --timeout=600s
kubectl apply -f manifests\35-ai\ollama-models-job.yaml
kubectl logs -f job/ollama-models          # download progress; first time several GB
```

## Steps 8 + 9: Deploy the Spring Boot services (Deployments and Services)

Every service file in `40-apps/` has the **same three resources**, copied from the user-service reference:

| Resource | Purpose |
|---|---|
| **ConfigMap** `<service>-config` | All non-secret settings as environment variables |
| **Deployment** `<service>` | Pods, replicas, rolling updates, probes, resources, security |
| **Service** `<service>` (ClusterIP) | Stable internal DNS name + load balancing across the ready Pods |

Each Deployment has an **init container** that waits for exactly that service's dependencies. This is the
replacement for `depends_on`:

| Service | Waits for (`DEPENDS_ON`) |
|---|---|
| product-write-service | `mysql-product:3306 kafka:9092 schema-registry:8081` |
| product-service | `elasticsearch:9200 kafka:9092 schema-registry:8081` |
| stock-service | `mysql-stock:3306 kafka:9092 schema-registry:8081` |
| order-service | `mysql-order:3306 kafka:9092 schema-registry:8081` |
| user-service | `mysql-user:3306` |
| rag-service | `postgres-rag:5432 ollama:11434` (the reranker is optional) |
| mcp-client | `ollama:11434 mcp-server:8090` (its MCP client connects at startup) |
| mcp-server, api-gateway | nothing (Redis is optional for the gateway: rate limiting fails open) |

```powershell
kubectl apply -f manifests\40-apps\product-write-service.yaml -f manifests\40-apps\user-service.yaml `
              -f manifests\40-apps\stock-service.yaml -f manifests\40-apps\order-service.yaml `
              -f manifests\40-apps\product-service.yaml
kubectl get pods -w          # Init:0/1 → PodInitializing → Running 0/1 → Running 1/1; Ctrl+C to stop
```

Then the AI services and the gateway:

```powershell
kubectl apply -f manifests\40-apps\rag-service.yaml -f manifests\40-apps\mcp-server.yaml -f manifests\40-apps\mcp-client.yaml
kubectl apply -f manifests\40-apps\api-gateway.yaml
kubectl rollout status deployment/api-gateway --timeout=600s
```

**CDC last** (the `product` table must exist, and product-write-service's Flyway creates it):

```powershell
kubectl apply -f manifests\45-cdc\kafka-connect.yaml
kubectl rollout status deployment/kafka-connect --timeout=600s
kubectl apply -f manifests\45-cdc\debezium-connector-job.yaml
kubectl wait --for=condition=complete job/debezium-connector --timeout=600s
kubectl logs job/debezium-connector             # ends with "state":"RUNNING"
```

| CDC file | Resources |
|---|---|
| `kafka-connect.yaml` | Service + Deployment using **your** Connect + Debezium image. Connect stores its state in Kafka topics, so a Deployment. |
| `debezium-connector-job.yaml` | **Secret** with the connector JSON (it contains the Debezium password) + a **Job** that `PUT`s it to Kafka Connect (replaces `debezium-init`). The only change to the JSON vs Compose: `kafka:9092` instead of `kafka:29092`. |

## Step 10: ConfigMaps and Secrets

| | ConfigMap | Secret |
|---|---|---|
| For | URLs, flags, model names, logging | Passwords, the connector config, init SQL with passwords |
| Stored | Plain text | base64: **encoding, not encryption** |
| In this setup | `<service>-config`, `ai-models`, `kafka-topics-script`, `event-schemas-config` | `<db>-db-credentials`, `rag-db-credentials`, `mysql-product-init`, `debezium-connector-config` |

**One Secret per database, used by two consumers.** For example, `order-db-credentials` provides `MYSQL_USER` /
`MYSQL_PASSWORD` to MySQL **and** `ORDER_DB_USERNAME` / `ORDER_DB_PASSWORD` to order-service. Change it in one
place, and both stay in sync.

**One shared ConfigMap: `ai-models`.** `OLLAMA_CHAT_MODEL` and `OLLAMA_EMBEDDING_MODEL` are read by the
model-download Job, rag-service, **and** mcp-client. To switch the model, edit one line, re-run the Job, and
restart the two services.

**In real environments, Secrets don't go into git.** Create them with
`kubectl create secret generic ... --from-literal=...`, or use Sealed Secrets, External Secrets, or a cloud secret
manager. The files here are for local learning.

```powershell
kubectl get configmaps,secrets
kubectl describe configmap order-service-config
kubectl get secret order-db-credentials -o jsonpath="{.data.username}"    # base64 - decoding is trivial
```

## Step 11: Environment variables

Each container gets its environment from:

```yaml
envFrom:
  - configMapRef: { name: order-service-config }   # ALL keys as variables
env:                                               # ONLY the secret keys this app needs
  - name: ORDER_DB_PASSWORD
    valueFrom: { secretKeyRef: { name: order-db-credentials, key: password } }
```

Spring Boot maps each variable to its `application.yml` placeholder or property:

| Variable (examples) | Overrides | Value in Kind |
|---|---|---|
| `ORDER_DB_URL` | `${ORDER_DB_URL:jdbc:mysql://localhost:3310/...}` | `jdbc:mysql://mysql-order:3306/order_db?...` |
| `KAFKA_BOOTSTRAP_SERVERS` | `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` | `kafka:9092` |
| `SCHEMA_REGISTRY_URL` | `${...:http://localhost:18081}` | `http://schema-registry:8081` |
| `ELASTICSEARCH_URIS` | product-service | `http://elasticsearch:9200` |
| `OLLAMA_BASE_URL`, `RERANKER_URL`, `RAG_DB_URL` | rag-service | `http://ollama:11434`, `http://reranker:8095`, `jdbc:postgresql://postgres-rag:5432/rag_db` |
| `MCP_SERVER_URL` | mcp-client | `http://mcp-server:8090` |
| `PRODUCT_API_BASE_URL` | mcp-server | `http://api-gateway:8080` (MCP tools still go through the gateway) |
| `REDIS_HOST` | api-gateway | `redis` |
| `SPRING_APPLICATION_JSON` | api-gateway | The discovery map (step 12) |

**Common to every service:**

| Variable | Meaning |
|---|---|
| `EUREKA_CLIENT_ENABLED=false` | No Eureka |
| `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true` | Probe endpoints |
| `MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE` | What "ready" means (step 13) |
| `SERVER_SHUTDOWN=graceful` | Finish running requests on shutdown |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` and `LOGGING_FILE_NAME=""` | JSON on stdout, no log file |

Check what a Pod actually received:

```powershell
kubectl exec deploy/order-service -- env | Select-String "DB_URL|KAFKA|SCHEMA|EUREKA"
```

## Step 12: Service discovery between the microservices

```
order-service ──"mysql-order:3306"──► CoreDNS ──► headless Service → Pod IP of mysql-order-0
product-write ──"kafka:9092"──────────► CoreDNS ──► kafka-0 ──► tells clients "use kafka-0.kafka...:9092"
mcp-server ──"http://api-gateway:8080"► CoreDNS ──► ClusterIP 10.96.x.y ──kube-proxy──► a READY gateway Pod
api-gateway ──"lb://order-service"──► SimpleDiscoveryClient: order-service = http://order-service:8084
                                       └─► CoreDNS ──► ClusterIP ──kube-proxy──► a READY order-service Pod
```

- **Every Service gets a DNS name.** Inside the namespace, the short name works (`order-service`). The full name
  is `order-service.product-platform.svc.cluster.local`.
- **ClusterIP Service:** one virtual IP; kube-proxy spreads connections over the **ready** Pods.
- **Headless Service** (`clusterIP: None`): DNS returns the Pod IPs directly. It's used for StatefulSets
  (databases, Kafka), which need to address one specific Pod.
- **The gateway keeps its `lb://` routes** and all their filters (rate limiting, circuit breaker, retry). Spring
  Cloud's **SimpleDiscoveryClient**, configured in the `SPRING_APPLICATION_JSON` variable, now answers
  "where is order-service?" with `http://order-service:8084`. Kubernetes then load-balances across the Pods.
  When you scale a service, nothing in the gateway changes.

**Try it:**

```powershell
kubectl run net-test --rm -it --restart=Never --image=busybox:1.36 -- sh -c `
  "nslookup order-service; nslookup kafka; wget -qO- http://order-service:8084/actuator/health"
```

## Step 13: Liveness and readiness probes

**Spring Boot services** (all the same; health endpoints from Spring Boot Actuator):

| Probe | Endpoint | Fails → |
|---|---|---|
| **startup** | `/actuator/health/liveness`, up to 60 × 5 s = 5 min | Restarted. The other probes wait for it, so slow JVM starts on a busy laptop aren't killed. |
| **liveness** | `/actuator/health/liveness` | Container **restarted** (the app is stuck) |
| **readiness** | `/actuator/health/readiness` | **Removed from its Service** (not restarted) until it's healthy again |

**What "ready" includes** (`MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE`):

- **Services with a database:** `readinessState,db`. If their own DB is down, they receive no traffic.
- **Kafka is deliberately NOT part of readiness.** The consumers retry on their own, and an outage is absorbed by
  the outbox and by Kafka itself (failure test 24.3).
- **Liveness never checks dependencies.** If MySQL is down, restarting the app fixes nothing and only causes a
  restart storm.

**Infrastructure probes:**

| Component | Probe |
|---|---|
| MySQL | `mysqladmin ping` (exec) |
| PostgreSQL | `pg_isready` |
| Kafka | TCP on 9092 |
| Schema Registry, Kafka Connect | HTTP `/subjects`, `/connectors` |
| Elasticsearch | `/_cluster/health?local=true` |
| Redis | `redis-cli ping` |
| Ollama, reranker | HTTP `/`, `/health` |

```powershell
kubectl describe pod -l app.kubernetes.io/name=order-service | Select-String "Liveness|Readiness|Startup"
kubectl exec deploy/order-service -- wget -qO- localhost:8084/actuator/health/readiness
```

## Step 14: Resource requests and limits

| | requests (reserved) | limits (maximum) |
|---|---|---|
| Spring services | 100m CPU, 384 Mi (rag: 512 Mi) | 1 CPU, 768 Mi (rag: 1 Gi) |
| MySQL ×4 | 100m, 384 Mi | 768 Mi |
| Kafka / Connect | 250m, 768 Mi | 1 Gi (heap 512 MB) |
| Schema Registry | 100m, 384 Mi | 512 Mi (heap 256 MB) |
| Elasticsearch | 250m, 1.5 Gi | 2 Gi (heap 1 GB) |
| Ollama | 1 CPU, 4 Gi | 7 Gi |
| Reranker | 250m, 1 Gi | 2 Gi |

- **requests:** the scheduler places a Pod only where this much is free. **limits:** above the memory limit, the
  container is killed (`OOMKilled`); above the CPU limit, it's slowed down.
- **The JVM heap is always below the limit:** `-XX:MaxRAMPercentage=75` in the image (→ ~576 Mi of 768 Mi), and
  explicit heaps for Kafka and Elasticsearch, leaving room for non-heap memory.
- **A Kind detail:** every Kind node reports the *whole* Docker memory as its own, so 3 nodes look like 3× your
  RAM. The scheduler won't stop you from overcommitting, but Docker Desktop will run out of memory. That's why
  the memory table in part A matters.

```powershell
kubectl describe node product-platform-worker | Select-String -Context 0,15 "Allocated resources"
```

## Step 15: Replicas

Every service runs **1 replica** here, so the whole platform fits on a laptop. Step 22 shows how to scale.

| Production | Why |
|---|---|
| ≥ 2 replicas for stateless services | Survive a Pod or node failure; zero-downtime updates |
| + a PodDisruptionBudget `minAvailable: 1` | Keeps one Pod running during node maintenance |
| **No** PDB with `minAvailable: 1` when `replicas: 1` | It would block node drains forever, which is why there are no PDBs here |
| Databases and Kafka: replicas only through operators | Replication needs coordination (Strimzi, CloudNativePG, MySQL Operator); more StatefulSet replicas alone don't create a cluster |

---

# D. Expose

## Step 16: Networking between all components

```
Kind node (Docker container)            Kind node                              Kind node
┌──────────────────────┐   Pod network (kindnet CNI): every Pod can reach every Pod by IP
│ Pod 10.244.1.5        │◄───────────────────────────────────────────────────────────►Pod 10.244.2.7
└──────────────────────┘
           Services: stable virtual IPs (10.96.x.x) → kube-proxy rules on every node → ready Pods
           CoreDNS:  <service> → Service IP   (headless: → Pod IPs)
```

| Layer | Provided by | In this setup |
|---|---|---|
| Pod ↔ Pod | CNI plugin (Kind: kindnet) | Flat network; no port mapping between containers |
| Stable names/IPs | Services + CoreDNS | 23 Services; apps only use DNS names |
| Inside → outside | Node network | Nodes download images; the Ollama/reranker models come from the internet |
| Outside → inside | NodePort / Ingress + Kind `extraPortMappings` | Only the api-gateway (step 17) |

**`enableServiceLinks: false` on every Pod.** Kubernetes would otherwise add Docker-link-style variables for
*every* Service to *every* Pod (`REDIS_PORT=tcp://10.96.x.x:6379`, `KAFKA_PORT=...`). Those break the gateway
(`${REDIS_PORT}` must be a number) and the Confluent images. DNS is the discovery mechanism, and these variables
aren't needed.

**Restricting traffic (production):** by default every Pod can talk to every Pod. **NetworkPolicies** restrict
that, e.g. "only order-service may connect to mysql-order". Add them once everything works.

## Step 17: Expose the required applications

**Only the api-gateway is exposed**, exactly as in Compose, where clients only used port 8080. Every other Service
is `ClusterIP` (internal).

| Option | File | URL |
|---|---|---|
| **A. NodePort** (always works) | `50-exposure/api-gateway-nodeport.yaml` | http://localhost:30080 |
| **B. Ingress** (host-based HTTP routing, step 18) | `50-exposure/ingress.yaml` | http://platform.localtest.me |
| Debugging a single component | `kubectl port-forward` | e.g. `kubectl port-forward svc/kafka-ui 8088:8080` → http://localhost:8088 |

```powershell
kubectl apply -f manifests\50-exposure\api-gateway-nodeport.yaml
curl.exe http://localhost:30080/actuator/health
```

Then open:

- http://localhost:30080/chat/: the chat UI
- http://localhost:30080/swagger-ui.html: all the APIs

**Other useful port-forwards:**

```powershell
kubectl port-forward svc/kafka-ui 8088:8080            # Kafka UI
kubectl port-forward svc/elasticsearch 9200:9200       # Elasticsearch REST
kubectl port-forward svc/mysql-order 3310:3306         # a DB client against order_db
```

## Step 18: NGINX Ingress Controller

An **Ingress** is only a routing *rule* (host/path → Service). An **Ingress Controller** is the proxy that
implements it. Kind has none built in.

**Which NGINX?** The community `ingress-nginx` controller was **retired** by the Kubernetes project (announced
November 2025). This setup uses **F5's NGINX Ingress Controller**, which is actively maintained and works with
standard Ingress resources. Its annotations start with `nginx.org/`. For new projects, also look at the **Gateway
API**, the Kubernetes successor to Ingress.

```powershell
helm install nginx-ingress oci://ghcr.io/nginx/charts/nginx-ingress `
  --namespace nginx-ingress --create-namespace -f ingress\nginx-ingress-values.yaml
kubectl -n nginx-ingress rollout status deployment --timeout=300s
kubectl get ingressclass                       # "nginx"
```

`ingress\nginx-ingress-values.yaml` makes the controller's Service a **NodePort** on 30100/30443, which Kind
maps to Windows ports 80/443. Kind has no cloud load balancer.

If the chart location changed, check docs.nginx.com → NGINX Ingress Controller → Installation with Helm; the
values file stays the same.

**The Ingress rule** (`50-exposure/ingress.yaml`): host `platform.localtest.me` → Service `api-gateway`, port
`http`. `*.localtest.me` always resolves to 127.0.0.1, so no `hosts` file edit is needed. Two annotations match
the platform:

- `nginx.org/proxy-read-timeout: "135s"`: chat and RAG answers can take up to 2 minutes.
- `nginx.org/client-max-body-size: "25m"`: document uploads up to 20 MB.

```powershell
kubectl apply -f manifests\50-exposure\ingress.yaml
kubectl get ingress                            # ADDRESS may stay empty on Kind - that's fine
curl.exe http://platform.localtest.me/actuator/health
```

**Verify:** `{"status":"UP"...}`. Also try http://platform.localtest.me/chat/.

---

# E. Operate

## Step 19: Test the complete application flow

```powershell
cd product-platform\kind-platform
.\scripts\test-platform.ps1                    # core flow
.\scripts\test-platform.ps1 -IncludeAi         # + RAG search + chatbot
.\scripts\test-platform.ps1 -Gateway http://platform.localtest.me    # through the Ingress
```

It runs the **same business flow as with Compose**, entirely through the gateway, with unique test data on every
run:

| # | Check | Proves |
|---|---|---|
| 1 | Gateway up | Exposure works |
| 2 | Create product | product-write-service → mysql-product |
| 3 | Product searchable | **CDC**: binlog → Debezium → Kafka → product-service → Elasticsearch |
| 4 | Product in order-service's catalogue | **Outbox** → `product.catalog.v1` → order-service |
| 5–6 | Add stock, check availability | stock-service → mysql-stock |
| 7 | Create user | user-service → mysql-user |
| 8 | Place order (202) | order-service + outbox → `order.events.v1` |
| 9 | Order **CONFIRMED** | **Saga**: stock reserved → `stock.events.v1` → order confirmed |
| 10 | Stock 10 → 8 | Saga committed the reservation |
| 11 | `X-RateLimit-Remaining` header | Gateway resilience + Redis |
| (AI) | RAG search, chatbot answer | pgvector, Ollama, MCP tools → gateway |

Steps 3, 4, 9, and 10 are **asynchronous**, so the script polls (up to 90 s) before failing. A `FAIL` names the
step, and step 21 shows how to find the cause.

**Manual tests:**

- The chat UI at http://localhost:30080/chat/: *"Is the MacBook Air available?"*, *"I want to buy it"*.
- Upload a document in the chat UI (**Documents**) and ask about it.
- Swagger UI: every API, with "Try it out".
- The `.http` files from the projects work with `@gw = http://localhost:30080`.

## Step 20: Verify MySQL, Kafka, and all services

**Everything at a glance:**

```powershell
kubectl get pods -o wide                     # all Running, READY 1/1; Jobs "Completed"
kubectl get statefulsets,deployments,jobs
kubectl get pvc                              # all Bound
```

**MySQL:**

```powershell
kubectl exec mysql-order-0 -- sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SHOW TABLES; SELECT id,status FROM orders ORDER BY created_at DESC LIMIT 3;"'
kubectl exec mysql-product-0 -- sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT sku,name FROM product LIMIT 5;"'
```

**Kafka:**

```powershell
kubectl exec kafka-0 -- kafka-topics --bootstrap-server localhost:9092 --list
kubectl exec kafka-0 -- kafka-consumer-groups --bootstrap-server localhost:9092 --list
kubectl exec kafka-0 -- kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group stock-service
```

The `LAG` column should be 0 when everything is processed. Kafka UI gives the same view: topics, messages,
consumers, schemas, and the connector.

**Schema Registry and CDC:**

```powershell
kubectl exec deploy/schema-registry -- curl -s localhost:8081/subjects
kubectl exec deploy/kafka-connect -- curl -s localhost:8083/connectors/product-db-cdc/status
```

**Elasticsearch:**

```powershell
kubectl exec elasticsearch-0 -- curl -s "localhost:9200/_cat/indices?v"
```

**Services (health through Kubernetes and through the gateway):**

```powershell
foreach ($s in "product-write-service:8089","product-service:8081","stock-service:8082","order-service:8084","user-service:8092") {
  $n,$p = $s.Split(":"); "{0,-24} {1}" -f $n, (kubectl exec deploy/$n -- wget -qO- localhost:$p/actuator/health/readiness)
}
curl.exe http://localhost:30080/actuator/resilience          # gateway circuit breakers + bulkheads
```

## Step 21: Logs and troubleshooting failed Pods

**Logs** (JSON lines from the services, plain text from the infrastructure):

```powershell
kubectl logs deploy/order-service --tail=50                 # one Deployment
kubectl logs deploy/order-service -f                        # follow
kubectl logs -l app.kubernetes.io/component=backend --prefix --tail=20   # several services at once
kubectl logs <pod> -c wait-for-dependencies                 # the init container
kubectl logs <pod> --previous                               # the CRASHED previous run
kubectl logs job/debezium-connector                         # a Job
```

**Follow one request across services** (the correlation id from the gateway):

```powershell
kubectl logs -l app.kubernetes.io/part-of=product-platform --prefix --tail=500 | Select-String "demo-trace-12345"
```

**The troubleshooting routine:**

```powershell
kubectl get pods                                  # 1. which Pod, which STATUS?
kubectl describe pod <pod>                        # 2. read "Events" at the bottom
kubectl logs <pod> [--previous] [-c <container>]  # 3. the app's own error
kubectl get events --sort-by=.lastTimestamp       # 4. cluster-wide events
kubectl exec -it <pod> -- sh                      # 5. look inside (env, files, wget to other services)
```

| STATUS / symptom | Likely cause | Fix |
|---|---|---|
| `ErrImagePull` / `ImagePullBackOff` | A local image (`*:1.0.0`, `product-platform/kafka-connect`) wasn't loaded | `kind load docker-image <image> --name product-platform` |
| `Init:0/1` for minutes | A dependency isn't up | `kubectl logs <pod> -c wait-for-dependencies` shows which one |
| `CrashLoopBackOff` (Kafka, Schema Registry) | `enableServiceLinks` removed, or a wrong listener | `kubectl logs kafka-0 --previous` |
| `CrashLoopBackOff` (Spring service) | DB credentials, Flyway, a config typo | `kubectl logs <pod> --previous`, look for the first `ERROR` |
| `Running` but `0/1` | Readiness failing (its DB?) | `kubectl exec <pod> -- wget -qO- localhost:<port>/actuator/health/readiness` |
| `OOMKilled` (in `describe`) | Memory limit too low, or Docker out of memory | Raise `limits.memory`; check the memory table; `-SkipAi` |
| `Pending` | Not enough resources, or the PVC is unbound | `describe pod` → Events; `kubectl get pvc` |
| `CreateContainerConfigError` | Secret/ConfigMap or key missing | `describe pod` names it |
| Job not completing | Its target isn't reachable | `kubectl logs job/<name>`; delete and re-apply the Job after fixing |
| Product never searchable | Connector not RUNNING | `kubectl exec deploy/kafka-connect -- curl -s localhost:8083/connectors/product-db-cdc/status` |
| Order stays `PENDING` | stock-service down, or its consumer stuck | consumer-groups `--describe --group stock-service`; stock-service logs |
| Gateway 503 `reason: CIRCUIT_OPEN` | The target service was failing | Fix the service; the circuit closes after 15 s (gateway RESILIENCE.md) |
| `elasticsearch-0` exits at start | Memory, or `vm.max_map_count` | Check memory; the single-node setting skips the bootstrap checks |
| Ingress returns 404 | Wrong host, or the controller isn't installed | Use `platform.localtest.me`; `kubectl get ingressclass` |

**Re-running a Job after a fix** (Jobs are immutable once created):

```powershell
kubectl delete job kafka-topics; kubectl apply -f manifests\20-kafka\kafka-topics-job.yaml
```

## Step 22: Scale the microservices

```powershell
kubectl scale deployment/order-service --replicas=3
kubectl get pods -l app.kubernetes.io/name=order-service -o wide        # spread over the workers
kubectl exec kafka-0 -- kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group order-service
```

Expect **3 consumers**, each owning one of the 3 partitions of `stock.events.v1`. The Service now spreads HTTP
requests over 3 Pods, without any change in the gateway.

**Why scaling is safe here, by design:**

- **Kafka consumers:** at most as many active consumers as partitions (3). A 4th Pod would idle.
- **Outbox relays** use `FOR UPDATE SKIP LOCKED`, so several Pods never publish the same event twice.
- **Consumers are idempotent** (the `processed_event` table), so redelivery during rebalancing is harmless.

**Limits:**

- **The chatbot and RAG** are limited by Ollama (1 instance, CPU), not by their own replicas.
- **Automatic scaling:** HorizontalPodAutoscaler (needs metrics-server; see the user-service guide, section 6).

Scale back with `kubectl scale deployment/order-service --replicas=1`. For permanent changes, edit `replicas:` in
the YAML and apply it.

## Step 23: Restart and update microservices

**Restart** (new Pods one by one, no downtime when replicas ≥ 2):

```powershell
kubectl rollout restart deployment/order-service
kubectl rollout status deployment/order-service
```

**Configuration change** (environment variables are read at startup):

```powershell
kubectl apply -f manifests\40-apps\order-service.yaml
kubectl rollout restart deployment/order-service
```

**New version of the code:**

```powershell
cd product-platform
.\kind-platform\scripts\build-images.ps1 -Services order-service -Tag 1.0.1 -SkipInfrastructureImages
kubectl set image deployment/order-service order-service=order-service:1.0.1
kubectl rollout status deployment/order-service
kubectl rollout history deployment/order-service
kubectl rollout undo deployment/order-service           # back to the previous version
```

Afterwards, set `image: order-service:1.0.1` in the YAML, so the file stays the truth.

**New `event-schemas` version:** run its registration Job (with the new version in the name) **before** updating
the producers.

## Step 24: Failure scenarios

Run `test-platform.ps1` first, so there's data. Each test shows **what Kubernetes does**, and what the
application design adds on top.

**24.1 A Pod dies** (self-healing):

```powershell
kubectl delete pod -l app.kubernetes.io/name=order-service
kubectl get pods -l app.kubernetes.io/name=order-service -w
```

A new Pod is created immediately and becomes ready. Kafka resumes from the committed offsets, so no event is lost.

**24.2 The process crashes inside the container** (a liveness-style restart):

```powershell
kubectl exec deploy/user-service -- kill 1
kubectl get pods -l app.kubernetes.io/name=user-service
```

`RESTARTS` goes up by 1. Same Pod, new container.

**24.3 A service is unavailable: the saga waits instead of failing:**

```powershell
kubectl scale deployment/stock-service --replicas=0
.\scripts\test-platform.ps1     # "Saga: order CONFIRMED" FAILS: the order stays PENDING
kubectl scale deployment/stock-service --replicas=1
```

About a minute later, check the order again (`GET /api/v1/orders/{id}`): it's **CONFIRMED**. The
`OrderCreated` event waited in Kafka. This is the main advantage of the event-driven saga over synchronous calls.

**24.4 The read side is down: gateway fallback routing:**

```powershell
kubectl scale deployment/product-service --replicas=0
curl.exe -s -D - -o NUL http://localhost:30080/api/v1/products/<product-id>   # 200 + X-Fallback-Source: product-write-service
curl.exe -s http://localhost:30080/api/v1/products?q=laptop                    # 503 fallback ProblemDetail
kubectl scale deployment/product-service --replicas=1
```

Products created meanwhile appear in search afterwards: the CDC events waited in Kafka.

**24.5 A database is down: readiness, not restarts:**

```powershell
kubectl scale statefulset/mysql-user --replicas=0
kubectl get pods -l app.kubernetes.io/name=user-service -w      # 0/1 READY, RESTARTS unchanged
curl.exe -s http://localhost:30080/api/v1/users                 # 503 from the gateway's fallback
kubectl scale statefulset/mysql-user --replicas=1               # back to 1/1; the data is still there (PVC)
```

**24.6 A whole node fails:**

```powershell
kubectl get pods -o wide                      # note which Pods run on product-platform-worker2
docker stop product-platform-worker2
kubectl get nodes -w                          # worker2 → NotReady after ~40 s
```

After ~5 minutes (Kubernetes' default grace period), the **Deployment** Pods from that node are recreated on the
other nodes. **StatefulSet Pods with local volumes stay down**: their data is on the stopped node. That's the
reason production uses network storage, or databases replicated by operators.

Bring the node back with `docker start product-platform-worker2`.

**24.7 Kafka is down: the outbox absorbs it:**

```powershell
kubectl scale statefulset/kafka --replicas=0
# Place an order (Swagger or .http): 202 PENDING - order-service saved it together with its outbox event
kubectl scale statefulset/kafka --replicas=1
```

When Kafka is back, the outbox relay publishes the event and the order gets CONFIRMED.

## Step 25: Compare with the Docker Compose environment

Run the **same** tests in both environments:

| Check | Compose | Kind | Must match |
|---|---|---|---|
| End-to-end business flow | `.http` files on 8080 | `test-platform.ps1` (or the same `.http` files on 30080) | Every step passes |
| Topics | `kafka-topics --list` in the container | Step 20 | The same 9 topics (+ Kafka's and Connect's internal ones) |
| Schemas | Schema Registry on 18081 | Step 20 | The same subjects |
| CDC | New product → searchable | Check 3 | Same behaviour |
| Saga | Order → CONFIRMED, stock reduced | Checks 9–10 | Same result |
| Gateway resilience | `resilience-tests.ps1` on 8080 | `resilience-tests.ps1 -Gateway http://localhost:30080` | Rate limits, bulkheads, fallbacks |
| Chat + RAG | Chat UI on 8080 | Chat UI on 30080 | Same answers (same model) |

**What is *better* in Kubernetes**, and how you saw it:

| Behaviour | Compose | Kubernetes |
|---|---|---|
| A crashed service | `restart: unless-stopped` | Recreated and checked by probes (24.1, 24.2) |
| A dependency is down | Containers start anyway | Init containers wait; readiness takes Pods out of traffic (24.5) |
| Updates | Stop, then start | Rolling update + rollback (23) |
| Scaling | `--scale` without load balancing | Replicas behind a Service (22) |
| A node fails | Everything stops | Stateless Pods move to other nodes (24.6) |

**What Compose still does better:** a fast local development loop. Keep using it (with your IDE) to write code,
and use Kind to learn and test how the platform runs in production. Both run from the same images and the same
`application.yml`, with different environment variables.

---

## Clean up

```powershell
kubectl delete namespace product-platform        # everything in the platform, INCLUDING the data (PVCs)
helm uninstall nginx-ingress -n nginx-ingress    # if installed
kind delete cluster --name product-platform      # the whole cluster
```

## From here to production

| Here (learning) | Production |
|---|---|
| Secrets in YAML files | External Secrets / Sealed Secrets / a cloud secret manager |
| Single Kafka broker, StatefulSet | **Strimzi** operator (3+ brokers, upgrades, users, topics as resources) |
| MySQL / PostgreSQL StatefulSets | Managed databases (RDS ...) or operators (CloudNativePG, MySQL Operator) |
| Elasticsearch StatefulSet | ECK operator, or a managed service |
| `kubectl apply` of folders | Kustomize overlays / Helm charts + GitOps (Argo CD, Flux) |
| 1 replica | ≥ 2 + PDBs + HorizontalPodAutoscalers |
| Open Pod network | NetworkPolicies |
| `kind load` | An image registry + CI pipeline |
| NodePort / NGINX Ingress | Gateway API + TLS (cert-manager) |
| `kubectl logs` | Loki + Grafana (the JSON logs are ready) |
