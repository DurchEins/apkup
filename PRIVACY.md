# Privacy Policy for ApkUp

**Last Updated:** November 13, 2026

ApkUp is an open-source, client-side Android application developed by DurchEins. This Privacy Policy explains how ApkUp handles data and respects user privacy.

---

### 1. No Developer Backend, Analytics, or Ads
ApkUp is designed with a strict **Privacy by Design** philosophy:
- **No Developer Servers:** There is no ApkUp backend, proxy server, or developer database logging user queries or IP addresses.
- **No User Accounts Required:** ApkUp does not require registration, login, or user profiles.
- **No Analytics or Telemetry:** ApkUp contains zero analytics SDKs, crash trackers, or behavioral telemetry (no Google Firebase, no Crashlytics, no Sentry, no Mixpanel).
- **No Advertisements:** ApkUp is completely free of ads, tracking banners, and ad networks.
- **No Hardware Identifiers:** ApkUp never collects or transmits IMEI numbers, hardware serial numbers, MAC addresses, or Advertising IDs.

---

### 2. Explicit Source Consent & Network Activity
On first launch, ApkUp presents a **Choose Update Sources** consent dialog. No external network checks are performed until you choose which sources to enable, and you can toggle any source on or off at any time in Settings:

- **F-Droid & IzzyOnDroid (Signed Repository Indexes):** Downloads signed repository index archives (`index-v1.jar` / `entry.json`) via HTTPS from `f-droid.org`, `apt.izzysoft.de`, or custom HTTPS F-Droid mirrors you add, and compares versions locally on your device. Your list of installed apps is **not** transmitted to F-Droid or IzzyOnDroid.
- **GitHub, GitLab & Codeberg / Forgejo:** Queries public release endpoints (`api.github.com`, `gitlab.com`, `codeberg.org`, or user-configured self-hosted GitLab/Forgejo hosts) via HTTPS solely for installed or user-watched apps mapped to open-source repositories. Optional Personal Access Tokens are sent strictly to their respective host and never forwarded across cross-host redirects.
- **APKMirror:** Queries `www.apkmirror.com` via HTTPS (API and/or public RSS/Atom feeds) to check version numbers and resolve APK download links for tracked packages.
- **Google Play Store (Anonymous Compatibility Check):** When enabled, ApkUp requests an anonymous session token from the community Aurora Dispenser (`auroraoss.com`) and queries `android.clients.google.com` via HTTPS using installed package IDs and a minimized device compatibility profile (CPU ABI, Android SDK version, screen density, and normalized language code) to retrieve compatible update metadata. No personal Google account is used.

---

### 3. Device Permissions & Encrypted Local Storage
ApkUp requests only standard Android permissions necessary for its core functionality:
- `INTERNET` & `ACCESS_NETWORK_STATE`: Required to query enabled update sources and download APK files over HTTPS.
- `REQUEST_INSTALL_PACKAGES`: Required to hand over verified APK packages to the Android system `PackageInstaller` or optional Shizuku service.
- `POST_NOTIFICATIONS`: Optional permission to notify you about available updates and download progress.
- `QUERY_ALL_PACKAGES`: Required locally on the device to inspect installed package names, version codes, and signing certificates so ApkUp can detect updates and block signature-mismatched packages before installation.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`: Used by AndroidX `WorkManager` to run user-initiated update downloads reliably.

All local settings, repository bindings, and optional API tokens are stored exclusively in private app storage encrypted at rest via **Google Tink (AES-256-GCM)** backed by the Android Keystore. Cloud backups and device-to-device data extraction are disabled (`android:allowBackup="false"`). Downloaded APK files are stored inside ApkUp's private internal cache directory (and automatically purged after installation or expiration) unless you explicitly use "Save APK" to export a copy to your chosen download folder. Any local diagnostic log (`AppJournal` + process logcat) stays strictly offline on your device, strips access tokens, and is only saved or shared when explicitly triggered by you.

---

### 4. Open Source Auditing
ApkUp is licensed under the **GNU General Public License v3.0 (GPLv3)**. Its entire source code is publicly accessible and open to independent security audits at:  
https://github.com/DurchEins/apkup

---

### 5. Contact
If you have any questions or concerns regarding this Privacy Policy, please reach out via GitHub:
- **Issue Tracker:** https://github.com/DurchEins/apkup/issues
- **Security Advisories (Confidential):** https://github.com/DurchEins/apkup/security/advisories/new
