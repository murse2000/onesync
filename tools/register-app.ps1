<#
.SYNOPSIS
  Azure 앱 등록을 자동화한다.

.DESCRIPTION
  1. 앱 등록 생성 (또는 같은 이름의 기존 앱 재사용)
  2. Android용 public client redirect URI 등록
  3. Microsoft Graph 위임 권한(User.Read, Files.ReadWrite.All) 추가

  Azure CLI(az)가 필요하다. 개인 Microsoft 계정은 첫 로그인 때 사용자 동의로
  권한이 부여되므로 관리자 동의(admin consent)는 필요 없다.

  콘솔 출력은 인코딩 문제를 피하려고 영어로 쓴다.
#>
param(
    [string]$AppName = "OneSync Lab",
    [string]$RedirectUri = "com.dalbear.onesync://auth"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[!] Azure CLI (az) is not installed." -ForegroundColor Yellow
    Write-Host "    winget install -e --id Microsoft.AzureCLI"
    Write-Host "    Then open a new terminal and run this script again."
    Write-Host ""
    Write-Host "Manual values:"
    Write-Host "    Redirect URI : $RedirectUri"
    Write-Host "    Permissions  : User.Read, Files.ReadWrite.All (Delegated)"
    exit 1
}

az account show -o none 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Signing in to Azure..." -ForegroundColor Cyan
    az login | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] az login failed." -ForegroundColor Red
        exit 1
    }
}

# 같은 이름의 앱이 있으면 재사용해서 재실행해도 앱이 쌓이지 않게 한다.
$existingJson = az ad app list --display-name $AppName -o json
$existing = @()
if ($LASTEXITCODE -eq 0 -and $existingJson) {
    $existing = $existingJson | ConvertFrom-Json
}

if ($existing.Count -gt 0) {
    $appId = $existing[0].appId
    Write-Host "Reusing existing app: $AppName ($appId)" -ForegroundColor Cyan
} else {
    $createdJson = az ad app create `
        --display-name $AppName `
        --sign-in-audience AzureADandPersonalMicrosoftAccount `
        -o json
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] Failed to create the app. Register it manually in the Azure Portal." -ForegroundColor Red
        exit 1
    }
    $appId = ($createdJson | ConvertFrom-Json).appId
    Write-Host "Created app: $AppName ($appId)" -ForegroundColor Green
}

# 2단계: Android(mobile/desktop) public client redirect URI
az ad app update --id $appId --public-client-redirect-uris $RedirectUri 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Could not set the redirect URI automatically." -ForegroundColor Yellow
    Write-Host "    Azure Portal > Authentication > Add platform > Android, then add:"
    Write-Host "    $RedirectUri"
} else {
    Write-Host "Redirect URI: $RedirectUri" -ForegroundColor Green
}

# 3단계: Microsoft Graph 위임 권한
$graphApiId = "00000003-0000-0000-c000-000000000000"
$userRead = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
$filesReadWriteAll = "863451e7-0667-486c-a5d6-d5e4809c2d3a"

# Graph 서비스 주체가 없는 테넌트에서도 권한을 추가할 수 있게 미리 만들어 둔다.
az ad sp create --id $graphApiId -o none 2>$null

az ad app permission add `
    --id $appId `
    --api $graphApiId `
    --api-permissions "$userRead=Scope" "$filesReadWriteAll=Scope" `
    2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Could not add permissions automatically." -ForegroundColor Yellow
    Write-Host "    Azure Portal > API permissions > Add a permission > Microsoft Graph"
    Write-Host "    > Delegated permissions > User.Read, Files.ReadWrite.All"
} else {
    Write-Host "Permissions: User.Read, Files.ReadWrite.All" -ForegroundColor Green
}

Write-Host ""
Write-Host "CLIENT_ID=$appId" -ForegroundColor Green
try {
    $appId | Set-Clipboard
    Write-Host "(Copied to clipboard. Paste it into the app's client ID field.)"
} catch {
    Write-Host "(Clipboard copy failed. Copy the value above manually.)"
}
