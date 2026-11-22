# Contributing to ApkUp

Thank you for your interest in contributing to **ApkUp**! Whether you are fixing a bug, improving translations, or proposing a new feature, your help is appreciated.

---

## 🌍 Translations (No Git Required)

ApkUp's **app and website UI** are available in **9 languages** (`en`, `de`, `es`, `fr`, `it`, `ja`, `pt`, `ru`, `zh-cn`), while the **Wiki guides** are primarily maintained in English.

- **Preferred method:** Translate directly in your browser on **[Hosted Weblate](https://hosted.weblate.org/engage/apkup/)**.
- **Quick typos or new language requests:** Open a **[Translation Issue](https://github.com/DurchEins/apkup/issues/new?template=translation.yml)**.

---

## 🐛 Reporting Bugs & Suggesting Features

Before opening a new issue, please check [existing issues](https://github.com/DurchEins/apkup/issues) and the [Troubleshooting Wiki](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors).

- **[Report a Bug](https://github.com/DurchEins/apkup/issues/new?template=bug_report.yml)** — include your Android version, ApkUp version, update source, and steps to reproduce.
- **[Request a Feature](https://github.com/DurchEins/apkup/issues/new?template=feature_request.yml)** — describe the use case and expected behavior.
- **Security vulnerabilities:** Do **not** open a public issue. Follow [SECURITY.md](SECURITY.md) and use GitHub Private Vulnerability Reporting.

---

## 🛠️ Development & Pull Requests

### Prerequisites
- **Android Studio** (or JDK 17+) with **Android SDK 37** (`minSdk = 29`, `targetSdk = 37`)
- **Gradle** wrapper included (`./gradlew`)

### Local Build & Verification
```bash
git clone https://github.com/DurchEins/apkup.git
cd apkup
./gradlew lintDebug testDebugUnitTest assembleDebug
```

### Guidelines
1. **Branch from `main`** with a descriptive name (e.g., `fix/...`, `feat/...`, `docs/...`).
2. **Keep PRs reviewable:** Describe all included changes clearly in the PR summary so they are easy to review and test.
3. **Verify locally:** Ensure `lintDebug` and `testDebugUnitTest` pass before submitting.
4. **Match project conventions:** Follow Kotlin / Jetpack Compose & Material 3 Expressive patterns used in the codebase, and preserve user-facing privacy guarantees (no tracking, no unconsented network requests, strict host-isolated tokens).
5. **UI changes:** Include before/after screenshots in your PR description.

---

## 📜 License

By contributing, you agree that your contributions will be licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.
