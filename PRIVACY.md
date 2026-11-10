# Privacy Policy for ApkUp

**Last Updated:** November 10, 2026

ApkUp is an open-source, client-side Android application developed by Nico (DurchEins). This Privacy Policy explains how ApkUp handles data and respects user privacy.

---

### 1. No Collection of Personal Data
ApkUp is designed with a strict **Privacy by Design** philosophy:
- **No Personal Information:** We do not collect, transmit, process, or sell names, email addresses, phone numbers, location data, or unique device identifiers (IMEI, Android ID, Advertising ID).
- **No User Accounts:** ApkUp does not require registration, login, or user profiles.
- **No Analytics or Telemetry:** ApkUp contains zero analytics SDKs, crash trackers, or behavioral telemetry (no Google Firebase, no Crashlytics, no Sentry, no Mixpanel).
- **No Advertisements:** ApkUp is completely free of ads, tracking banners, and ad networks.

---

### 2. Network Activity & External Services
ApkUp functions as an update manager and sideloading companion. All network communication is initiated locally from the user's device and connects directly via encrypted HTTPS to public developer distribution channels:
- **Public Version Checking:** Queries public release endpoints on GitHub (`api.github.com`), GitLab (`gitlab.com`), F-Droid, or APKMirror to compare version codes of installed apps.
- **Direct Downloads:** Downloads public APK installation packages directly from developer-hosted releases to the device's local storage.
- **No Intermediary Servers:** There is no ApkUp backend, proxy server, or database logging user queries or IP addresses.

---

### 3. Device Permissions & Local Data
ApkUp requests only standard Android permissions necessary for its core functionality:
- `INTERNET` & `ACCESS_NETWORK_STATE`: Required to query public repository release feeds and download APK files.
- `REQUEST_INSTALL_PACKAGES`: Required to hand over downloaded APK files to the Android system installer or Shizuku service.
- `POST_NOTIFICATIONS`: Optional permission to notify users when background update checks discover new releases.
- `QUERY_ALL_PACKAGES`: Required locally on the device to read package names and installed version numbers of applications the user wishes to track. This information never leaves the device.

All preferences, ignore lists, and cached update timestamps are stored exclusively in private local app storage (`SharedPreferences` / local SQLite database) on the user's device.

---

### 4. Open Source Auditing
ApkUp is licensed under the **GNU General Public License v3.0 (GPLv3)**. Its entire source code is publicly accessible and open to independent security audits at:  
https://github.com/DurchEins/apkup

---

### 5. Contact
If you have any questions or concerns regarding this Privacy Policy, please contact:
- **Email:** apkup.app@gmail.com
- **Issue Tracker:** https://github.com/DurchEins/apkup/issues
