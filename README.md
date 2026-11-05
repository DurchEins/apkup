# ApkUp

<p align="center">
  <img src=".github/assets/logo.png" width="96" height="96" alt="ApkUp Icon" />
</p>

<p align="center">
  <b>A modern open-source Android application for discovering, managing, and installing app updates across multiple sources (Google Play, APKMirror, IzzyOnDroid / F-Droid Index V2, GitHub & GitLab) featuring Material 3 Expressive design, Shizuku silent installations, and a clean Google Pixel aesthetic.</b>
</p>

<p align="center">
  <a href="https://github.com/DurchEins/apkup/releases/latest"><img src="https://img.shields.io/badge/Version-1.40.1--Beta%20(153)-A855F7?style=flat-square" alt="Version" /></a>
  <img src="https://img.shields.io/badge/Android-10.0%2B%20(API%2029%2B)-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square" alt="License: GPL v3" />
  <img src="https://img.shields.io/badge/Shizuku-Supported-00897B?style=flat-square" alt="Shizuku" />
  <img src="https://img.shields.io/badge/Privacy-No%20Ads%20%7C%20No%20Tracking-brightgreen?style=flat-square" alt="Privacy" />
</p>

<p align="center">
  <a href="#-features">Features</a> • <a href="#-tech-stack">Tech Stack</a> • <a href="#-requirements">Requirements</a> • <a href="#-screenshots">Screenshots</a> • <a href="#-troubleshooting--wiki">Wiki</a> • <a href="#-license--credits">License</a>
</p>

---

## ✨ Features

* **🚀 Robust Update Sources & Self-Updater:**
  * **Google Play Store:** Direct store query with accurate delta version recognition and automatic HTTP 429 rate-limit recovery.
  * **APKMirror In-App Downloads:** Direct in-app downloading and installation (including `.apkm` split-bundle extraction) with intelligent rate-limiting and automatic Cloudflare browser fallback.
  * **IzzyOnDroid (F-Droid Index V2):** Modern F-Droid Index V2 specification support with ETag pre-flight checks and SHA-256 certificate validation, making update checks virtually instantaneous while drastically saving bandwidth.
  * **GitHub & GitLab:** Built-in recognition for popular FOSS projects plus custom repository bindings (`owner/repo`, self-hosted GitLab, and multi-level subgroup paths).
  * **Obtainium Interoperability:** Seamlessly import app repository configurations from Obtainium backup files and export your custom bindings back into Obtainium-compatible JSON format.
  * **ApkUp Self-Updater:** Automatic release checks and direct in-app updating without relying on external app stores.
* **🎨 Material 3 Expressive Design:**
  * Pixel-inspired UI optimized for Android 15, 16, and 17 styling with a floating capsule navigation dock and tactile haptic feedback.
  * Comprehensive **Dynamic Color (Material You)** support with dark, light, and system themes.
  * Monthly-grouped in-app changelog with semantic release tags, expandable month accordions, and visual highlight banners.
  * Multiple display modes for the update list (*Compact Grid Tiles* vs. *Detailed List*).
  * Auto-resizing typography ensuring multi-digit version numbers always display cleanly on a single line.
* **⚡ Uninterrupted Background Downloads, 24h Cache & Shizuku:**
  * **Foreground Download Service:** Downloads and batch updates (*Update All*) continue reliably in the background and across screen locks, complete with live progress notifications and instant cancellation.
  * **24-Hour Smart Cache & Instant Resume:** Downloaded APKs are preserved locally for up to 24 hours. Dismissed installations can be resumed instantly with "Install" without re-downloading.
  * **Silent Shizuku Installs:** Unattended background updates without repeated permission dialogs when connected to Shizuku.
  * **Reliable Native Fallback:** Seamless fallback to Android's `PackageInstaller` session API with Android 14+ Background Activity Launch (BAL) notification support and robust session abandonment.
