---
description: "Central knowledge base for ApkUp: multi-source Android updates, Shizuku automation, SHA-256 verification, and troubleshooting."
icon: book-open
---

# 📖 Welcome to the Official ApkUp Wiki

Welcome to the central knowledge base for **ApkUp** — an open-source, privacy-first Android app updater built with Kotlin, Jetpack Compose, and Material 3 Expressive.

- 🟢 **User Guides & FAQs:** Every feature guide is written in plain language for everyday users, followed by practical **Frequently Asked Questions (FAQ)**.
- 💻 **Technical Overview:** A dedicated, comprehensive category for architecture, protocols, cryptography, CI/CD, testing, and Android internals.

---

## 🟢 User Guides & FAQs

### 1. 🌐 Update Sources, Repository Linking & Signature Protection
* **Main Guide:** [Update Sources, Repository Linking & Signature Protection](update-sources.md) — How Google Play, APKMirror, F-Droid/IzzyOnDroid, GitHub, GitLab, and Codeberg/Forgejo work together, how **Source Priority & Per-App Pinning** work, how **Signature Verification** protects you, and how to link custom repositories.
* **Specialized Guides:**
  * [📦 F-Droid & Index V2 Repositories](fdroid-index-v2.md)
  * [🔑 Configuring GitHub, GitLab & Codeberg Personal Access Tokens](tokens.md)

### 2. ⚡ Shizuku & Silent Background Installations
* **Main Guide:** [Shizuku & Silent Background Installations](shizuku-background-installation.md) — Why Android normally prompts for every update, how **Shizuku** enables one-tap batch updates and silent background installs without Root, and how the optional **Root Installer (`su`)** works.
* **Quick Setup:** [⚡ Setting up Shizuku](setting-up-shizuku.md) — Step-by-step walkthrough for Wireless Debugging (Android 11+), USB ADB, Root, and OEM battery settings.

