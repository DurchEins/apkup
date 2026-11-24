---
description: "Import Obtainium JSON exports into ApkUp with interactive preview for repo links, regex filters, F-Droid repos, and watchlist items."
icon: arrows-rotate
---

# 🔄 Obtainium Migration & Sync Guide

ApkUp features native integration with [Obtainium](https://github.com/ImranR98/Obtainium) JSON exports so you can migrate your tracked apps, custom repository bindings, regex asset filters, and F-Droid repositories in seconds.

---

## 📑 Quick Navigation

* [1. Why Migrate from Obtainium to ApkUp?](#1-why-migrate-from-obtainium-to-apkup)
* [2. How to Export from Obtainium](#2-how-to-export-from-obtainium)
* [3. How to Import into ApkUp (Interactive Preview)](#3-how-to-import-into-apkup-interactive-preview)
* [4. Supported Source Types & Mapping](#4-supported-source-types--mapping)
* [5. Signature Security & Overwrite Protection](#5-signature-security--overwrite-protection)
* [6. ApkUp Settings Backup & Restore](#6-apkup-settings-backup--restore)

---

## 1. Why Migrate from Obtainium to ApkUp?

| Feature | Obtainium | ApkUp |
| :--- | :--- | :--- |
| **Multi-Source Aggregation** | Scrapes a single configured URL | Combines Google Play, APKMirror, IzzyOnDroid, F-Droid, GitHub, GitLab & Codeberg |
| **Source Priority & Fallback** | Manual link per app | Global **Source Priority** order + per-app **Source Pinning** with automatic fallback |
| **Cryptographic Signature Defense** | Basic checks | Strict SHA-1 / SHA-256 certificate matching blocks mismatched updates |
| **Direct APKMirror Downloads** | Browser-dependent | In-app resolver with Cloudflare WebView & `.apkm` split bundle extraction |
| **Silent Background Updates** | Notification-driven | Native Shizuku IPC + optional Root (`su`), quiet hours time window & WorkManager |
| **Native UI** | Flutter | 100% native Kotlin / Jetpack Compose Material 3 Expressive |

---

## 2. How to Export from Obtainium

1. Open **Obtainium** on your Android device.
2. Tap **Settings ➔ Import / Export**.
3. Tap **Export Apps to JSON File** and save `obtainium_export.json` (e.g. to *Downloads*).

---

## 3. How to Import into ApkUp (Interactive Preview)

1. Open **ApkUp ➔ Setup ➔ Backup & Restore ➔ Obtainium Import**.
2. Select your `obtainium_export.json` file in Android's file picker.
3. Review everything in the **Interactive Preview Sheet** before applying:
   * **Repo Links for Installed Apps:** Matches installed package IDs to GitHub, GitLab, Codeberg/Forgejo, and `apkFilterRegEx` rules.
   * **Third-Party F-Droid Repositories:** Detects custom HTTPS `/repo` or `/archive` URLs (including 64-character `?fingerprint=` pins) and imports them into ApkUp's unified F-Droid manager.
   * **Watch Uninstalled Apps (Opt-In):** Lists uninstalled apps from the export (unchecked by default so you only add what you explicitly select to your **Watchlist**).
4. Tap **Import selected**, then pull to refresh on the **Updates** tab!

---

## 4. Supported Source Types & Mapping

* **GitHub Repositories:** Mapped to ApkUp's GitHub resolver (standard releases, pre-releases, and asset regex matching).
* **GitLab Projects:** Mapped to ApkUp's GitLab resolver (including multi-level subgroups and self-hosted hosts).
* **Codeberg & Forgejo Repositories:** Mapped to ApkUp's Codeberg/Forgejo v1 resolver (`codeberg.org` and self-hosted Forgejo).
* **F-Droid & IzzyOnDroid Links:** Mapped to ApkUp's Index V2 streaming engine and F-Droid repository list.
* **APK Filter Regex (`apkFilterRegEx`):** Automatically preserved from Obtainium entries.

---

## 5. Signature Security & Overwrite Protection

Even when a custom repository offers a higher `versionCode`, ApkUp extracts the signing certificate from the downloaded APK and verifies it against the currently installed app before installation. Mismatches are flagged immediately to prevent `INSTALL_FAILED_UPDATE_INCOMPATIBLE` errors.

---

## 6. ApkUp Settings Backup & Restore

To back up or transfer your **ApkUp** configuration (including custom repo links, ignored apps, source priority, and preferences) to another phone via Android's Storage Access Framework (SAF), follow the full guide:
👉 **[Guide: Backing up settings and restoring them on a new phone](app-management-and-backup.md#16-guide-backing-up-settings-and-restoring-them-on-a-new-phone)**

{% hint style="info" %}
Personal Access Tokens are stored encrypted on-device via Google Tink `AES-256-GCM` and are never exported in plaintext JSON backups.
{% endhint %}
