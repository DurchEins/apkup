# ⚡ Technical Overview: Installation, Shizuku, Root & WorkManager

This page covers ApkUp's download orchestration, background scheduling, multi-path installation pipeline (`PackageInstaller`, `Shizuku`, `su`), and rollback architecture.

---

## 1. Download Orchestration (`WorkManager` & `DownloadCoordinator`)

- **User-Initiated Data Transfer (`UpdateDownloadWorker`):** Interactive single updates, multi-select batch updates, and *Install All* runs execute through AndroidX `WorkManager` with live progress (`DownloadTaskState`), smoothed transfer speed, and batch ETA calculation.
- **24-Hour Private Cache:** Verified APK/split files reside in private internal app storage (`cacheDir`) so cached updates (`readyUpdateIds`) can be installed immediately without re-downloading.
- **Save APK / `.apks` Export:** Exports verified single `.apk` files or `.apks` split bundles to `Download/ApkUp/` or a user-configured Storage Access Framework (SAF) directory.

---

## 2. Dual/Triple-Path Installation Pipeline

Before any installer backend is invoked, `ApkIntegrityVerifier` validates SHA-256 checksums (when `expectedSha256` is supplied) and verifies APK Signature Scheme v2/v3 certificates against the installed app, blocking mismatches and unintended version downgrades.

1. **Standard `SessionInstaller` (`PackageInstaller`):**
   - Stages single or split APKs into `PackageInstaller.Session` (`createSession` → `openWrite` → `fsync` → `commit`), prompting the Android system confirmation sheet.
   - Handles Android 14+ Background Activity Launch (BAL) restrictions gracefully with explicit user prompt fallbacks.
2. **Shizuku Binder IPC (`ShizukuAutoUpdateWorker`):**
   - Uses Shizuku's scoped ADB binder privileges to execute `pm install-create` / `install-write` / `install-commit` without Root, enabling silent batch updates and unattended background installs.
   - Checks live Shizuku availability and permission state on resume and before background execution.
3. **Optional Root Installer (`RootInstaller` via `su`):**
   - Performs explicit pre-checks on staged package paths and executes `pm install` via `su` when Root mode is enabled.
   - Supports in-place downgrade (`-r -d`) for rollback restoration when Root is available.

---

## 3. Background Checks & Rollback Backups

- **Scheduled Background Checks (`UpdatesWorker`):** Respects user-configured **Wi-Fi only**, **While charging**, and active **Time Window** constraints with catch-up scheduling when a window opens.
- **Consolidated Summary Notifications:** Groups discovered or installed updates cleanly without notification spam, and triggers `UpdatesWidgetProvider.notifyWidgetDataChanged` to keep the home screen widget synchronized.
- **Configurable Rollback Backups:** Before updating, ApkUp can archive installed base + split APKs within user-defined storage caps (up to 2 GB) and retention limits (7–30 days or unlimited), exposing one-tap cleanup and live storage usage in Setup.

---

> 📖 **User-friendly guide:** [Shizuku & Silent Background Installations](https://github.com/DurchEins/apkup/wiki/Shizuku-&-Background-Installation)
