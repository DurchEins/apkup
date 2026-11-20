# 📖 Welcome to the Official ApkUp Wiki

Welcome to the central knowledge base for **ApkUp** — an open-source, privacy-first Android app updater built with Kotlin, Jetpack Compose, and Material 3 Expressive.

Every main guide is structured into clear layers:
- 🟢 **Part 1 (Plain Language):** Everyday step-by-step instructions without technical jargon.
- 💻 **Part 2 (Technical Deep Dive):** Architecture, cryptography, Android Binder IPC, and security guarantees.
- ❓ **Part 3 (FAQ & Edge Cases):** Practical answers to common real-world questions.

---

## 🗺️ Recommended Reading Order

### 1. [🌐 Update Sources, Repository Linking & Signature Protection](https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection)
* **For Everyone:** How Google Play, APKMirror, F-Droid/IzzyOnDroid, GitHub, GitLab, and Codeberg/Forgejo work together, how global **Source Priority & Per-App Pinning** decide which source wins, how **Pre-Release Channels** and **Signature Verification** protect you, and how to link custom repositories.
* **For Experts:** Parallel multi-source aggregation, SHA-1/SHA-256 certificate verification, `.apkm` split extraction (Zip-Slip protection), Aurora dispenser HTTP 429 rotation, and 3-tier repository resolution.
* **Specialized Source Guides:**
  * [📦 F-Droid & Index V2 Repositories](https://github.com/DurchEins/apkup/wiki/F-Droid-&-Index-V2-Repositories) — Streaming `index-v2.json`, incremental diffs (`RFC 7396`), built-in presets, and TOFU certificate pinning.
  * [🔑 Configuring GitHub, GitLab & Codeberg Personal Access Tokens](https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens) — Raise GitHub limits from 60 to 5,000 requests/hour, enable GraphQL batching, access multi-level GitLab subgroups, and test tokens live.

---

### 2. [⚡ Shizuku & Silent Background Installations (plus Optional Root)](https://github.com/DurchEins/apkup/wiki/Shizuku-&-Background-Installation)
* **For Everyone:** Why Android normally prompts for every update, how **Shizuku** enables one-tap batch updates and silent background installs without Root, and how the optional **Root Installer (`su`)** fallback works.
* **Quick Setup:** [⚡ Setting up Shizuku](https://github.com/DurchEins/apkup/wiki/Setting-up-Shizuku) — Step-by-step walkthrough for Wireless Debugging (Android 11+), USB ADB, Root, and OEM battery settings (Xiaomi/HyperOS, Samsung, OnePlus).
* **For Experts:** Dual-path installation pipeline (`Shizuku` ➔ `su` ➔ `PackageInstaller`), `pm install-create -r -i com.android.vending`, foreground services, and Android 14+ BAL resilience.

---

### 3. [🛠️ App Management, Filters, Background Checks & Backup](https://github.com/DurchEins/apkup/wiki/App-Management,-Filters,-Background-Checks-&-Backup)
* **For Everyone:** Skipping versions vs. ignoring apps, Watchlist & **App Watcher Import**, searching/sorting, exporting APKs (`.apk` / `.apkm`), **Background Update Time Windows**, **Rollback Backups**, **ApkUp Self-Updater Beta Opt-In**, and transferring settings via JSON backup.
* **For Experts:** Package exclusion vs. `versionCode` pinning, Android `WorkManager` scheduling, persistent update snapshot cache, offline sanitized diagnostic logs, and Storage Access Framework (SAF) JSON security.
* **Migration Guide:** [🔄 Obtainium Migration & Sync Guide](https://github.com/DurchEins/apkup/wiki/Obtainium-Migration-&-Sync-Guide) — Import Obtainium JSON exports with interactive preview, repo bindings, F-Droid repos, and Watchlist candidates.

---

### 4. [🚨 Troubleshooting Source Errors & Error Codes](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors)
* **Instant Self-Help:** Direct solutions for every error badge and diagnostic code in ApkUp:
  * [DNS Resolution Failed & Connection Timeouts](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#dns-resolution-failed)
  * [Authentication & Rate Limits (`HTTP 401 / 403 / 429`)](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#auth-and-rate-limits)
  * [APKMirror `404 Not Found` & Server Errors (`5xx`)](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#server-errors)
  * [APKMirror Cloudflare Challenges & Browser Fallback](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#apkmirror-cloudflare)
  * [Signature Conflicts (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#signature-conflicts)
  * [Custom Repository Linking & No Matching APK Asset](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors#custom-repo-linking)

---

## 🔗 Quick Project Links
- 🏠 **[ApkUp GitHub Repository & README](https://github.com/DurchEins/apkup)**
- 📦 **[Latest Releases & Checksums](https://github.com/DurchEins/apkup/releases)**
- 🐛 **[Report a Bug or Request a Feature](https://github.com/DurchEins/apkup/issues)**
- 🛡️ **[Security Policy (`SECURITY.md`)](https://github.com/DurchEins/apkup/blob/main/SECURITY.md)**
