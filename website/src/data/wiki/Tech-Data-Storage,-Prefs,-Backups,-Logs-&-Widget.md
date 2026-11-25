# 💾 Technical Overview: Data Storage, Prefs, Backups, Logs & Widget

This page documents ApkUp's encrypted preference storage, JSON backup schema, sanitized diagnostic logging, and the Home Screen Updates Widget (Preview).

---

## 1. Encrypted Preferences (`Prefs.kt` & `SecurePrefsStore.kt`)

- **Slot-Based Abstraction:** `Prefs.kt` exposes typed `PrefSlot<T>` and `StateFlow` accessors backed by `TinkSecurePrefsBackend` (`AES-256-GCM`).
- **Strict Allowlist:** `SecurePrefsStore.kt` maintains an explicit key allowlist so unknown keys cannot be persisted or restored from untrusted sources.
- **Snapshot Cache (`CachedUpdateSnapshot`):** Stores the last discovered update list and `timestampMillis` locally so the Updates tab, notifications, and the home screen widget can render immediately without re-querying network sources.

---

## 2. Settings Backup & Restore (`ApkUpBackupConfig`)

- **Versioned JSON Schema (`CURRENT_SCHEMA_VERSION`):** `SettingsViewModel` exports and imports `ApkUpBackupConfig` via Android's Storage Access Framework (SAF), preserving source toggles, source priority, pinned sources, ignored apps/versions (`IgnoredVersionEntry` deduplicated by `id_packageName_versionCode`), pre-release preferences, rollback backup limits, and theme/accessibility settings.
- **Sanitized Import:** Imported values are clamped and validated (e.g. `ignoredApps`, `ignoredVersions`, numeric bounds) before writing to `Prefs`.

---

## 3. Diagnostic Logs (`AppJournal`) & Update History

- **Offline Diagnostic Export:** Captures structured runtime events, source health states, and installer outcomes without network telemetry, automatically redacting API tokens and credentials prior to SAF export.
- **Persistent Update History:** Tracks completed installations and exposes rollback links directly from the Update History sheet.

---

## 4. Home Screen Updates Widget (`UpdatesWidgetProvider` — Preview)

- **Preview Status:** Marked with a `Preview` pill badge in `widget_updates.xml` (`@string/widget_updates_preview_badge`) and `(Preview)` across widget descriptions.
- **RemoteViews Constraints:** Uses `RemoteViews`-compatible layouts (`LinearLayout`, `FrameLayout`, `ImageView`, `TextView`, `ImageButton`, `ProgressBar`) — avoiding unsupported views such as `<View>` (enforced by `lintDebug` `RemoteViewLayout`).
- **Live Sync & Quick Refresh:** Reads `CachedUpdateSnapshot` + `Prefs` visibility rules (`countVisibleWidgetUpdates`), opens `MainActivity` on tap (`UpdatesNotification.UpdateAction`), and dispatches `ACTION_REFRESH_WIDGET` to trigger `UpdatesWorker.launchImmediate(WorkManager)`.

---

> 📖 **User-friendly guide:** [App Management, Filters, Background Checks & Backup](https://github.com/DurchEins/apkup/wiki/App-Management,-Filters,-Background-Checks-&-Backup)
