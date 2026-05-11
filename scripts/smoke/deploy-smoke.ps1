param(
    [string] $AppBaseUrl = $env:APP_BASE_URL,
    [string] $ApiBaseUrl = $env:API_BASE_URL,
    [string] $Origin = $env:SMOKE_ORIGIN,
    [int] $TimeoutSeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-BaseUrl {
    param(
        [string] $Value,
        [string] $Fallback,
        [string] $Name
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        $Value = $Fallback
    }

    $normalized = $Value.Trim().TrimEnd("/")
    if (-not [Uri]::IsWellFormedUriString($normalized, [UriKind]::Absolute)) {
        throw "$Name must be an absolute http(s) URL. Value: $normalized"
    }

    $uri = [Uri] $normalized
    if ($uri.Scheme -notin @("http", "https")) {
        throw "$Name must use http or https. Value: $normalized"
    }

    return $normalized
}

function Get-HeaderValue {
    param(
        [Parameter(Mandatory = $true)] $Response,
        [Parameter(Mandatory = $true)] [string] $Name
    )

    $value = $Response.Headers[$Name]
    if ($null -eq $value) {
        return ""
    }

    if ($value -is [array]) {
        return ($value -join ",")
    }

    return [string] $value
}

function Get-ResponseContentText {
    param([Parameter(Mandatory = $true)] $Response)

    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }

    return [string] $Response.Content
}

function Invoke-SmokeRequest {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [string] $Uri,
        [string] $Method = "GET",
        [hashtable] $Headers = @{}
    )

    try {
        return Invoke-WebRequest `
            -Uri $Uri `
            -Method $Method `
            -Headers $Headers `
            -TimeoutSec $TimeoutSeconds `
            -UseBasicParsing
    } catch {
        throw "$Name request failed: $($_.Exception.Message)"
    }
}

function Assert-StatusRange {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] $Response,
        [int] $Min = 200,
        [int] $MaxExclusive = 400
    )

    $statusCode = [int] $Response.StatusCode
    if ($statusCode -lt $Min -or $statusCode -ge $MaxExclusive) {
        throw "$Name returned unexpected status $statusCode"
    }
}

function Write-Pass {
    param([Parameter(Mandatory = $true)] [string] $Message)
    Write-Host "[PASS] $Message"
}

function Write-Fail {
    param([Parameter(Mandatory = $true)] [string] $Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    $script:failures.Add($Message) | Out-Null
}

$appBase = Normalize-BaseUrl -Value $AppBaseUrl -Fallback "http://localhost:3000" -Name "APP_BASE_URL"
$apiBase = Normalize-BaseUrl -Value $ApiBaseUrl -Fallback "http://localhost:8080" -Name "API_BASE_URL"

if ([string]::IsNullOrWhiteSpace($Origin)) {
    $Origin = $appBase
}
$originBase = Normalize-BaseUrl -Value $Origin -Fallback $appBase -Name "SMOKE_ORIGIN"

$script:failures = [System.Collections.Generic.List[string]]::new()

Write-Host "Deploy smoke target"
Write-Host "APP_BASE_URL=$appBase"
Write-Host "API_BASE_URL=$apiBase"
Write-Host "SMOKE_ORIGIN=$originBase"
Write-Host ""

try {
    $frontendResponse = Invoke-SmokeRequest -Name "frontend root" -Uri $appBase
    Assert-StatusRange -Name "frontend root" -Response $frontendResponse
    Write-Pass "frontend root responded with HTTP $([int] $frontendResponse.StatusCode)"
} catch {
    Write-Fail $_.Exception.Message
}

try {
    $healthResponse = Invoke-SmokeRequest -Name "backend health" -Uri "$apiBase/actuator/health"
    Assert-StatusRange -Name "backend health" -Response $healthResponse -MaxExclusive 300

    $health = Get-ResponseContentText -Response $healthResponse | ConvertFrom-Json
    if (-not ($health.PSObject.Properties.Name -contains "status")) {
        throw "backend health response does not include status"
    }
    if ($health.status -ne "UP") {
        throw "backend health status is $($health.status)"
    }

    Write-Pass "backend health is UP"
} catch {
    Write-Fail $_.Exception.Message
}

try {
    $infoResponse = Invoke-SmokeRequest -Name "backend info" -Uri "$apiBase/actuator/info"
    Assert-StatusRange -Name "backend info" -Response $infoResponse -MaxExclusive 300
    Write-Pass "backend info endpoint responded with HTTP $([int] $infoResponse.StatusCode)"
} catch {
    Write-Fail $_.Exception.Message
}

try {
    $corsHeaders = @{
        Origin = $originBase
        "Access-Control-Request-Method" = "GET"
        "Access-Control-Request-Headers" = "Content-Type"
    }
    $corsResponse = Invoke-SmokeRequest `
        -Name "CORS preflight" `
        -Uri "$apiBase/actuator/health" `
        -Method "OPTIONS" `
        -Headers $corsHeaders

    Assert-StatusRange -Name "CORS preflight" -Response $corsResponse -MaxExclusive 300

    $allowOrigin = Get-HeaderValue -Response $corsResponse -Name "Access-Control-Allow-Origin"
    $allowCredentials = Get-HeaderValue -Response $corsResponse -Name "Access-Control-Allow-Credentials"

    if ($allowOrigin -ne $originBase -and $allowOrigin -ne "*") {
        throw "CORS preflight returned Access-Control-Allow-Origin='$allowOrigin'"
    }
    if ($allowCredentials.ToLowerInvariant() -ne "true") {
        throw "CORS preflight did not allow credentials"
    }

    Write-Pass "CORS preflight allowed $originBase"
} catch {
    Write-Fail $_.Exception.Message
}

if ($script:failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Deploy smoke failed with $($script:failures.Count) failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Deploy smoke passed."
