[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$writer = Join-Path $PSScriptRoot 'write-release-notes.ps1'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ("telegram-wsp-notes-" + [Guid]::NewGuid().ToString('N'))
$oldCore = '1' * 40
$newCore = '2' * 40
$oldTelegram = '3' * 40
$newTelegram = '4' * 40

function Save-Json {
    param([string]$Path, [object]$Value)
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 5))
}

function Commit-Fixture {
    param([string]$Message)
    & git -C $fixture add -A
    if ($LASTEXITCODE -ne 0) { throw 'git add fixture failed.' }
    & git -C $fixture commit -q -m $Message
    if ($LASTEXITCODE -ne 0) { throw "git commit fixture failed: $Message" }
    $sha = (& git -C $fixture rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'git rev-parse fixture failed.' }
    return $sha
}

function Write-TestNotes {
    param([string]$Commit, [string]$Previous = 'v13.1.1-wsp.3')
    & $writer -ReleaseRevision 3 -ApkSha256 ('a' * 64) -Repository 'Regstar2/telegram-wsp' -PreviousReleaseTag $Previous -CurrentRef $Commit -RepositoryPath $fixture -OutputPath 'dist/release-notes.md'
    return Get-Content (Join-Path $fixture 'dist/release-notes.md') -Raw
}

function Assert-Contains {
    param([string]$Value, [string]$Text)
    if (-not $Value.Contains($Text)) { throw "Expected release notes to contain: $Text" }
}
function Assert-NotContains {
    param([string]$Value, [string]$Text)
    if ($Value.Contains($Text)) { throw "Unexpected release notes content: $Text" }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $fixture 'config') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixture 'integration/telegram') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixture 'patches') -Force | Out-Null
    & git init -q $fixture
    if ($LASTEXITCODE -ne 0) { throw 'git init fixture failed.' }
    & git -C $fixture config user.email 'release-notes-test@example.invalid'
    & git -C $fixture config user.name 'Release Notes Test'

    $upstream = [ordered]@{ telegramVersion = '13.1.1'; telegramBuild = 7237; pinnedCommit = $oldTelegram }
    $core = [ordered]@{ pinnedCommit = $oldCore }
    Save-Json (Join-Path $fixture 'config/upstream.json') $upstream
    Save-Json (Join-Path $fixture 'config/core.json') $core
    [IO.File]::WriteAllText((Join-Path $fixture 'integration/telegram/Proxy.java'), 'original integration')
    [IO.File]::WriteAllText((Join-Path $fixture 'patches/README.md'), 'original documentation')
    [IO.File]::WriteAllText((Join-Path $fixture 'build-notes.txt'), 'original')
    $null = Commit-Fixture 'Initial release'
    & git -C $fixture tag v13.1.1-wsp.3
    if ($LASTEXITCODE -ne 0) { throw 'git tag fixture failed.' }

    # Telegram-only updates, documentation and release machinery changes
    # must never repeat WSP feature lists.
    $upstream.telegramVersion = '13.2.1'
    $upstream.telegramBuild = 7291
    $upstream.pinnedCommit = $newTelegram
    Save-Json (Join-Path $fixture 'config/upstream.json') $upstream
    [IO.File]::WriteAllText((Join-Path $fixture 'patches/README.md'), 'updated documentation')
    [IO.File]::WriteAllText((Join-Path $fixture 'build-notes.txt'), 'updated')
    $telegramOnlySha = Commit-Fixture 'Bump Telegram and documentation'
    $notes = Write-TestNotes -Commit $telegramOnlySha
    Assert-Contains $notes 'Updated Telegram from 13.1.1 (7237) to 13.2.1 (7291).'
    Assert-Contains $notes 'No WSP proxy core or integration changes since'
    Assert-NotContains $notes 'Changed WSP integration sources:'
    Assert-NotContains $notes 'Added the built-in proxy control screen'
    Write-Host 'PASS: Telegram-only update does not duplicate WSP changelog'

    # Actual integration diff is detected even at the same WSP revision.
    [IO.File]::WriteAllText((Join-Path $fixture 'integration/telegram/Proxy.java'), 'changed integration')
    $overlaySha = Commit-Fixture 'Update proxy integration'
    $notes = Write-TestNotes -Commit $overlaySha
    Assert-Contains $notes 'Changed WSP integration sources:'
    Assert-Contains $notes 'integration/telegram/Proxy.java'
    Assert-NotContains $notes 'No WSP proxy core or integration changes since'
    Write-Host 'PASS: integration source changes are detected'

    # Core SHA change is reported, but old static capabilities are not.
    $core.pinnedCommit = $newCore
    Save-Json (Join-Path $fixture 'config/core.json') $core
    $coreSha = Commit-Fixture 'Update proxy core pin'
    $notes = Write-TestNotes -Commit $coreSha
    Assert-Contains $notes "Updated tgwsproxy-core: $oldCore"
    Assert-Contains $notes $newCore
    Assert-NotContains $notes 'Added the built-in proxy control screen'
    Write-Host 'PASS: proxy core update is detected'

    # Without a previous release, do not invent a historical changelog.
    $notes = Write-TestNotes -Commit $coreSha -Previous ''
    Assert-Contains $notes 'Baseline release.'
    Assert-NotContains $notes 'Updated tgwsproxy-core:'
    Write-Host 'PASS: first-release notes are factual'

    $rejected = $false
    try {
        $null = Write-TestNotes -Commit $coreSha -Previous 'v99.0.0-wsp.1'
    }
    catch {
        if ($_.Exception.Message -notmatch 'Previous release tag not found') { throw }
        $rejected = $true
    }
    if (-not $rejected) { throw 'Missing previous release tag was silently ignored.' }
    Write-Host 'PASS: missing previous stable release tag fails closed'
}
finally {
    Remove-Item -LiteralPath $fixture -Force -Recurse -ErrorAction SilentlyContinue
}

Write-Host 'Incremental release notes regression tests passed.'
