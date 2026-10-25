[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'resolve-upstream-release.ps1'

function Test-Decision {
    param(
        [string]$Name,
        [string]$Version,
        [bool]$Changed,
        [bool]$Force,
        [object[]]$Releases,
        [string]$Revision = '',
        [bool]$ExpectedRequired,
        [string]$ExpectedTag,
        [string]$ExpectedReason = ''
    )

    $releaseList = ConvertTo-Json -InputObject @($Releases) -Compress -Depth 4
    $rawResult = & $scriptPath -TelegramVersion $Version -UpstreamChanged $Changed `
        -ForceRelease $Force -ReleaseListJson $releaseList -ReleaseRef 'regression-ref' `
        -RequestedRevision $Revision
    $result = $rawResult | ConvertFrom-Json
    if ([bool]$result.releaseRequired -ne $ExpectedRequired -or $result.tag -ne $ExpectedTag) {
        throw "$Name failed: $(ConvertTo-Json $result -Compress)"
    }
    if ($ExpectedReason -ne '' -and $result.reason -ne $ExpectedReason) {
        throw "$Name: unexpected reason $($result.reason)"
    }
    if ($result.releaseRef -ne 'regression-ref') {
        throw "$Name: release ref was not preserved."
    }
    Write-Host "PASS: $Name"
}

$stableOld = @{ tagName = 'v12.10.6-wsp.2'; isDraft = $false; isPrerelease = $false }
$stableOldFirst = @{ tagName = 'v12.10.6-wsp.1'; isDraft = $false; isPrerelease = $false }
$stableNew = @{ tagName = 'v13.0.3-wsp.2'; isDraft = $false; isPrerelease = $false }
$stableNewFirst = @{ tagName = 'v13.0.3-wsp.1'; isDraft = $false; isPrerelease = $false }
$draftNew = @{ tagName = 'v13.0.3-wsp.2'; isDraft = $true; isPrerelease = $false }
$prereleaseNew = @{ tagName = 'v13.0.3-wsp.2'; isDraft = $false; isPrerelease = $true }

Test-Decision -Name 'First publication' -Version '12.10.6' -Changed $true -Force $false `
    -Releases @() -ExpectedRequired $true -ExpectedTag 'v12.10.6-wsp.1'
Test-Decision -Name 'Pinned stable already published' -Version '12.10.6' -Changed $false -Force $false `
    -Releases @($stableOld, $stableOldFirst) -ExpectedRequired $false -ExpectedTag 'v12.10.6-wsp.2'
Test-Decision -Name 'New upstream inherits global WSP revision' -Version '13.0.3' -Changed $true -Force $false `
    -Releases @($stableOld, $stableOldFirst) -ExpectedRequired $true -ExpectedTag 'v13.0.3-wsp.2'
Test-Decision -Name 'Retry missing release after upstream pin commit' -Version '13.0.3' -Changed $false -Force $false `
    -Releases @($stableOld, $stableOldFirst) -ExpectedRequired $true -ExpectedTag 'v13.0.3-wsp.2' `
    -ExpectedReason 'retry-unpublished-upstream'
Test-Decision -Name 'Same version stable already published' -Version '13.0.3' -Changed $false -Force $false `
    -Releases @($stableOld, $stableNew) -ExpectedRequired $false -ExpectedTag 'v13.0.3-wsp.2'
Test-Decision -Name 'Do not invent missing WSP revision hotfix' -Version '13.0.3' -Changed $false -Force $false `
    -Releases @($stableOld, $stableNewFirst) -ExpectedRequired $false -ExpectedTag 'v13.0.3-wsp.2'
Test-Decision -Name 'Explicit newer hotfix revision' -Version '13.0.3' -Changed $false -Force $true `
    -Releases @($stableOld, $stableNewFirst) -Revision '3' -ExpectedRequired $true -ExpectedTag 'v13.0.3-wsp.3'
Test-Decision -Name 'Never overwrite existing stable tag even if forced' -Version '13.0.3' -Changed $false -Force $true `
    -Releases @($stableOld, $stableNew) -ExpectedRequired $false -ExpectedTag 'v13.0.3-wsp.2'
Test-Decision -Name 'Never overwrite an existing draft tag' -Version '13.0.3' -Changed $true -Force $false `
    -Releases @($stableOld, $draftNew) -ExpectedRequired $false -ExpectedTag 'v13.0.3-wsp.2'
Test-Decision -Name 'Never overwrite an existing prerelease tag' -Version '13.0.3' -Changed $true -Force $false `
    -Releases @($stableOld, $prereleaseNew) -ExpectedRequired $false -ExpectedTag 'v13.0.3-wsp.2'

$rollbackRejected = $false
try {
    & $scriptPath -TelegramVersion '12.10.5' -UpstreamChanged $true -ForceRelease $true `
        -ReleaseListJson (ConvertTo-Json -InputObject @($stableOld) -Compress) -ReleaseRef 'test' | Out-Null
} catch {
    if ($_.Exception.Message -notmatch 'older than published stable') { throw }
    $rollbackRejected = $true
}
if (-not $rollbackRejected) { throw 'Release rollback was not rejected.' }
Write-Host 'PASS: version rollback rejected'

Write-Host 'Release decision regression tests passed.'
