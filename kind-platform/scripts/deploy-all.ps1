<#
  Deploys the complete platform in dependency order and WAITS after every layer.
  Run from the kind-platform folder:

    .\scripts\deploy-all.ps1            # everything
    .\scripts\deploy-all.ps1 -SkipAi    # without Ollama/RAG/chatbot (needs ~8 GB less memory)

  Every step is also explained, with manual commands, in GUIDE.md.
#>
param([switch]$SkipAi)
$ErrorActionPreference = "Stop"
$ns = "product-platform"

function Step([string]$Title) { Write-Host "`n=== $Title ===" -ForegroundColor Cyan }
function Apply([string]$Path) {
    kubectl apply -f $Path
    if ($LASTEXITCODE -ne 0) { throw "kubectl apply -f $Path failed" }
}
function WaitFor([string]$Resource, [string]$Timeout = "300s") {
    Write-Host "  waiting for $Resource ..."
    kubectl -n $ns rollout status $Resource --timeout=$Timeout
    if ($LASTEXITCODE -ne 0) { throw "$Resource did not become ready - see GUIDE.md step 21" }
}
function WaitJob([string]$Job, [string]$Timeout = "600s") {
    Write-Host "  waiting for job/$Job to complete ..."
    kubectl -n $ns wait --for=condition=complete "job/$Job" --timeout=$Timeout
    if ($LASTEXITCODE -ne 0) { throw "job/$Job did not complete - kubectl -n $ns logs job/$Job" }
}

Step "1. Namespace"
Apply "manifests/00-namespace.yaml"

Step "2. Databases (MySQL x4)"
foreach ($db in "mysql-product", "mysql-stock", "mysql-order", "mysql-user") { Apply "manifests/10-databases/$db.yaml" }
foreach ($db in "mysql-product", "mysql-stock", "mysql-order", "mysql-user") { WaitFor "statefulset/$db" }

Step "3. Kafka, Schema Registry, topics"
Apply "manifests/20-kafka/kafka.yaml";            WaitFor "statefulset/kafka"
Apply "manifests/20-kafka/schema-registry.yaml";  WaitFor "deployment/schema-registry"
Apply "manifests/20-kafka/kafka-topics-job.yaml"; WaitJob "kafka-topics"
Apply "manifests/20-kafka/kafka-ui.yaml"

Step "4. event-schemas: register the Avro schemas"
Apply "manifests/25-event-schemas/schema-registration-job.yaml"
WaitJob "event-schemas-registration-1-0-0"

Step "5. Elasticsearch, Redis"
Apply "manifests/30-platform/elasticsearch.yaml"
Apply "manifests/30-platform/redis.yaml"
WaitFor "statefulset/elasticsearch" "600s"
WaitFor "deployment/redis"

if (-not $SkipAi) {
    Step "6. AI: PostgreSQL+pgvector, Ollama + models, reranker"
    Apply "manifests/10-databases/postgres-rag.yaml"
    Apply "manifests/35-ai/ollama.yaml"
    Apply "manifests/35-ai/reranker.yaml"           # downloads its model in the background
    WaitFor "statefulset/postgres-rag"
    WaitFor "statefulset/ollama" "600s"
    Apply "manifests/35-ai/ollama-models-job.yaml"
    WaitJob "ollama-models" "3600s"                 # first time: several GB
}

Step "7. Business services"
foreach ($app in "product-write-service", "user-service", "stock-service", "order-service", "product-service") {
    Apply "manifests/40-apps/$app.yaml"
}
foreach ($app in "product-write-service", "user-service", "stock-service", "order-service", "product-service") {
    WaitFor "deployment/$app" "600s"
}

if (-not $SkipAi) {
    Step "8. AI services"
    Apply "manifests/40-apps/rag-service.yaml"; WaitFor "deployment/rag-service" "600s"
    Apply "manifests/40-apps/mcp-server.yaml";  WaitFor "deployment/mcp-server" "600s"
    Apply "manifests/40-apps/mcp-client.yaml";  WaitFor "deployment/mcp-client" "600s"
}

Step "9. API Gateway"
Apply "manifests/40-apps/api-gateway.yaml"; WaitFor "deployment/api-gateway" "600s"

Step "10. CDC: Kafka Connect + Debezium connector"
Apply "manifests/45-cdc/kafka-connect.yaml";          WaitFor "deployment/kafka-connect" "600s"
Apply "manifests/45-cdc/debezium-connector-job.yaml"; WaitJob "debezium-connector"

Step "11. Exposure"
Apply "manifests/50-exposure/api-gateway-nodeport.yaml"

Write-Host "`nPlatform deployed." -ForegroundColor Green
kubectl -n $ns get pods -o wide
Write-Host "`nGateway: http://localhost:30080   Next: .\scripts\test-platform.ps1"
