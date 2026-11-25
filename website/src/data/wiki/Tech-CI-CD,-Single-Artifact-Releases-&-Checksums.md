# 🚀 Technical Overview: CI/CD, Single-Artifact Releases & Checksums

This page documents ApkUp's GitHub Actions CI/CD workflows, versioning rules, signing & checksum verification, and mirror distribution.

---

## 1. Strict Semantic Versioning

- **Format:** `versionName` follows `<Major>.<Minor>.<Patch> (<versionCode>)` (e.g. `1.47.0 (237)`), with `<Major>.<Minor>.<Patch>-beta.<N> (<versionCode>)` for explicit pre-releases.
- **Monotonic `versionCode`:** `versionCode` increments strictly monotonically by `+1` with every build; UI components (`SettingsScreen.kt`, `ChangelogBottomSheet.kt`) and `SelfUpdateRepository.kt` support 3–4+ digit segments without wrapping.

---

## 2. Pull Request & CI Gates (`Android CI`)

- **Branch & PR Flow:** Features and fixes develop on dedicated local branches off `main`, are pushed to GitHub (`DurchEins/apkup`), and merge via Pull Request (`Squash and merge`) after all CI checks pass.
- **Automated Checks:** Every PR runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug` (plus `website/scripts/check-a11y.mjs` for website changes).
- **Debug Parallelism:** Debug builds use `applicationIdSuffix = ".debug"` (`com.apkup.debug` / **ApkUp Debug**) so development builds can be tested side-by-side with the production `com.apkup` release.

---

## 3. Single-Artifact Release Pipeline (`release.yml`)

- **Canonical Binary:** The signed APK built by GitHub Actions (`release.yml`) is the **single canonical release artifact** (`ApkUp-v<Major>.<Minor>.<Patch>-(<versionCode>).apk`).
- **Signing Fingerprint Verification:** Every official APK is signed with ApkUp's release key (`8e9b512371ee965afc7cc91f23490d3a514623b90d483e638bba8a070a2170b4` SHA-256).
- **Checksums & Mirrors:** `SHA256SUMS` digests are published alongside the binary on GitHub Releases and the ApkUp Website, ensuring identical SHA-256 and VirusTotal hashes across GitLab Releases, Zapstore, itch.io, APKPure, Appteka, and Komi Store.
- **In-App Self-Updater (`SelfUpdateRepository`):** Checks GitHub Releases (stable-only by default, with an opt-in Beta channel toggle in Setup → About), verifies `SHA256SUMS` and signing certificate fingerprints before installing.

---

> 📦 **Latest Releases:** [GitHub Releases](https://github.com/DurchEins/apkup/releases) • **ProTip:** Append `.patch` or `.diff` to any GitHub commit or PR URL for plaintext views.
