<#
  API Gateway resilience tests (Windows PowerShell 5.1 or PowerShell 7).
  Uses curl.exe (built into Windows 10/11) and .NET HttpClient for truly concurrent requests.

  Usage (from the api-gateway folder):
    .\scripts\resilience-tests.ps1 -Test RateLimit
    .\scripts\resilience-tests.ps1 -Test Bulkhead
    .\scripts\resilience-tests.ps1 -Test CircuitBreaker      # stop stock-service first
    .\scripts\resilience-tests.ps1 -Test FallbackRouting     # stop product-service first
    .\scripts\resilience-tests.ps1 -Test Timeout             # gateway started with a tiny timeout
    .\scripts\resilience-tests.ps1 -Test State               # circuit breakers + bulkheads now
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("RateLimit", "Bulkhead", "CircuitBreaker", "FallbackRouting", "Timeout", "State")]
    [string]$Test,
    [string]$Gateway = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Show-State {
    Write-Host "`n--- /actuator/resilience ---" -ForegroundColor Cyan
    $state = curl.exe -s "$Gateway/actuator/resilience" | ConvertFrom-Json
    Write-Host ("{0,-16} {1,-10} {2,8} {3,9} {4,7} {5,13}" -f "circuit breaker", "state", "fail %", "buffered", "failed", "not permitted")
    foreach ($cb in $state.circuitBreakers) {
        Write-Host ("{0,-16} {1,-10} {2,8} {3,9} {4,7} {5,13}" -f $cb.name, $cb.state, $cb.failureRatePercent,
                    $cb.bufferedCalls, $cb.failedCalls, $cb.notPermittedCalls)
    }
    Write-Host ""
    Write-Host ("{0,-24} {1,6} {2,10} {3,9}" -f "bulkhead", "max", "in flight", "rejected")
    foreach ($b in $state.bulkheads) {
        Write-Host ("{0,-24} {1,6} {2,10} {3,9}" -f $b.name, $b.maxConcurrentCalls, $b.callsInFlight, $b.rejectedCalls)
    }
}

# Sends one request; returns status code, reason (from a fallback ProblemDetail) and duration.
function Invoke-Once([string]$Url) {
    $tmp = New-TemporaryFile
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $code = curl.exe -s -o $tmp.FullName -w "%{http_code}" $Url
    $watch.Stop()
    $body = Get-Content $tmp.FullName -Raw
    Remove-Item $tmp.FullName
    $reason = ""
    if ($code -eq "503" -and $body) {
        try { $reason = ($body | ConvertFrom-Json).reason } catch { }
    }
    [pscustomobject]@{ Status = $code; Reason = $reason; Ms = $watch.ElapsedMilliseconds }
}

# Sends $Count requests AT THE SAME TIME (truly concurrent, via .NET HttpClient) and returns
# their status codes. -DistinctUsers gives every request its own X-User-Id, so each one has its
# own rate-limit bucket: then only the BULKHEAD can reject them.
function Send-Concurrent([int]$Count, [string]$Method, [string]$Url, [string]$JsonBody, [switch]$DistinctUsers) {
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $tasks = New-Object System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]
    for ($i = 1; $i -le $Count; $i++) {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
        if ($DistinctUsers) { $request.Headers.Add("X-User-Id", [guid]::NewGuid().ToString()) }
        if ($JsonBody) {
            $request.Content = [System.Net.Http.StringContent]::new($JsonBody, [System.Text.Encoding]::UTF8, "application/json")
        }
        $tasks.Add($client.SendAsync($request))
    }
    try {
        [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
    } catch {
        Write-Host "Some requests failed to connect: $($_.Exception.InnerException.Message)"
    }
    $codes = foreach ($t in $tasks) { if ($t.Status -eq "RanToCompletion") { [int]$t.Result.StatusCode } else { "error" } }
    $client.Dispose()
    return $codes
}

# Prints "status: count" lines for a list of HTTP status codes.
function Show-StatusCounts($Codes) {
    $Codes | Where-Object { $_ } | Group-Object | Sort-Object Name | ForEach-Object {
        Write-Host ("  HTTP {0}: {1} request(s)" -f $_.Name, $_.Count)
    }
}

# Prints the response lines matching a pattern (status line, X-RateLimit-*, X-Fallback-Source ...).
function Show-Headers([string]$Url, [string]$Pattern) {
    curl.exe -s -D - -o NUL $Url | Where-Object { $_ -match $Pattern } | ForEach-Object { Write-Host ("  " + $_.Trim()) }
}

