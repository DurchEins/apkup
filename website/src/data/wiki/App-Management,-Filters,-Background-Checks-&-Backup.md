# 🛠️ App Management, Filters, Background Checks & Backup/Restore in ApkUp

This guide explains how to skip faulty updates, ignore apps, track uninstalled apps via your Watchlist, export APKs, schedule background update windows, use rollback backups, opt into ApkUp beta updates, and transfer your configuration between devices.

---

<a id="-table-of-contents"></a>
## 📑 Table of Contents
1. [🟢 Part 1: Explained Simply for Everyone](#-part-1-explained-simply-for-everyone)
   - [1.1 "Skip this version" vs. "Ignore app permanently"](#11-skip-this-version-vs-ignore-app-permanently)
   - [1.2 Search, Sorting, Watchlist & App Watcher Import](#12-search-sorting-watchlist--app-watcher-import)
   - [1.3 Saving APKs, Batch ETA & Offline Diagnostic Logs](#13-saving-apks-batch-eta--offline-diagnostic-logs)
   - [1.4 Background Checks, Time Window, Pre-Releases & Rollbacks](#14-background-checks-time-window-pre-releases--rollbacks)
   - [1.5 Showing or Hiding System Apps](#15-showing-or-hiding-system-apps)
   - [1.6 Guide: Backing up settings and restoring them on a new phone](#16-guide-backing-up-settings-and-restoring-them-on-a-new-phone)
2. [💻 Part 2: Technical Documentation for Experts & Power Users](#-part-2-technical-documentation-for-experts--power-users)
   - [2.1 Filter Architecture: Package-Wide Exclusion vs. Version Pinning](#21-filter-architecture-package-wide-exclusion-vs-version-pinning)
   - [2.2 Battery-Friendly Background Scheduling via Android `WorkManager`](#22-battery-friendly-background-scheduling-via-android-workmanager)
   - [2.3 Persistent Update Snapshot Cache for Instant App Startup](#23-persistent-update-snapshot-cache-for-instant-app-startup)
   - [2.4 Structure & Privacy of the JSON Backup File (SAF)](#24-structure--privacy-of-the-json-backup-file-saf)
3. [❓ Part 3: Frequently Asked Questions (FAQ)](#-part-3-frequently-asked-questions-faq)
   - [3.1 When should I use "Skip this version" vs. "Ignore app permanently"?](#31-when-should-i-use-skip-this-version-vs-ignore-app-permanently)
   - [3.2 Why should I be careful with System Apps?](#32-why-should-i-be-careful-with-system-apps)
   - [3.3 Do background update checks drain the battery?](#33-do-background-update-checks-drain-the-battery)
   - [3.4 Why am I not receiving background notifications?](#34-why-am-i-not-receiving-background-notifications)
   - [3.5 Why does a skipped version reappear after a hotfix?](#35-why-does-a-skipped-version-reappear-after-a-hotfix)
   - [3.6 Does the JSON backup include APK files or tokens, and can I edit it?](#36-does-the-json-backup-include-apk-files-or-tokens-and-can-i-edit-it)
4. [🔗 Part 4: Official Links & Further Resources](#-part-4-official-links--further-resources)

---

<a id="-part-1-explained-simply-for-everyone"></a>
## 🟢 Part 1: Explained Simply for Everyone

<a id="11-skip-this-version-vs-ignore-app-permanently"></a>
### 1.1 "Skip this version" vs. "Ignore app permanently"
| Feature | What happens? | When will you be notified again? | How to undo? |
| :--- | :--- | :--- | :--- |
| **⏭️ Skip this version** | Hides only **that specific build** (e.g. `2.4.1`). | Automatically as soon as a **newer version** (`2.4.2`) is released. | **Setup ➔ Manage skipped versions** |
| **🙈 Ignore app permanently** | Excludes the app from all future update checks. | Never — until you unhide it. | **Setup ➔ Manage ignored apps** or **Apps ➔ Ignored** chip |

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="12-search-sorting-watchlist--app-watcher-import"></a>
### 1.2 Search, Sorting, Watchlist & App Watcher Import
* **Instant Search & Sorting:** Both **Updates** and **Apps** offer live search and flexible sorting (*Name A–Z / Z–A*, *Source*, *Newest Release Date* in Updates, *Recently Updated* in Apps) with automatic scroll-to-top.
* **Watchlist (Uninstalled Apps):** Tap **`+`** in the **Apps** top bar to follow an uninstalled app by package ID (and optional GitHub/GitLab/Codeberg URL). Tracked entries appear under the **Watched** chip and surface new releases in **Updates** with a **`Watched · New`** badge and a direct **Install** button.
* **Import from App Watcher:** Open **Setup ➔ Import & Export ➔ Import from App Watcher** to import uninstalled apps (including their last-seen version) straight into your Watchlist.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="13-saving-apks-batch-eta--offline-diagnostic-logs"></a>
### 1.3 Saving APKs, Batch ETA & Offline Diagnostic Logs
* **Save APK without Installing:** Tap **Save APK** inside any update card to export standalone `.apk` files or `.apks` split archives into `Download/ApkUp/` (or a custom SAF folder via **Setup ➔ Saved APK folder**). File/archive/SHA-256 integrity is verified even if the installed app has a different signing certificate.
* **Overall Batch Progress & ETA:** Batch updates and exports display combined progress percentage, speed, and estimated time remaining.
* **Offline Diagnostic & Update Log:** Under **Setup ➔ Advanced Settings ➔ Diagnostic & Update Log**, export (`.txt`) or share a sanitized report. Optional process `logcat` is **disabled by default (opt-in)** and all tokens/credentials (`Bearer`, `ghp_`, `glpat-`, `PRIVATE-TOKEN`, Aurora `Auth=`/`aas_et/`) are redacted automatically.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="14-background-checks-time-window-pre-releases--rollbacks"></a>
### 1.4 Background Checks, Time Window, Pre-Releases & Rollbacks
* **Background Check Interval:** Under **Setup ➔ Automatic Checks & Schedule**, choose **6 hours**, **12 hours**, **24 hours** (recommended), **48 hours**, or **7 days**.
* **Background Update Time Window:** Enable **Background Update Time Window** to either pause checks & auto-updates during quiet hours (e.g. `22:00 – 07:00`) or restrict them to an active window. Any run that falls outside the window automatically schedules a one-time catch-up at the next window start.
* **Pre-Release Channels (Apps):** Open **Setup ➔ Include Pre-Releases** to enable **Beta**, **RC**, **Alpha**, or **Nightly** channels globally, or override pre-releases per app.
* **ApkUp Self-Updater Beta Opt-In:** Under **Setup ➔ About ➔ Receive beta updates** (disabled by default), you can opt into pre-release builds of ApkUp itself. Pre-releases show a **Beta / Pre-Release** badge in the update dialog and enforce identical SHA-256 and certificate checks; turning it off automatically returns you to stable with the next regular release.
* **Automatic Rollback Backups & Update History:** Keep backups of previous APKs in private storage (`2 GB` LRU limit) before updating so you can roll back anytime from **Update History** (in-place via Root `-r -d`, or guided two-step rollback without Root).

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="15-showing-or-hiding-system-apps"></a>
### 1.5 Showing or Hiding System Apps
ApkUp hides pre-installed system apps by default so your list stays clean. To include system components (such as *Android System WebView*), enable **Setup ➔ Include System Apps**.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="16-guide-backing-up-settings-and-restoring-them-on-a-new-phone"></a>
### 1.6 Guide: Backing up settings and restoring them on a new phone
* **📤 Export Configuration:** **ApkUp ➔ Setup ➔ Backup & Restore ➔ Export Configuration** ➔ save `apkup-backup.json` to Downloads or cloud storage.
* **📥 Import Configuration:** **ApkUp ➔ Setup ➔ Backup & Restore ➔ Import Configuration** ➔ select your `.json` file. All sources, custom source priority, pinned apps, filters, pre-release settings, and repo links are restored immediately.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="-part-2-technical-documentation-for-experts--power-users"></a>
## 💻 Part 2: Technical Documentation for Experts & Power Users

<a id="21-filter-architecture-package-wide-exclusion-vs-version-pinning"></a>
### 2.1 Filter Architecture: Package-Wide Exclusion vs. Version Pinning
1. **Package-Wide Exclusion (Ignore App):** Applied **before** network requests are dispatched — reduces payload size, saves GitHub API quota, and preserves privacy.
2. **Version-Specific Pinning (Skip Version):** Stores `(packageName, version, versionCode)` and expires automatically as soon as any source delivers a strictly higher `versionCode`.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="22-battery-friendly-background-scheduling-via-android-workmanager"></a>
### 2.2 Battery-Friendly Background Scheduling via Android `WorkManager`
ApkUp delegates background checks to Android **`WorkManager`** (`NetworkType.CONNECTED`), batching work into OS maintenance windows (Doze / App Standby) with atomic `.part` download staging.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="23-persistent-update-snapshot-cache-for-instant-app-startup"></a>
### 2.3 Persistent Update Snapshot Cache for Instant App Startup
After each scan, ApkUp stores a local **Update Snapshot** in private app storage and reconciles it against live `PackageManager` state on launch so the **Updates** tab populates instantaneously without forcing a redundant online scan.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="24-structure--privacy-of-the-json-backup-file-saf"></a>
### 2.4 Structure & Privacy of the JSON Backup File (SAF)
ApkUp uses Android's **Storage Access Framework (SAF)** with zero broad storage permissions. The exported JSON contains:
- Enabled update sources, source priority order, pinned app sources, F-Droid repositories, and watched apps
- Ignored package names, skipped versions, and custom GitHub/GitLab/Codeberg bindings (validated on import for `https://`, allowed hosts, and regex limits)
- Theme, display, update window, pre-release channels, rollback backup toggle, self-updater beta opt-in (`includeSelfUpdatePreReleases`, safely defaulting to `false` on older backups), and background check preferences

*(Note: Personal Access Tokens remain encrypted on-device in Google Tink `AES-256-GCM` storage and are never exported in plaintext backups; importing a backup also resets `useRootInstaller` to `false` with a security notice.)*

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="-part-3-frequently-asked-questions-faq"></a>
## ❓ Part 3: Frequently Asked Questions (FAQ)

<a id="31-when-should-i-use-skip-this-version-vs-ignore-app-permanently"></a>
### 3.1 When should I use "Skip this version" vs. "Ignore app permanently"?
Use **Skip this version** for one buggy release you want to jump past; use **Ignore app permanently** when you intentionally want to stay on an older or customized build forever.

<a id="32-why-should-i-be-careful-with-system-apps"></a>
### 3.2 Why should I be careful with System Apps?
OEM system apps (Phone, Camera, Gallery, Launcher) are often tied to specific device models and firmware. Updating them from generic mirror builds can cause crashes.

<a id="33-do-background-update-checks-drain-the-battery"></a>
### 3.3 Do background update checks drain the battery?
**No.** `WorkManager` runs brief 3–5 second checks inside existing OS network wakeups.

<a id="34-why-am-i-not-receiving-background-notifications"></a>
### 3.4 Why am I not receiving background notifications?
1. Check **Android Settings ➔ Apps ➔ ApkUp ➔ Notifications** (Android 13+ permission).
2. Set **Android Settings ➔ Apps ➔ ApkUp ➔ Battery** to **Unrestricted** (especially on Xiaomi, Samsung, and OnePlus).

<a id="35-why-does-a-skipped-version-reappear-after-a-hotfix"></a>
### 3.5 Why does a skipped version reappear after a hotfix?
"Skip this version" tracks the internal `versionCode`. If the developer ships a hotfix with the same display name (e.g. `2.1.0`) but a higher `versionCode`, ApkUp rightly surfaces the newer build.

<a id="36-does-the-json-backup-include-apk-files-or-tokens-and-can-i-edit-it"></a>
### 3.6 Does the JSON backup include APK files or tokens, and can I edit it?
No — the `.json` file is only a few KB of settings (no APKs, no secret tokens). You can edit it safely in any text editor; just avoid trailing commas (`,`) before `}` or `]`.

---

<a id="-part-4-official-links--further-resources"></a>
## 🔗 Part 4: Official Links & Further Resources

- 🔋 **[Don't Kill My App!](https://dontkillmyapp.com/)** — OEM guides to keep background checks reliable.
- ⚙️ **[Android WorkManager Docs](https://developer.android.com/topic/libraries/architecture/workmanager)** — Official background scheduling architecture.
- 🔄 **[Obtainium Migration & Sync Guide](https://github.com/DurchEins/apkup/wiki/Obtainium-Migration-&-Sync-Guide)** — Migrate your Obtainium JSON export into ApkUp.

[⬆️ Back to Table of Contents](#-table-of-contents)
