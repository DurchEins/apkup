# ApkUp Versioning Policy (Semantic Versioning 2.0.0)

**ApkUp** strictly follows **[Semantic Versioning 2.0.0 (SemVer)](https://semver.org/)** starting with `v1.47.0`. Legacy Git tags and GitHub Releases up to and including `v1.46.0` remain untouched for historical continuity.

---

## 1. Canonical Format Across All Surfaces

| Surface | Stable Format | Pre-Release Format | Example (`v1.47.0`) |
| :--- | :--- | :--- | :--- |
| **Single Source of Truth** (`version.properties`) | `MAJOR`, `MINOR`, `PATCH`, `VERSION_PRE_RELEASE=`, `VERSION_CODE` | `VERSION_PRE_RELEASE=beta.1` | `1`, `47`, `0`, `""`, `238` |
| **Git Tags** (pure SemVer) | `vMAJOR.MINOR.PATCH` | `vMAJOR.MINOR.PATCH-beta.N` | `v1.47.0` / `v1.47.0-beta.1` |
| **GitHub & GitLab Release Titles** | `vMAJOR.MINOR.PATCH (BUILD)` | `vMAJOR.MINOR.PATCH-beta.N (BUILD)` | `v1.47.0 (238)` |
| **Android `versionCode`** | Strictly monotonic positive integer | Strictly monotonic positive integer | `238`, `239`, … |
| **Android `versionName`** | `MAJOR.MINOR.PATCH+BUILD.SHORT_GIT_HASH` | `MAJOR.MINOR.PATCH-beta.N+BUILD.SHORT_GIT_HASH` | `1.47.0+238.74a9f8d` |
| **GitHub Release APK Filename** | `ApkUp-vMAJOR.MINOR.PATCH-buildBUILD.apk` | `ApkUp-vMAJOR.MINOR.PATCH-beta.N-buildBUILD.apk` | `ApkUp-v1.47.0-build238.apk` |

### Key Technical Guarantees
- **SemVer Precedence & Build Metadata:** Build metadata after `+` (e.g. `+238.74a9f8d`) is **strictly ignored** during version comparison (`1.47.0+238.abc == v1.47.0`). Pre-release precedence obeys `alpha < beta < rc < stable` with numeric identifier comparison.
- **No Misinterpreted Filenames:** Release asset names use `-build<VERSION_CODE>.apk` so `-build238` is never parsed as a pre-release identifier.
- **Fallback Out of Git:** If built outside a Git checkout, `SHORT_GIT_HASH` falls back cleanly to `nogit`.

---

## 2. Objective Bump Evaluation Rules

Before every new release, evaluate commits since the previous release tag (`git log <last-tag>..HEAD` via [`scripts/suggest-version.ps1`](scripts/suggest-version.ps1)):

1. **`PATCH` (`+0.0.1`):**
   - Only backwards-compatible bug fixes (`fix:`), security patches, dependency maintenance, translations, documentation (`docs:`), CI (`ci:`), tests (`test:`), or internal refactoring without user-facing new features or breaking changes.
2. **`MINOR` (`+0.1.0`, resets `PATCH` to `0`):**
   - At least one backwards-compatible new feature (`feat:`) is included.
   - Minor versions grow monotonically (`1.9.0 → 1.10.0`, `1.99.0 → 1.100.0`) — reaching `.9` or `.99` **never** triggers a Major bump.
3. **`MAJOR` (`+1.0.0`, resets `MINOR` & `PATCH` to `0`) — Two-Stage Rule:**
   - **Stage 1 — Mandatory Major:** Automatically required whenever commits introduce hard backwards-incompatibility (`feat!:`, `fix!:`, or any `BREAKING CHANGE:` footer), breaking backup/storage/preference schemas without automatic migration, breaking URI/deep-link/intent contracts, or dropping critical compatibility contracts.
   - **Stage 2 — Milestone Major (Never unilateral):** If no hard breaking change exists, **never bump `MAJOR` unilaterally**. Instead, explicitly present the option (pros & cons) to the maintainer for approval when shipping a complete UI/UX overhaul, a fundamental architectural rewrite under the hood, or an intentional product relaunch.

> [!IMPORTANT]
> **Strict Independence from Pre-Edited Docs:** Already prepared version numbers in changelogs, `README.md`, `SECURITY.md`, or `website/` must **NEVER** influence bump evaluation. Evaluate changes objectively from Git commits & scope first; if the maintainer chooses a different bump (such as a Milestone Major), adjust documentation version numbers cleanly afterward.

---

## 3. Release Workflow Summary

1. Run `pwsh -File scripts/suggest-version.ps1` (or `powershell -ExecutionPolicy Bypass -File scripts/suggest-version.ps1`) to inspect commits since the last tag.
2. Decide `PATCH` / `MINOR` / `MAJOR` (or `-beta.N`) and update [`version.properties`](version.properties) (plus `VERSION_CODE = <current + 1>`).
3. Update in-app changelogs (`<Major>.<Minor>_de.md` & `_en.md`), `README.md`, `SECURITY.md`, `website/src/data/content.ts`, and Wiki docs on the PR branch.
4. Merge via PR into `main`, trigger `.github/workflows/release.yml` with tag `vMAJOR.MINOR.PATCH` (or `vMAJOR.MINOR.PATCH-beta.N`), and verify the single canonical APK & `SHA256SUMS`.
