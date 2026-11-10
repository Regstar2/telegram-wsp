[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 99)][int]$ReleaseRevision,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ApkSha256,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [string]$PreviousReleaseTag = '',
    [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$CurrentRef = '',
    [string]$RepositoryPath = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputPath = 'dist/release-notes.md'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = [IO.Path]::GetFullPath($RepositoryPath)
$upstream = Get-Content (Join-Path $root 'config/upstream.json') -Raw | ConvertFrom-Json
$core = Get-Content (Join-Path $root 'config/core.json') -Raw | ConvertFrom-Json
$tag = "v$($upstream.telegramVersion)-wsp.$ReleaseRevision"

if ($CurrentRef -eq '') {
    $CurrentRef = (& git -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $CurrentRef -notmatch '^[a-fA-F0-9]{40}$') {
        throw 'Cannot resolve current release commit.'
    }
}

$notes = [System.Collections.Generic.List[string]]::new()
$notes.Add("# Telegram-WSP $($upstream.telegramVersion) (WSP $ReleaseRevision)")
$notes.Add('')
$notes.Add("- Telegram upstream: $($upstream.telegramVersion) ($($upstream.telegramBuild))")
$notes.Add("- Telegram commit: $($upstream.pinnedCommit)")
$notes.Add("- tgwsproxy-core commit: $($core.pinnedCommit)")
$notes.Add('- Android package: org.telegram.messenger.web')
$notes.Add("- APK SHA-256: $($ApkSha256.ToLowerInvariant())")
$notes.Add("- Release revision: $ReleaseRevision")
$notes.Add('')
$notes.Add('## Telegram upstream')
$notes.Add('')

if ($PreviousReleaseTag -ne '') {
    if ($PreviousReleaseTag -notmatch '^v\d+\.\d+\.\d+-wsp\.\d+$') {
        throw "Invalid previous stable release tag: $PreviousReleaseTag"
    }
    if ($PreviousReleaseTag -eq $tag) {
        throw "Previous release tag must differ from current tag: $tag"
    }

    $previousCommit = @(& git -C $root rev-parse --verify "$($PreviousReleaseTag)^{commit}")
    if ($LASTEXITCODE -ne 0 -or $previousCommit.Count -ne 1) {
        throw "Previous release tag not found in checked-out repository: $PreviousReleaseTag"
    }

    $priorUpstreamRaw = @(& git -C $root show "$($PreviousReleaseTag):config/upstream.json")
    if ($LASTEXITCODE -ne 0) { throw "Cannot read Telegram metadata from $PreviousReleaseTag." }
    $priorUpstream = ($priorUpstreamRaw -join [Environment]::NewLine) | ConvertFrom-Json

    $priorCoreRaw = @(& git -C $root show "$($PreviousReleaseTag):config/core.json")
    if ($LASTEXITCODE -ne 0) { throw "Cannot read proxy core metadata from $PreviousReleaseTag." }
    $priorCore = ($priorCoreRaw -join [Environment]::NewLine) | ConvertFrom-Json

    if ($priorUpstream.pinnedCommit -ne $upstream.pinnedCommit) {
        $notes.Add("- Updated Telegram from $($priorUpstream.telegramVersion) ($($priorUpstream.telegramBuild)) to $($upstream.telegramVersion) ($($upstream.telegramBuild)).")
        $notes.Add("- [Compare Telegram upstream commits](https://github.com/DrKLO/Telegram/compare/$($priorUpstream.pinnedCommit)...$($upstream.pinnedCommit))")
    }
    else {
        $notes.Add('- Telegram upstream source is unchanged from the previous stable release.')
    }

    # Only shipped WSP implementation inputs count. Documentation, CI, release
    # scripts and Telegram version pinning must not create a false WSP changelog.
    $wspPaths = @(
        'config/core.json',
        'integration/branding',
        'integration/telegram',
        'patches',
        'scripts/apply-integration.ps1',
        'scripts/prepare-integration.ps1'
    )
    $changedPaths = @(& git -C $root diff --name-only $PreviousReleaseTag $CurrentRef -- $wspPaths)
    if ($LASTEXITCODE -ne 0) { throw "Could not compare WSP sources with $PreviousReleaseTag." }

    $coreChanged = [string]$priorCore.pinnedCommit -ne [string]$core.pinnedCommit
    $integrationChanges = @($changedPaths | Where-Object { $_ -ne 'config/core.json' -and $_ -notmatch '(^|/)README\.md$' })
    $notes.Add('')
    $notes.Add('## Telegram-WSP changes')
    $notes.Add('')
    if (-not $coreChanged -and $integrationChanges.Count -eq 0) {
        $notes.Add("No WSP proxy core or integration changes since [$PreviousReleaseTag](https://github.com/$Repository/releases/tag/$PreviousReleaseTag).")
    }
    else {
        if ($coreChanged) {
            $notes.Add("- Updated tgwsproxy-core: $($priorCore.pinnedCommit) → $($core.pinnedCommit).")
        }
        if ($integrationChanges.Count -gt 0) {
            $notes.Add('- Changed WSP integration sources:')
            foreach ($path in $integrationChanges) {
                $notes.Add("  - $path")
            }
        }
        $notes.Add("- [Compare repository changes](https://github.com/$Repository/compare/$PreviousReleaseTag...$tag)")
        $notes.Add('This list identifies changed components, not an inferred list of user-facing features.')
    }
}
else {
    $notes.Add('First published release in the available history; see the project documentation for Telegram upstream details.')
    $notes.Add('')
    $notes.Add('## Telegram-WSP changes')
    $notes.Add('')
    $notes.Add('Baseline release. WSP features are documented in the project README.')
}

$releaseNotesPath = Join-Path $root "docs/releases/$tag.md"
$releaseNotesEnPath = Join-Path $root "docs/releases/$($tag)_EN.md"
if ((Test-Path -LiteralPath $releaseNotesPath) -and (Test-Path -LiteralPath $releaseNotesEnPath)) {
    $notes.Add('')
    $notes.Add("[Полное описание на русском](https://github.com/$Repository/blob/$tag/docs/releases/$tag.md) · [Full release notes in English](https://github.com/$Repository/blob/$tag/docs/releases/$($tag)_EN.md)")
}

$notes.Add('')
$notes.Add('The APK is signed with the persistent Telegram-WSP release key. Exact Corresponding Source, checksums and update feed are attached to this release.')

$absoluteOutputPath = if ([IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath
} else {
    Join-Path $root $OutputPath
}
New-Item -ItemType Directory -Path (Split-Path -Parent $absoluteOutputPath) -Force | Out-Null
[IO.File]::WriteAllText($absoluteOutputPath, ($notes -join [Environment]::NewLine) + [Environment]::NewLine)
Write-Host "Release notes: $tag (previous stable: $PreviousReleaseTag)"
