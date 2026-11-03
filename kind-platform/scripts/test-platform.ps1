<#
  End-to-end test of the platform THROUGH THE API GATEWAY - the same business flow
  you tested with docker compose:

    product created (write side) -> CDC -> searchable (Elasticsearch)
                                 -> outbox -> order-service catalogue
    stock added -> user created -> order placed -> saga (Kafka) -> CONFIRMED -> stock reduced

    .\scripts\test-platform.ps1                       # via NodePort http://localhost:30080
    .\scripts\test-platform.ps1 -IncludeAi            # + RAG upload/search + chatbot
    .\scripts\test-platform.ps1 -Gateway http://platform.localtest.me   # via the Ingress
#>
param([string]$Gateway = "http://localhost:30080", [switch]$IncludeAi)
$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]

function Check([string]$Name, [scriptblock]$Test) {
    try {
        $detail = & $Test
        $results.Add([pscustomobject]@{ Check = $Name; Result = "PASS"; Detail = "$detail" })
        Write-Host ("PASS  {0}  {1}" -f $Name, $detail) -ForegroundColor Green
    } catch {
        $results.Add([pscustomobject]@{ Check = $Name; Result = "FAIL"; Detail = $_.Exception.Message })
        Write-Host ("FAIL  {0}  {1}" -f $Name, $_.Exception.Message) -ForegroundColor Red
    }
}
# Repeats $Probe until it returns $true (eventual consistency: CDC, outbox, saga are asynchronous)
function WaitUntil([string]$What, [int]$Seconds, [scriptblock]$Probe) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try { if (& $Probe) { return } } catch { }
        Start-Sleep -Seconds 2
    }
    throw "timeout after $Seconds s waiting for: $What"
}
function Api([string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}) {
    $params = @{ Method = $Method; Uri = "$Gateway$Path"; Headers = $Headers; TimeoutSec = 130 }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 5); $params.ContentType = "application/json" }
    return Invoke-RestMethod @params
}

$run = Get-Date -Format "MMddHHmmss"           # unique test data on every run
$sku = "K8S-TEST-$run"
$name = "Kind Test Laptop $run"
Write-Host "Testing $Gateway with SKU $sku`n"

Check "Gateway is up" { (Api GET "/actuator/health").status }

Check "Create product (product-write-service, MySQL)" {
    $script:product = Api POST "/api/v1/products" @{ sku = $sku; name = $name; description = "Created by test-platform.ps1";
        brand = "Acme"; category = "laptop"; price = 999.99; ramGb = 16; rating = 4.5; tags = @("kind", "test") }
    "id=$($product.id)"
}

Check "CDC: product searchable (Debezium -> Kafka -> product-service -> Elasticsearch)" {
    WaitUntil "product in search index" 90 { (Api GET "/api/v1/products?q=$run").totalElements -ge 1 }
    "found by search"
}

Check "Outbox: product in order-service catalogue (product.catalog.v1)" {
    WaitUntil "catalogue entry" 60 { (Api GET "/api/v1/orders/catalog/$sku").sku -eq $sku }
    "catalogue has $sku"
}

Check "Add stock (stock-service)" {
    $s = Api POST "/api/v1/stock" @{ sku = $sku; quantityOnHand = 10; reorderLevel = 2 }
    "onHand=$($s.quantityOnHand)"
}

Check "Availability through the gateway" {
    $a = Api GET "/api/v1/stock/$sku/availability"
    if (-not $a.available) { throw "not available: $($a | ConvertTo-Json -Compress)" }
    "available=$($a.availableQuantity)"
}

Check "Create user (user-service)" {
    $script:user = Api POST "/api/v1/users" @{ email = "kind.test.$run@example.com"; firstName = "Kind"; lastName = "Tester" }
    "id=$($user.id)"
}

Check "Place order (order-service, 202 + saga)" {
    $script:order = Api POST "/api/v1/orders" @{ userId = $user.id; items = @(@{ sku = $sku; quantity = 2 }) } `
        @{ "Idempotency-Key" = [guid]::NewGuid().ToString(); "X-User-Id" = $user.id }
    "id=$($order.id) status=$($order.status)"
}

Check "Saga: order CONFIRMED (order.events.v1 <-> stock.events.v1)" {
    WaitUntil "order leaves PENDING" 60 { (Api GET "/api/v1/orders/$($order.id)").status -ne "PENDING" }
    $final = Api GET "/api/v1/orders/$($order.id)"
    if ($final.status -ne "CONFIRMED") { throw "status=$($final.status) reason=$($final.failureReason)" }
    "CONFIRMED total=$($final.totalAmount)"
}

Check "Stock reduced by the saga (10 -> 8)" {
    WaitUntil "stock committed" 30 { (Api GET "/api/v1/stock/$sku").quantityOnHand -eq 8 }
    "onHand=8"
}

Check "Rate-limit headers present (gateway resilience)" {
    $r = Invoke-WebRequest -Uri "$Gateway/api/v1/stock/$sku/availability" -UseBasicParsing
    $remaining = $r.Headers["X-RateLimit-Remaining"]
    if (-not $remaining) { throw "no X-RateLimit-Remaining header (is Redis running?)" }
    "X-RateLimit-Remaining=$remaining"
}

if ($IncludeAi) {
    Check "RAG search (rag-service, pgvector)" {
        $s = Api POST "/api/v1/rag/search" @{ query = "return policy"; topK = 3 }
        "chunks=$(@($s.chunks).Count) reranked=$($s.diagnostics.reranked)"
    }
    Check "Chatbot answers through MCP tools (mcp-client -> Ollama -> mcp-server -> gateway)" {
        $c = Api POST "/api/v1/chat" @{ message = "Is the product with SKU $sku in stock?" }
        if (-not $c.answer) { throw "empty answer" }
        $c.answer.Substring(0, [Math]::Min(80, $c.answer.Length))
    }
}

$failed = @($results | Where-Object Result -eq "FAIL").Count
Write-Host ("`n{0} checks, {1} failed" -f $results.Count, $failed) -ForegroundColor $(if ($failed) { "Red" } else { "Green" })
if ($failed) { exit 1 }