### 3. 🛠️ App Management, Filters, Background Checks & Backup
* **Main Guide:** [App Management, Filters, Background Checks & Backup](app-management-and-backup.md) — Skipping versions vs. ignoring apps, Watchlist & **App Watcher Import**, searching/sorting, exporting APKs (`.apk` / `.apkm`), **Home Screen Updates Widget (Preview)**, **Background Update Time Windows**, **Configurable Rollback Backups**, **Self-Updater Beta Opt-In**, and JSON settings backup.
* **Accessibility Guide:** [♿ Accessibility & Accessible Mode](https://github.com/DurchEins/apkup/wiki/Accessibility-%26-Accessible-Mode) — One-tap Accessible UI, 48 dp touch targets, TalkBack support, and the high-contrast solar `Auto / Light / Dark` theme.
* **Migration Guide:** [🔄 Obtainium Migration & Sync Guide](obtainium-sync.md)

### 4. 🚨 Troubleshooting Source Errors & Error Codes
* **Main Guide:** [Troubleshooting Source Errors & Error Codes](troubleshooting.md) — Direct self-help for DNS/connection timeouts, HTTP 401/403/429 rate limits, APKMirror `404` & Cloudflare challenges, signature conflicts, and custom repository linking.

### 5. 📲 Installing Unknown Apps & ⚠️ Google’s 24h Lock
* **Sideloading:** [📲 Installing Unknown Apps](installing-unknown-apps.md) — Step-by-step instructions for Android’s *“Allow from this source”* permission.
* **Google’s 24h Lock:** [⚠️ Google’s 24h Lock & Keep Android Open](google-24h-lock.md) — How to permanently unlock *“Apps from unverified developers”* (*Allow indefinitely*) for the 2026/2027 Play Protect rollout.

---

## 💻 Technical Overview

Comprehensive architectural and engineering documentation for developers, contributors, and security reviewers:

1. 🌐 **[Update Sources, Networking & Parsing](https://github.com/DurchEins/apkup/wiki/Tech-Update-Sources,-Networking-&-Parsing)** — Multi-source aggregation, `gplayapi`, F-Droid Index-V2 (`RFC 7396`), APKMirror `.apkm` & Cloudflare handling, GitHub GraphQL & token isolation.
2. ⚡ **[Installation, Shizuku, Root & WorkManager](https://github.com/DurchEins/apkup/wiki/Tech-Installation,-Shizuku,-Root-&-WorkManager)** — `UpdateDownloadWorker`, 24h cache, `SessionInstaller` (`PackageInstaller`), Shizuku Binder IPC, `RootInstaller` (`su`), and rollback backups.
3. 🛡️ **[Security, Cryptography & Privacy](https://github.com/DurchEins/apkup/wiki/Tech-Security,-Cryptography-&-Privacy)** — `ApkIntegrityVerifier` (v2/v3 signatures, SHA-256, downgrade guard), Google Tink `AES-256-GCM`, Zip-Slip/decompression-bomb limits, Tapjacking & `AppJournal` redaction.
4. 🎨 **[UI, Accessibility & Website Architecture](https://github.com/DurchEins/apkup/wiki/Tech-UI,-Accessibility-&-Website-Architecture)** — Jetpack Compose Material 3 Expressive, `a11yAccessibleUi`, `AccessibleTypography`, `SolarScheduleCalculator`, Astro 9-language site & `check-a11y.mjs`.
5. 🚀 **[CI/CD, Single-Artifact Releases & Checksums](https://github.com/DurchEins/apkup/wiki/Tech-CI-CD,-Single-Artifact-Releases-&-Checksums)** — SemVer 2.0.0 CI pipeline, PR checks, `release.yml` canonical APK, `SHA256SUMS`, Self-Updater & mirrors.
6. 🏷️ **[Versioning Standard (Legacy vs. SemVer 2.0.0)](https://github.com/DurchEins/apkup/wiki/Tech-Versioning,-SemVer-2.0.0-&-Migration)** — Centralized `version.properties`, automatic short Git hash, `-build<N>` asset naming, Self-Updater precedence & two-stage Major rule.
7. 💾 **[Data Storage, Prefs, Backups, Logs & Widget](https://github.com/DurchEins/apkup/wiki/Tech-Data-Storage,-Prefs,-Backups,-Logs-&-Widget)** — `SecurePrefsStore` allowlist, `CachedUpdateSnapshot`, `ApkUpBackupConfig` schema, `AppJournal`, and `UpdatesWidgetProvider` (`RemoteViews` Preview).
8. 🧪 **[Build System, R8 & Automated Tests](https://github.com/DurchEins/apkup/wiki/Tech-Build-System,-R8-&-Automated-Tests)** — Gradle configuration, Debug vs. Release (R8), `PrefsTest`, `ChangelogBottomSheetTest`, `AccessibilityAuditTest`, and `lintDebug`.
9. 🌍 **[Localization, Weblate & Asset Pipeline](https://github.com/DurchEins/apkup/wiki/Tech-Localization,-Weblate-&-Assets)** — 9-locale `strings.xml` & `website/src/i18n/*.json` parity, Hosted Weblate, changelog asset parser, Fastlane & adaptive SVGs.

---

## 🔗 Quick Project Links
- 🏠 **[ApkUp GitHub Repository & README](https://github.com/DurchEins/apkup)**
- 📦 **[Latest Releases & Checksums](https://github.com/DurchEins/apkup/releases)**
- 🐛 **[Report a Bug or Request a Feature](https://github.com/DurchEins/apkup/issues)**
- 🛡️ **[Security Policy (`SECURITY.md`)](https://github.com/DurchEins/apkup/blob/main/SECURITY.md)**
- ♿ **[Accessibility Statement (`ACCESSIBILITY.md`)](https://github.com/DurchEins/apkup/blob/main/ACCESSIBILITY.md)**
- 💡 **GitHub ProTip:** Append `.patch` or `.diff` to any commit or PR URL (e.g. [PR #29 Diff](https://github.com/DurchEins/apkup/pull/29.diff)) for Git's plaintext view.
