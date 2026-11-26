# 🚀 Technical Overview: CI/CD, Single-Artifact Releases & Checksums

This page documents ApkUp's GitHub Actions CI/CD workflows, versioning rules, signing & checksum verification, and mirror distribution.

---

## 1. Strict Semantic Versioning 2.0.0 (`version.properties`)

- **Single Source of Truth (`version.properties`):** Centrally defines `VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH`, `VERSION_PRE_RELEASE`, and strictly monotonic `VERSION_CODE` (`versionCode`).
- **Git Tags & Release Titles:** Pure SemVer tags (`vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-beta.N`, e.g. `v1.47.0` / `v1.47.0-beta.1`); GitHub and GitLab release titles use `vMAJOR.MINOR.PATCH (VERSION_CODE)` (e.g. `v1.47.0 (238)`). Legacy tags up to `v1.46.0` remain unchanged.
- **Android `versionName`:** `MAJOR.MINOR.PATCH[-beta.N]+VERSION_CODE.SHORT_GIT_HASH` (e.g. `1.47.0+238.74a9f8d`, with `nogit` fallback outside Git).
- **Precedence & Two-Stage Major Rule (`VERSIONING.md`):** Build metadata after `+` is ignored during SemVer comparison; pre-releases follow `alpha < beta < rc < stable`. `PATCH` covers fixes/docs/chores, `MINOR` covers backwards-compatible features, and `MAJOR` follows the two-stage rule (mandatory on breaking changes, or user-approved Milestone Major — always evaluated independently of pre-edited documentation version numbers via `scripts/suggest-version.ps1`).
- **Multi-Digit Support:** `SelfUpdateRepository.kt`, `SettingsScreen.kt` (including Tap-to-Copy), and `ChangelogBottomSheet.kt` support multi-digit segments and full SemVer strings cleanly.

---

## 2. Pull Request & CI Gates (`Android CI`)

- **Branch & PR Flow:** Features and fixes develop on dedicated local branches off `main` (never merging or modifying `main` from experimental `experiment/*` branches), are pushed to GitHub (`DurchEins/apkup`), and merge via Pull Request (`Squash and merge`) after all CI checks pass.
- **Automated Checks:** Every PR runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug` (plus `website/scripts/check-a11y.mjs` for website changes).
- **Debug Parallelism:** Debug builds use `applicationIdSuffix = ".debug"` (`com.apkup.debug` / **ApkUp Debug**) so development builds can be tested side-by-side with the production `com.apkup` release.

---

## 3. Single-Artifact Release Pipeline (`release.yml`)

- **Canonical Binary:** The signed APK built by GitHub Actions (`release.yml`) is the **single canonical release artifact** (`ApkUp-v<Major>.<Minor>.<Patch>-build<versionCode>.apk` or `ApkUp-v<Major>.<Minor>.<Patch>-beta.<N>-build<versionCode>.apk`, so `-build<N>` is never misparsed as a pre-release).
- **Signing Fingerprint Verification:** Every official APK is signed with ApkUp's release key (`8e9b512371ee965afc7cc91f23490d3a514623b90d483e638bba8a070a2170b4` SHA-256).
- **Checksums & Mirrors:** `SHA256SUMS` digests are published alongside the binary on GitHub Releases and the ApkUp Website, ensuring identical SHA-256 and VirusTotal hashes across GitLab Releases, Zapstore, itch.io, APKPure, Appteka, and Komi Store.
- **In-App Self-Updater (`SelfUpdateRepository`):** Checks GitHub Releases (stable-only by default, with an opt-in Beta channel toggle in Setup → About), verifies `SHA256SUMS` and signing certificate fingerprints before installing.

---

> 📦 **Latest Releases:** [GitHub Releases](https://github.com/DurchEins/apkup/releases) • **Versioning Details:** [Tech: Versioning Standard (Legacy vs. SemVer 2.0.0)](https://github.com/DurchEins/apkup/wiki/Tech-Versioning,-SemVer-2.0.0-&-Migration) • **ProTip:** Append `.patch` or `.diff` to any GitHub commit or PR URL for plaintext views.
