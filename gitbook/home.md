---
description: "Central knowledge base for ApkUp: multi-source Android updates, Shizuku automation, SHA-256 verification, and troubleshooting."
icon: book-open
---

# 📖 Welcome to the Official ApkUp Wiki

Welcome to the central knowledge base for **ApkUp** — an open-source, privacy-first Android app updater built with Kotlin, Jetpack Compose, and Material 3 Expressive.

Every main guide is structured into clear layers:
- 🟢 **Part 1 (Plain Language):** Everyday step-by-step instructions without technical jargon.
- 💻 **Part 2 (Technical Deep Dive):** Architecture, cryptography, Android Binder IPC, and security guarantees.
- ❓ **Part 3 (FAQ & Edge Cases):** Practical answers to common real-world questions.

---


<table data-view="cards">
<thead><tr><th>Guide</th><th>What you will learn</th><th data-hidden data-card-target data-type="content-ref">Link</th></tr></thead>
<tbody>
<tr><td><strong>🌐 Update Sources & Security</strong></td><td>Play Store, F-Droid Index V2, APKMirror, GitHub/GitLab/Codeberg, Priority &amp; Signatures</td><td><a href="update-sources.md">update-sources.md</a></td></tr>
<tr><td><strong>⚡ Setting up Shizuku</strong></td><td>Silent background updates &amp; one-tap batch installs without Root</td><td><a href="setting-up-shizuku.md">setting-up-shizuku.md</a></td></tr>
<tr><td><strong>🛡️ SHA-256 &amp; Signatures</strong></td><td>Verify APK checksums on Windows, Linux &amp; macOS + automatic v2/v3 checks</td><td><a href="sha256-signature-verification.md">sha256-signature-verification.md</a></td></tr>
<tr><td><strong>🛠️ Management &amp; Backups</strong></td><td>Watchlist, quiet hours, rollback backups &amp; encrypted settings export</td><td><a href="app-management-and-backup.md">app-management-and-backup.md</a></td></tr>
<tr><td><strong>🔄 Obtainium Migration</strong></td><td>Import Obtainium JSON exports with interactive preview in seconds</td><td><a href="obtainium-sync.md">obtainium-sync.md</a></td></tr>
<tr><td><strong>🚨 Troubleshooting</strong></td><td>Quick fixes for DNS, 401/403/429 rate limits, Cloudflare &amp; signature errors</td><td><a href="troubleshooting.md">troubleshooting.md</a></td></tr>
</tbody>
</table>
## 🗺️ Recommended Reading Order

### 1. [🌐 Update Sources, Repository Linking & Signature Protection](update-sources.md)
* **For Everyone:** How Google Play, APKMirror, F-Droid/IzzyOnDroid, GitHub, GitLab, and Codeberg/Forgejo work together, how global **Source Priority & Per-App Pinning** decide which source wins, how **Pre-Release Channels** and **Signature Verification** protect you, and how to link custom repositories.
* **For Experts:** Parallel multi-source aggregation, SHA-1/SHA-256 certificate verification, `.apkm` split extraction (Zip-Slip protection), direct `gplayapi 4.0.0` session & HTTP 429 rotation, and 3-tier repository resolution.
* **Specialized Source Guides:**
  * [📦 F-Droid & Index V2 Repositories](fdroid-index-v2.md) — Streaming `index-v2.json`, incremental diffs (`RFC 7396`), built-in presets, and TOFU certificate pinning.
  * [🔑 Configuring GitHub, GitLab & Codeberg Personal Access Tokens](tokens.md) — Raise GitHub limits from 60 to 5,000 requests/hour, enable GraphQL batching, access multi-level GitLab subgroups, and test tokens live.

---

### 2. [⚡ Shizuku & Silent Background Installations (plus Optional Root)](shizuku-background-installation.md)
* **For Everyone:** Why Android normally prompts for every update, how **Shizuku** enables one-tap batch updates and silent background installs without Root, and how the optional **Root Installer (`su`)** fallback works.
* **Quick Setup:** [⚡ Setting up Shizuku](setting-up-shizuku.md) — Step-by-step walkthrough for Wireless Debugging (Android 11+), USB ADB, Root, and OEM battery settings (Xiaomi/HyperOS, Samsung, OnePlus).
* **For Experts:** Dual-path installation pipeline (`Shizuku` ➔ `su` ➔ `PackageInstaller`), `pm install-create -r -i com.android.vending`, foreground services, and Android 14+ BAL resilience.

---

### 3. [🛠️ App Management, Filters, Background Checks & Backup](app-management-and-backup.md)
* **For Everyone:** Skipping versions vs. ignoring apps, Watchlist & **App Watcher Import**, searching/sorting, exporting APKs (`.apk` / `.apkm`), **Background Update Time Windows**, **Configurable Rollback Backups**, **ApkUp Self-Updater Beta Opt-In**, and transferring settings via JSON backup.
* **For Experts:** Package exclusion vs. `versionCode` pinning, Android `WorkManager` scheduling, persistent update snapshot cache, offline sanitized diagnostic logs, and Storage Access Framework (SAF) JSON security.
* **Migration Guide:** [🔄 Obtainium Migration & Sync Guide](obtainium-sync.md) — Import Obtainium JSON exports with interactive preview, repo bindings, F-Droid repos, and Watchlist candidates.

---

### 4. [🚨 Troubleshooting Source Errors & Error Codes](troubleshooting.md)
* **Instant Self-Help:** Direct solutions for every error badge and diagnostic code in ApkUp:
  * [DNS Resolution Failed & Connection Timeouts](troubleshooting.md#dns-resolution-failed)
  * [Authentication & Rate Limits (`HTTP 401 / 403 / 429`)](troubleshooting.md#auth-and-rate-limits)
  * [APKMirror `404 Not Found` & Server Errors (`5xx`)](troubleshooting.md#server-errors)
  * [APKMirror Cloudflare Challenges & Browser Fallback](troubleshooting.md#apkmirror-cloudflare)
  * [Signature Conflicts (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)](troubleshooting.md#signature-conflicts)
  * [Custom Repository Linking & No Matching APK Asset](troubleshooting.md#custom-repo-linking)

---

### 5. [📲 Installing Unknown Apps](installing-unknown-apps.md) & [⚠️ Google’s 24h Lock](google-24h-lock.md)
* **Installing Unknown Apps:** Step-by-step instructions for Android’s classic *“Allow from this source”* permission plus power-user installation via **Shizuku**, **ADB**, and **Root (`su`)**.
* **Google’s 24h Lock & Keep Android Open:** How to permanently unlock *“Apps from unverified developers”* (*Allow indefinitely*, avoiding the 7-day reset trap) for the 2026/2027 Play Protect rollout, and why ApkUp supports [Keep Android Open](https://keepandroidopen.org).

---

## 🔗 Quick Project Links
- 🏠 **[ApkUp GitHub Repository & README](https://github.com/DurchEins/apkup)**
- 📦 **[Latest Releases & Checksums](https://github.com/DurchEins/apkup/releases)**
- 🐛 **[Report a Bug or Request a Feature](https://github.com/DurchEins/apkup/issues)**
- 🛡️ **[Security Policy (`SECURITY.md`)](https://github.com/DurchEins/apkup/blob/main/SECURITY.md)**
