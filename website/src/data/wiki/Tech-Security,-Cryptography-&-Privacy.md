# 🛡️ Technical Overview: Security, Cryptography & Privacy

This page documents ApkUp's defense-in-depth security model, cryptographic verification layers, archive hardening, and zero-telemetry guarantees.

---

## 1. Pre-Install APK Integrity & Signature Verification

- **`ApkIntegrityVerifier`:**
  - **Signing Certificate Match:** Extracts APK Signature Scheme v2/v3 certificates from downloaded APKs (and split bases) and compares them against the currently installed app's signing certificate before passing files to `PackageInstaller`, Shizuku, or Root (`su`).
  - **Downgrade Guard:** Blocks accidental `versionCode` downgrades during normal update flows.
  - **SHA-256 Digest Validation (`verifyFileSha256`):** Validates normalized 64-char hex SHA-256 digests whenever `expectedSha256` is supplied by the source (mandatory for F-Droid/IzzyOnDroid, supported for GitHub/Codeberg releases and Self-Updater `SHA256SUMS`).

---

## 2. Encrypted Storage & Network Hardening

- **Google Tink (`AES-256-GCM`) + Android Keystore:** `TinkSecurePrefsBackend` and `SecurePrefsStore` encrypt preferences and optional GitHub/GitLab/Codeberg Personal Access Tokens at rest using hardware-backed Android Keystore keys.
- **Allowlist Enforcement:** `SecurePrefsStore` enforces an explicit key allowlist to prevent unexpected or malformed preference injection during backup imports.
- **HTTPS-Only Transport:** Cleartext HTTP is strictly disabled (`cleartextTrafficPermitted="false"`).
- **Host-Isolated Credentials:** Personal Access Tokens are scoped to their configured host and stripped automatically across cross-host HTTP redirects.
- **Zero Cloud Backups & Zero Telemetry:** `android:allowBackup="false"` prevents cloud/ADB extraction of encrypted prefs; ApkUp contains zero analytics SDKs or developer tracking servers, and first-launch onboarding ensures zero external network calls occur before source consent.

---

## 3. Archive, UI & Process Hardening

- **`.apkm` Split-Bundle Hardening:** Archive extraction validates canonical entry paths to block **Zip-Slip** directory traversal and enforces hard per-entry and total **decompression-bomb** byte limits inside private internal storage.
- **Tapjacking Protection:** Sensitive confirmation controls filter obscured touches (`filterTouchesWhenObscured`).
- **Credential-Scrubbed Logs (`AppJournal`):** Diagnostic log exports automatically redact tokens, authorization headers, and sensitive query parameters before saving.

---

> 🛡️ **Official Policy:** [`SECURITY.md`](https://github.com/DurchEins/apkup/blob/main/SECURITY.md) • **Privacy Policy:** [`PRIVACY.md`](https://github.com/DurchEins/apkup/blob/main/PRIVACY.md)
