<#
  Builds ALL images of the platform and loads them into the Kind cluster.
  Run from the product-platform folder (the folder that contains all projects):

    .\kind-platform\scripts\build-images.ps1                         # everything
    .\kind-platform\scripts\build-images.ps1 -Services order-service # just one service
    .\kind-platform\scripts\build-images.ps1 -Tag 1.0.1              # a new version
#>
param(
    [string[]]$Services = @("product-write-service", "product-service", "stock-service", "order-service",
                            "user-service", "rag-service", "mcp-server", "mcp-client", "api-gateway"),
    [string]$Tag = "1.0.0",
    [string]$Cluster = "product-platform",
    [switch]$SkipInfrastructureImages,
    [switch]$SkipLoad
)
$ErrorActionPreference = "Stop"

# Container port of each service (only used for EXPOSE = documentation)
$ports = @{ "product-write-service" = 8089; "product-service" = 8081; "stock-service" = 8082; "order-service" = 8084;
            "user-service" = 8092; "rag-service" = 8093; "mcp-server" = 8090; "mcp-client" = 8091; "api-gateway" = 8080 }

if (-not (Test-Path ".\event-schemas\pom.xml")) {
    throw "Run this script from the product-platform folder (event-schemas\pom.xml not found here)."
}

function Invoke-Checked([string]$What, [scriptblock]$Command) {
    Write-Host "`n=== $What ===" -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "FAILED: $What" }
}

$images = @()
foreach ($service in $Services) {
    if (-not $ports.ContainsKey($service)) { throw "Unknown service '$service'" }
    $image = "${service}:$Tag"
    Invoke-Checked "docker build $image" {
        docker build -f kind-platform/docker/Dockerfile.service `
            --build-arg SERVICE=$service --build-arg PORT=$($ports[$service]) -t $image .
    }
    $images += $image
}

if (-not $SkipInfrastructureImages) {
    $connect = "product-platform/kafka-connect:8.0.0-debezium-3.2.0.Final"
    Invoke-Checked "docker build $connect (Kafka Connect + Debezium)" {
        docker build -t $connect infrastructure/kafka-connect
    }
    $registration = "event-schemas-registration:1.0.0"
    Invoke-Checked "docker build $registration" {
        docker build -f kind-platform/docker/Dockerfile.event-schemas-registration -t $registration event-schemas
    }
    $images += @($connect, $registration)
}

if (-not $SkipLoad) {
    foreach ($image in $images) {
        Invoke-Checked "kind load $image" { kind load docker-image $image --name $Cluster }
    }
}
Write-Host "`nDone. Images:" -ForegroundColor Green
$images | ForEach-Object { Write-Host "  $_" }
