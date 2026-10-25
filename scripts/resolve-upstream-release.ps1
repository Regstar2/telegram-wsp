[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TelegramVersion,
    [Parameter(Mandatory = $true)][bool]$UpstreamChanged,
    [Parameter(Mandatory = $true)][bool]$ForceRelease,
    [Parameter(Mandatory = $true)][string]$ReleaseListJson,
    [Parameter(Mandatory = $true)][string]$ReleaseRef,
    [string]$RequestedRevision = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($TelegramVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw "Unsupported Telegram version: $TelegramVersion"
}
$targetVersion = [version]$TelegramVersion

$releases = @(ConvertFrom-Json -InputObject $ReleaseListJson)
$highestStableVersion = $null
$highestStableRevision = 0
$highestCurrentRevision = 0
$currentVersionPublished = $false
$knownTags = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

foreach ($release in $releases) {
    if ($null -eq $release.tagName -or [string]::IsNullOrWhiteSpace([string]$release.tagName)) {
        throw 'GitHub release list contains a missing tagName.'
    }
    [void]$knownTags.Add([string]$release.tagName)
    if ($release.isDraft -or $release.isPrerelease) { continue }
    if ($release.tagName -notmatch '^v(\d+\.\d+\.\d+)-wsp\.(\d+)$') { continue }

    $version = [version]$Matches[1]
    $revision = [int]$Matches[2]
    if ($revision -lt 1 -or $revision -gt 99) {
        throw "Stable Telegram-WSP release revision is invalid: $($release.tagName)"
    }
    if ($null -eq $highestStableVersion -or $version -gt $highestStableVersion) {
        $highestStableVersion = $version
    }
    if ($revision -gt $highestStableRevision) {
        $highestStableRevision = $revision
    }
    if ($version -eq $targetVersion) {
        $currentVersionPublished = $true
        if ($revision -gt $highestCurrentRevision) {
            $highestCurrentRevision = $revision
        }
    }
}

if ($null -ne $highestStableVersion -and $targetVersion -lt $highestStableVersion) {
    throw "Pinned Telegram $TelegramVersion is older than published stable Telegram $highestStableVersion."
}
if ($RequestedRevision -ne '' -and $RequestedRevision -notmatch '^\d+$') {
    throw 'Requested Telegram-WSP revision must be decimal digits.'
}
$revision = if ($RequestedRevision -eq '') {
    [Math]::Max(1, $highestStableRevision)
} else {
    [int]$RequestedRevision
}
if ($revision -lt 1 -or $revision -gt 99) {
    throw 'Requested Telegram-WSP revision must be between 1 and 99.'
}

$tag = "v$TelegramVersion-wsp.$revision"
if ($currentVersionPublished -and $revision -lt $highestCurrentRevision -and
    -not $knownTags.Contains($tag)) {
    throw "Refusing to publish lower WSP revision $tag after revision $highestCurrentRevision."
}

$tagExists = $knownTags.Contains($tag)
$missingStableForPin = -not $currentVersionPublished
# A failed Windows signing/publishing job can leave the validated upstream pin
# on main. Retry the missing release on the next scheduled run without requiring
# the Telegram master commit to change again.
$releaseRequired = -not $tagExists -and (
    $ForceRelease -or
    ($missingStableForPin -and ($UpstreamChanged -or
        $null -eq $highestStableVersion -or $targetVersion -gt $highestStableVersion))
)
$reason = if ($tagExists) {
    'tag-already-exists'
} elseif ($releaseRequired -and $UpstreamChanged) {
    'new-upstream'
} elseif ($releaseRequired -and $ForceRelease) {
    'explicit-force'
} elseif ($releaseRequired) {
    'retry-unpublished-upstream'
} else {
    'stable-current'
}

[ordered]@{
    releaseRequired = [bool]$releaseRequired
    releaseRef = $ReleaseRef
    revision = $revision
    tag = $tag
    reason = $reason
} | ConvertTo-Json -Compress