switch ($Test) {

    "RateLimit" {
        # Stock route: 30 tokens/s, bucket of 60. 100 requests at once -> ~60 pass, the rest 429.
        $url = "$Gateway/api/v1/stock/LAP-APL-001/availability"
        Write-Host "Headers of a single request (watch X-RateLimit-*):" -ForegroundColor Cyan
        Show-Headers $url "HTTP/|X-RateLimit"

        Write-Host "`nSending 100 requests at the same time from the SAME client..." -ForegroundColor Cyan
        $codes = Send-Concurrent -Count 100 -Method "GET" -Url $url
        Show-StatusCounts $codes
        Write-Host "Expected: about 60 x 200 (the burst) and the rest 429 Too Many Requests."
        Write-Host "Wait 2 s and run again: the bucket refills at 30 tokens per second."
    }

    "Bulkhead" {
        # RAG route: max 10 concurrent calls. 30 parallel searches, each from a DIFFERENT user,
        # so the per-user rate limit doesn't reject them first - only the bulkhead can.
        $url = "$Gateway/api/v1/rag/search"
        $json = '{"query":"return policy for opened headphones","topK":3}'
        Write-Host "Sending 30 RAG searches at the same time, each from a different user" -ForegroundColor Cyan
        Write-Host "(bulkhead 'rag-service' allows 10 at a time)..." -ForegroundColor Cyan
        $codes = Send-Concurrent -Count 30 -Method "POST" -Url $url -JsonBody $json -DistinctUsers
        Show-StatusCounts $codes
        Write-Host "Expected: about 10 x 200 and the rest 503 'Service busy' (with Retry-After: 2)."
        Show-State
    }

    "CircuitBreaker" {
        # Run AFTER stopping stock-service. 25 calls one after another.
        $url = "$Gateway/api/v1/stock/LAP-APL-001/availability"
        Write-Host "25 sequential requests while stock-service is DOWN:" -ForegroundColor Cyan
        1..25 | ForEach-Object {
            $r = Invoke-Once $url
            Write-Host ("{0,3}  HTTP {1}  {2,-14} {3,6} ms" -f $_, $r.Status, $r.Reason, $r.Ms)
        }
        Write-Host "`nExpected: the first ~10 are BACKEND_ERROR (the backend was tried), then CIRCUIT_OPEN"
        Write-Host "in a few ms - the gateway stops calling the dead service. Start stock-service again,"
        Write-Host "wait 15 s (HALF_OPEN), send a few requests: the state returns to CLOSED."
        Show-State
    }

    "FallbackRouting" {
        # Run AFTER stopping product-service (read side). product-write-service must be running.
        Write-Host "Looking up one product id from the write side (port 8089)..." -ForegroundColor Cyan
        $sql = "SELECT id FROM product LIMIT 1;"
        $id = (docker exec mysql-product mysql -uproduct_user -pproduct_dev_password product_db -N -e $sql 2>$null)
        if (-not $id) { Write-Host "Could not read a product id from mysql-product."; break }
        Write-Host "Product id: $id"

        Write-Host "`n1) GET /api/v1/products/{id} - fallback ROUTING to product-write-service:" -ForegroundColor Cyan
        Show-Headers "$Gateway/api/v1/products/$id" "HTTP/|X-Fallback-Source"
        Write-Host "Expected: HTTP 200 and X-Fallback-Source: product-write-service"

        Write-Host "`n2) Search - no alternative source, clear 503:" -ForegroundColor Cyan
        Write-Host ("  " + (curl.exe -s "$Gateway/api/v1/products?q=laptop"))
        Write-Host "Expected: 503 ProblemDetail, service product-read, reason BACKEND_ERROR or CIRCUIT_OPEN"
    }

    "Timeout" {
        # Gateway must be started with:  --app.resilience.circuit-breakers.product-read.timeout=50ms
        $url = "$Gateway/api/v1/products?q=laptop"
        Write-Host "5 searches with a 50 ms product-read timeout:" -ForegroundColor Cyan
        1..5 | ForEach-Object {
            $r = Invoke-Once $url
            Write-Host ("{0,3}  HTTP {1}  {2,-14} {3,6} ms" -f $_, $r.Status, $r.Reason, $r.Ms)
        }
        Write-Host "Expected: 503 with reason TIMEOUT after ~50 ms (a real search takes longer)."
    }

    "State" {
        Show-State
    }
}
