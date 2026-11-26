# 🏷️ Technical Overview: Versioning Standard (Legacy vs. SemVer 2.0.0)

This page documents ApkUp's transition from its legacy versioning scheme (up to `v1.46.0`) to strict **Semantic Versioning 2.0.0 (SemVer)** starting with `v1.47.0`, including artifact naming, self-updater precedence, release evaluation rules, and branch isolation.

> All previously published Git tags and GitHub releases up to and including `v1.46.0` remain completely untouched.

---

## 1. Legacy Standard (up to `v1.46.0`) vs. SemVer 2.0.0 (`v1.47.0+`)

| Aspect | Legacy Standard (up to `v1.46.0`) | SemVer 2.0.0 Standard (`v1.47.0+`) |
| :--- | :--- | :--- |
| **Git Tag (Stable)** | `v1.46.0` | `vMAJOR.MINOR.PATCH` (e.g. `v1.47.0`) |
| **Git Tag (Pre-Release)** | `v1.41.25-Beta` / `v1.45.0-beta.1` | `vMAJOR.MINOR.PATCH-beta.N` (e.g. `v1.47.0-beta.1`, `v1.47.0-beta.2`) |
| **Release Title (GitHub / GitLab)** | `v1.46.0` | `vMAJOR.MINOR.PATCH (VERSION_CODE)` / `vMAJOR.MINOR.PATCH-beta.N (VERSION_CODE)` |
| **Android `versionCode`** | Monotonic integer (`1` .. `231`) | Strictly monotonic integer (`238`, `239`, ...) |
| **Android `versionName`** | `1.46.0 (231)` (or `1.47.0-238` in CI) | `MAJOR.MINOR.PATCH[-beta.N]+VERSION_CODE.SHORT_GIT_HASH` (e.g. `1.47.0+238.74a9f8d`) |
| **Release APK Filename** | `ApkUp-v1.46.0-231.apk` | `ApkUp-v1.47.0-build238.apk` / `ApkUp-v1.47.0-beta.1-build238.apk` |
| **Version Source of Truth** | Hardcoded in `app/build.gradle.kts` | Centralized `version.properties` at repository root |

---

## 2. Single Source of Truth (`version.properties`) & CI Asset Naming

- **Central Configuration (`version.properties`):** Holds `VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH`, `VERSION_PRE_RELEASE`, and `VERSION_CODE` in one place so neither `app/build.gradle.kts` nor `.github/workflows/release.yml` needs fragile regex parsing on Kotlin build scripts.
- **Automatic Git Commit Hash:** Gradle (`app/build.gradle.kts`) reads `git rev-parse --short HEAD` via `providers.exec` (configuration-cache compatible) and falls back cleanly to `nogit` when built outside a Git repository. An optional `BUILD_NUMBER` environment variable can override `VERSION_CODE` in CI.
- **Why `-build<versionCode>` in APK Filenames:**
  - A bare `-238` suffix (as in `ApkUp-v1.47.0-238.apk`) can be misinterpreted by strict SemVer parsers as a pre-release identifier.
  - A `+238` suffix (as in `ApkUp-v1.47.0+238.apk`) is rewritten by GitHub Release asset uploads into a dot (`.`).
  - Using `-build<versionCode>` (`ApkUp-v1.47.0-build238.apk`) avoids both issues and keeps filenames unambiguous across GitHub, GitLab, Obtainium, and shell scripts.

---

## 3. In-App Self-Updater, Precedence & Backward Compatibility

- **Strict SemVer 2.0.0 Precedence (`SelfUpdateRepository.kt`):**
  - Pre-releases sort below stable releases: `1.47.0-alpha.1` < `1.47.0-beta.1` < `1.47.0-beta.2` < `1.47.0-rc.1` < `1.47.0` < `2.0.0`.
  - Build metadata after `+` (such as `+238.74a9f8d`) is **ignored** when comparing version precedence.
  - Legacy parenthesized build tie-breakers (e.g. `1.39.3-Beta (148)` vs. `1.39.3-Beta (149)`) remain supported for older releases, while `-build<N>` in APK filenames is never misparsed as a pre-release.
- **Verified Backward Compatibility (`v1.46.0` → `v1.47.0+`):**
  - Installed `v1.46.0` builds evaluate `release.tag_name` (`v1.47.0` / `v1.47.0-beta.1`), not the APK filename, and select release assets solely by `.apk` extension (excluding `-debug.apk` and `-unaligned.apk`) from trusted GitHub hosts alongside `asset.digest` / `.sha256` sidecars.
  - Existing `v1.46.0` installations therefore discover, verify, and install `ApkUp-v1.47.0-build238.apk` without any migration gap.
- **UI & Changelog Support:**
  - `ChangelogBottomSheet.kt` strips `+...` build metadata before resolving milestone asset prefixes (`1.47`) and release badges (`Major Update`, `Feature Update`, `Patch & Fix`).
  - `SettingsScreen.kt` renders long `versionName` strings cleanly in both standard and Accessible Mode, including Tap-to-Copy for full version and build details.

---

## 4. Release Evaluation Rules (Conventional Commits & Two-Stage Major)

Before each release, commits since the previous Git tag (`git log <last-tag>..HEAD`) are evaluated objectively (supported by `VERSIONING.md` and `scripts/suggest-version.ps1`):

1. **PATCH (`x.y.Z`):** Only `fix:`, security patches, localization updates, documentation, or maintenance chores.
2. **MINOR (`x.Y.0`):** At least one backwards-compatible `feat:` commit. There is **no** automatic Major bump at `.9` or `.99`.
3. **MAJOR (`X.0.0`) — Two-Stage Check:**
   - **Mandatory Major:** Triggered immediately by `feat!:` / `BREAKING CHANGE:` commits or genuine breaking changes (incompatible backups/databases, removal of a core update source, significant `minSdk` bump, or incompatible signing/update path).
   - **Milestone Major (Proposal Only):** Proposed as an explicit option alongside Minor when a release introduces a complete UI/navigation redesign, a from-scratch rewrite of a core engine, or a deliberate product relaunch — never applied silently without maintainer sign-off.
4. **Independence from Draft Docs:** Pre-edited version numbers in draft changelogs, `README.md`, `SECURITY.md`, or website metadata never constrain the version decision; if a Major bump is chosen, those documents are updated to match.

---

## 5. Developer Workflow: `-Latest` Artifacts & Worktrees

- **Canonical Release vs. `-Latest` Naming:** Official releases follow the Single-Artifact Principle via `release.yml`. Local or mirror tracking builds use `ApkUp-v<Major>.<Minor>.<Patch>-build<versionCode>-Latest.apk`, renaming the previous `-Latest` file to `ApkUp-v<Major>.<Minor>.<Patch>-(<versionCode>).apk` to avoid ambiguity.
- **Branch & Worktree Isolation:** Feature and fix branches are developed off `main` and merged via Pull Request after `Android CI` (`lintDebug`, `testDebugUnitTest`, `assembleDebug`) passes. Experimental side-project branches (`experiment/*`) stay isolated in dedicated worktrees: they may merge updates from `main`, but never modify or merge into `main` without explicit maintainer approval.

---

> 📖 **Related:** [Tech: CI/CD, Single-Artifact Releases & Checksums](https://github.com/DurchEins/apkup/wiki/Tech-CI-CD,-Single-Artifact-Releases-&-Checksums) • [VERSIONING.md](https://github.com/DurchEins/apkup/blob/main/VERSIONING.md)
