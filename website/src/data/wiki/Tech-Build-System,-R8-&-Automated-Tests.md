# 🧪 Technical Overview: Build System, R8 & Automated Tests

This page documents ApkUp's Gradle build configuration, R8 shrinking, Android Lint checks, and automated unit test suites.

---

## 1. Gradle & Build Performance

- **Kotlin + Jetpack Compose + Koin:** Built with Gradle (`app/build.gradle.kts`), Kotlin incremental compilation (`kotlin.incremental=true`), configuration/build caching (`org.gradle.caching=true`), and parallel execution (`org.gradle.parallel=true`).
- **Build Variants:**
  - `debug`: Fast iteration build without R8, using `applicationIdSuffix = ".debug"` (`com.apkup.debug`) and `versionNameSuffix = "-debug"`.
  - `release`: Full R8 code shrinking/obfuscation and resource shrinking, signed via release keystore (`local.properties` locally or CI secrets in `release.yml`).

---

## 2. Automated Test Suite (`testDebugUnitTest` — 413+ Unit Tests)

- **`PrefsTest`:** Verifies `PrefSlot` defaults, persistence across backend re-instantiation, and reactive `StateFlow` emissions.
- **`ChangelogBottomSheetTest`:**
  - Parses monthly accordion sections (`## Month Year`), release headers (`### Date | Version | Badge`), pill types (`Feature`, `Patch`, `Major`), and mandatory `> [Banner]` blocks on `.0` feature releases.
  - Enforces formatting rules on production assets (`1.47_de.md`, `1.47_en.md`): no trailing colons on entry titles and no inline backticks in descriptions.
- **`AccessibilityAuditTest`:** Audits localization completeness across all 9 locales for accessibility strings (`theme_a11y`, `a11y_accessible_ui_*`, `a11y_sun_*`, `onboarding_mode_*`, etc.).
- **Repository, Integrity & State Tests:** Covers `ApkIntegrityVerifier`, `UpdatesUiState`, `SolarScheduleCalculator`, source parsers, and backup serialization.

---

## 3. Static Analysis (`lintDebug`) & Website Verification

- **`lintDebug`:** Enforces Android resource/layout/API safety including `UnusedResources` (preventing dead strings across 9 locales) and `RemoteViewLayout` (ensuring widget XMLs only use `RemoteViews`-supported views).
- **`website/scripts/check-a11y.mjs`:** Runs as part of website validation to check all 90 built HTML pages for AA/AAA contrast tokens and ARIA compliance.