* **📶 Network Data Saver:**
  * **Wi-Fi Only Mode:** Restrict downloads to unmetered Wi-Fi connections with an active mobile data safety prompt.
* **🛡️ Smart Version Detection & Filters:**
  * **Zero False Positives:** Verifiable semantic version and `versionCode` progression check.
  * **Skip Single Version:** Ignore a specific faulty build once; you will be notified again when a newer release arrives.
  * **Permanently Hidden Apps:** Exclude apps permanently from the update cycle and manage them anytime.
  * **System Apps Toggle:** Filter or include pre-installed system applications on demand.
* **⏰ Background Worker & Notifications:**
  * Periodic update checks powered by Android `WorkManager` (configurable: 6h, 12h, 24h, 48h, or 7 days).
  * Informative system notifications alerting you when new updates are ready to install.
* **📖 Troubleshooting & Help Wiki:**
  * One-tap contextual help buttons on repository errors that link directly to comprehensive step-by-step guides in the [ApkUp GitHub Wiki](https://github.com/DurchEins/apkup/wiki).
* **💾 Configuration Backup & Restore:**
  * Export and import your entire setup, custom repository bindings, ignored apps, and skipped versions via a single JSON file.
* **🔒 Privacy First:**
  * No ads. No telemetry. No user tracking. Pure open source.

---

## 🛠️ Tech Stack

* **Language:** Kotlin 2.3+
* **UI Framework:** Jetpack Compose & Material 3 Expressive
* **Architecture:** MVVM with Coroutines & Reactive StateFlow
* **Dependency Injection:** Koin
* **Network & Parsing:** Retrofit 3, OkHttp 5, Jsoup
* **Privileged Install:** Shizuku API 13.x
* **Background Tasks:** AndroidX WorkManager & Foreground Service (`dataSync`)
* **SDK Compatibility:** Min SDK 29 (Android 10.0) | Target SDK 37 (Android 17)

---

## 📥 Requirements

* **Minimum:** Android 10.0 (API Level 29) or higher.
* **Recommended:** Android 12+ for full Dynamic Color theming and [Shizuku](https://shizuku.rikka.app/) for silent background installations.

---

## 📸 Screenshots

<p align="center">
  <img src=".github/assets/screenshots/updates%201.png" width="260" alt="Updates Screen" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/updates%202.png" width="260" alt="Update Details Sheet" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/apps%201.png" width="260" alt="Installed Apps List" />
</p>

<p align="center">
  <img src=".github/assets/screenshots/apps%202.png" width="260" alt="Apps Search & Grid" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/apps%203.png" width="260" alt="App Details & Context Actions" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/setup%201.png" width="260" alt="Settings & Appearance" />
</p>

<p align="center">
  <img src=".github/assets/screenshots/setup%202.png" width="260" alt="Settings & Sources" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/setup%203.png" width="260" alt="Shizuku & App Management" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/setup%204.png" width="260" alt="About ApkUp" />
</p>

---

## 📖 Troubleshooting & Wiki

Need help configuring sources or resolving connection errors? Visit the official **[ApkUp Wiki](https://github.com/DurchEins/apkup/wiki)** for step-by-step guides:

* [Troubleshooting Source Errors](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors)
* [Configuring GitHub & GitLab Personal Access Tokens](https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens)
* [Setting up Shizuku for Silent Updates](https://github.com/DurchEins/apkup/wiki/Setting-up-Shizuku)
* [Obtainium Migration & Backup Guide](https://github.com/DurchEins/apkup/wiki)

---

## 📜 License & Credits

This project is licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)** — see the [LICENSE](LICENSE) file for full details.

```text
ApkUp – Copyright (C) 2026 DurchEins
Portions Copyright (C) 2016–2024 rumboalla (based on the original project APKUpdater)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

> **Special Thanks:**  
> Based on the original open-source project by **[rumboalla (APKUpdater)](https://github.com/rumboalla/apkupdater)**.
