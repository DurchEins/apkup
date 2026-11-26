#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Evaluates commits since the latest SemVer Git tag and outputs an objective
    ApkUp SemVer 2.0.0 release recommendation template (independent of doc files).
#>
param(
    [string]$BaseRef = "HEAD"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot
try {
    $propsPath = Join-Path $repoRoot "version.properties"
    $props = @{}
    if (Test-Path $propsPath) {
        Get-Content $propsPath | ForEach-Object {
            if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$') {
                $props[$matches[1]] = $matches[2].Trim()
            }
        }
    }

    $major = if ($props.ContainsKey("VERSION_MAJOR") -and $props["VERSION_MAJOR"]) { [int]$props["VERSION_MAJOR"] } else { 1 }
    $minor = if ($props.ContainsKey("VERSION_MINOR") -and $props["VERSION_MINOR"]) { [int]$props["VERSION_MINOR"] } else { 0 }
    $patch = if ($props.ContainsKey("VERSION_PATCH") -and $props["VERSION_PATCH"]) { [int]$props["VERSION_PATCH"] } else { 0 }
    $pre   = if ($props.ContainsKey("VERSION_PRE_RELEASE")) { $props["VERSION_PRE_RELEASE"].Trim() } else { "" }
    $code  = if ($props.ContainsKey("VERSION_CODE") -and $props["VERSION_CODE"]) { [int]$props["VERSION_CODE"] } else { 1 }
    $nextCode = $code + 1
    $shortHash = (& git rev-parse --short $BaseRef 2>$null).Trim()
    if (-not $shortHash) { $shortHash = "nogit" }

    $lastTag = (& git describe --tags --abbrev=0 $BaseRef 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $lastTag) {
        $range = $BaseRef
        $lastTagLabel = "(no prior tag)"
    } else {
        $lastTag = $lastTag.Trim()
        $range = "$lastTag..$BaseRef"
        $lastTagLabel = $lastTag
    }

    $rawLog = & git log $range --format="%H%x1f%s%x1f%b%x1e"
    $entries = @()
    if ($rawLog) {
        $joined = ($rawLog -join "`n")
        foreach ($rec in ($joined -split [char]0x1e)) {
            $trimRec = $rec.Trim()
            if (-not $trimRec) { continue }
            $parts = $trimRec -split [char]0x1f
            $entries += [PSCustomObject]@{
                Hash    = $parts[0]
                Subject = if ($parts.Count -gt 1) { $parts[1] } else { "" }
                Body    = if ($parts.Count -gt 2) { $parts[2] } else { "" }
            }
        }
    }

    $breaking = @()
    $features = @()
    $fixes    = @()
    $other    = @()

    foreach ($e in $entries) {
        $sub = $e.Subject
        $hasBreakingFooter = $e.Body -match '(?m)^BREAKING CHANGE:'
        if ($sub -match '^[a-z]+(\([^)]+\))?!:' -or $hasBreakingFooter) {
            $breaking += $sub
        } elseif ($sub -match '^feat(\([^)]+\))?:') {
            $features += $sub
        } elseif ($sub -match '^fix(\([^)]+\))?:') {
            $fixes += $sub
        } else {
            $other += $sub
        }
    }

    $tagMajor = $major
    $tagMinor = $minor
    $tagPatch = $patch
    if ($lastTag -and $lastTag -match '^v?(\d+)\.(\d+)\.(\d+)') {
        $tagMajor = [int]$matches[1]
        $tagMinor = [int]$matches[2]
        $tagPatch = [int]$matches[3]
    }

    $patchCandidate = "$tagMajor.$tagMinor.$($tagPatch + 1)"
    $minorCandidate = "$tagMajor.$($tagMinor + 1).0"
    $majorCandidate = "$($tagMajor + 1).0.0"

    if ($breaking.Count -gt 0) {
        $recommendation = "MAJOR ($majorCandidate) - Mandatory Major due to breaking change commits"
    } elseif ($features.Count -gt 0) {
        $recommendation = "MINOR ($minorCandidate) - Feature commits present; evaluate Milestone Major ($majorCandidate) separately if a major architecture/UI relaunch occurred"
    } else {
        $recommendation = "PATCH ($patchCandidate) - Only fixes, docs, chores, or maintenance"
    }

    Write-Host "=== ApkUp SemVer 2.0.0 Version Advisor ===" -ForegroundColor Cyan
    Write-Host "Latest released Git tag    : $lastTagLabel"
    Write-Host "Pending version.properties : $major.$minor.$patch$(if ($pre) { "-$pre" })+$code.$shortHash"
    Write-Host "Commit range analyzed      : $range"
    Write-Host "Commits found              : $($entries.Count) (Breaking=$($breaking.Count), Features=$($features.Count), Fixes=$($fixes.Count), Other=$($other.Count))"
    Write-Host ""
    Write-Host "Objective Recommendation   : $recommendation" -ForegroundColor Green
    Write-Host "  - PATCH option           : v$patchCandidate ($nextCode)  | APK: ApkUp-v$patchCandidate-build$nextCode.apk"
    Write-Host "  - MINOR option           : v$minorCandidate ($nextCode)  | APK: ApkUp-v$minorCandidate-build$nextCode.apk"
    Write-Host "  - MAJOR option (2-stage) : v$majorCandidate ($nextCode)  | APK: ApkUp-v$majorCandidate-build$nextCode.apk"
    Write-Host ""
    Write-Host "NOTE: Existing version numbers in README/changelogs/docs were intentionally ignored during evaluation."
    if ($breaking.Count -gt 0) {
        Write-Host "`nBreaking changes:" -ForegroundColor Red
        $breaking | ForEach-Object { Write-Host "  - $_" }
    }
    if ($features.Count -gt 0) {
        Write-Host "`nFeatures ($($features.Count)):" -ForegroundColor Yellow
        $features | Select-Object -First 15 | ForEach-Object { Write-Host "  - $_" }
    }
    if ($fixes.Count -gt 0) {
        Write-Host "`nFixes ($($fixes.Count)):" -ForegroundColor Blue
        $fixes | Select-Object -First 10 | ForEach-Object { Write-Host "  - $_" }
    }
} finally {
    Pop-Location
}
