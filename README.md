# ApkUp

<p align="center">
  <img src="design/ic_launcher_512.png" width="96" height="96" alt="ApkUp Icon" />
</p>

<p align="center">
  <b>A modern open-source Android app for managing and installing app updates (Google Play & APKMirror) featuring Material 3 Expressive design, Shizuku integration, and a clean Google Pixel aesthetic.</b><br>
  <i>Eine moderne Open-Source-Android-App zur Verwaltung und Installation von App-Updates (Google Play & APKMirror) mit Material 3 Expressive Design, Shizuku-Unterstützung und minimalistischem Pixel-Look.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.26%20Beta-6750A4?style=flat-square" alt="Version" />
  <img src="https://img.shields.io/badge/Android-10.0%2B%20(API%2029%2B)-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License" />
  <img src="https://img.shields.io/badge/Shizuku-Supported-00897B?style=flat-square" alt="Shizuku" />
  <img src="https://img.shields.io/badge/Privacy-No%20Ads%20%7C%20No%20Tracking-brightgreen?style=flat-square" alt="Privacy" />
</p>

<p align="center">
  <a href="#english">English</a> • <a href="#deutsch">Deutsch</a> • <a href="#-screenshots">Screenshots</a> • <a href="#-license--credits">License</a>
</p>

---

<a name="english"></a>
## 🇬🇧 English

### ✨ Features
* **🚀 Robust Update Sources:**
  * **Google Play Store:** Direct store query with accurate delta version recognition.
  * **APKMirror Turbo:** Parallel, optimized scraper engine for instant detection of the latest app releases.
* **🎨 Material 3 Expressive Design:**
  * Pixel-inspired UI optimized for Android 15/16/17 styling.
  * Comprehensive **Dynamic Color (Material You)** support with dark, light, and system themes.
  * Multiple display modes for the update list (*Compact Grid Tiles* vs. *Detailed List*).
* **⚡ Seamless Installation with Shizuku:**
  * Silent, unattended background updates without repeated permission dialogs when connected to Shizuku.
  * Reliable native fallback to Android's `PackageInstaller` session API.
* **🛡️ Smart Version Detection & Filters:**
  * **Zero False Positives:** Verifiable semantic version and `versionCode` progression check.
  * **Skip Single Version:** Ignore a specific faulty build once; you will be notified again when a newer release arrives.
  * **Permanently Hidden Apps:** Exclude apps permanently from the update cycle and manage them anytime.
  * **System Apps Toggle:** Filter or include pre-installed system applications on demand.
* **⏰ Background Worker & Notifications:**
  * Periodic update checks powered by Android `WorkManager` (configurable: 6h, 12h, 24h, 48h, or 7 days).
  * Informative system notifications alerting you when new updates are ready to install.
* **💾 Configuration Backup & Restore:**
  * Export and import your entire setup, ignored apps, and skipped versions via a single JSON file.
* **🔒 Privacy First:**
  * No ads. No telemetry. No user tracking. Pure open source.

### 🛠️ Tech Stack
* **Language:** Kotlin 2.3+
* **UI Framework:** Jetpack Compose & Material 3 Expressive
* **Architecture:** MVVM with Coroutines & Reactive StateFlow
* **Dependency Injection:** Koin
* **Network & Parsing:** Retrofit 2, OkHttp 5, Jsoup
* **Privileged Install:** Shizuku API 13.x
* **Background Tasks:** AndroidX WorkManager

### 📥 Requirements
* **Minimum:** Android 10.0 (API Level 29) or higher.
* **Recommended:** Android 12+ for full Dynamic Color theming and [Shizuku](https://shizuku.rikka.app/) for silent background installations.

---

## 📸 Screenshots

<p align="center">
  <img src="screenshots/updates%201.png" width="260" alt="Updates Screen" />
  &nbsp;&nbsp;
  <img src="screenshots/updates%202.png" width="260" alt="Update Details Sheet" />
  &nbsp;&nbsp;
  <img src="screenshots/apps%201.png" width="260" alt="Installed Apps List" />
</p>

<p align="center">
  <img src="screenshots/apps%202.png" width="260" alt="Apps Search & Grid" />
  &nbsp;&nbsp;
  <img src="screenshots/apps%203.png" width="260" alt="App Details & Context Actions" />
  &nbsp;&nbsp;
  <img src="screenshots/setup%201.png" width="260" alt="Settings & Appearance" />
</p>

<p align="center">
  <img src="screenshots/setup%202.png" width="260" alt="Settings & Sources" />
  &nbsp;&nbsp;
  <img src="screenshots/setup%203.png" width="260" alt="Shizuku & App Management" />
  &nbsp;&nbsp;
  <img src="screenshots/setup%204.png" width="260" alt="About ApkUp" />
</p>

---

<a name="deutsch"></a>
## 🇩🇪 Deutsch

### ✨ Funktionen
* **🚀 Leistungsstarke Update-Quellen:**
  * **Google Play Store:** Direkte Store-Abfrage mit präziser Erkennung von Versionssprüngen.
  * **APKMirror Turbo:** Paralleler, optimierter Web-Scraper zur schnellen Erkennung neuester Releases.
* **🎨 Material 3 Expressive Design:**
  * Authentische Ästhetik im Stil moderner Google-Pixel-Flaggschiffe (Android 15/16/17).
  * Vollständige Unterstützung von **Dynamic Color (Material You)** sowie Dark-, Light- und System-Theming.
  * Wählbare Ansichten für den Updates-Tab (*Kompakte Kacheln* vs. *Detaillierte Liste*).
* **⚡ Nahtlose Installation mit Shizuku:**
  * Vollautomatische Hintergrund-Installation ohne manuelle Bestätigungs-Dialoge bei aktiver Shizuku-Verbindung.
  * Zuverlässiger Fallback auf die native Android `PackageInstaller`-Session-API.
* **🛡️ Präziser Versionsabgleich & Filter:**
  * **Keine False-Positives:** Exakte Erkennung höherer Versionsnummern und Build-Codes (`versionCode`).
  * **Übersprungene Einzel-Updates:** Eine fehlerhafte Version gezielt einmalig ignorieren – bei einem neueren Update wird wieder benachrichtigt.
  * **Dauerhaft verborgene Apps:** Apps dauerhaft aus dem Update-Zyklus ausschließen und übersichtlich verwalten.
  * **System-Apps-Filter:** Flexible Ein- oder Ausblendung von vorinstallierten Systemanwendungen.
* **⏰ Zuverlässige Hintergrundprüfung & Benachrichtigungen:**
  * Periodische Update-Prüfung via Android `WorkManager` (wählbare Intervalle: 6h, 12h, 24h, 48h oder 7 Tage).
  * Informative System-Benachrichtigung bei neuen Updates mit Direkt-Sprung in die App.
* **💾 Backup & Wiederherstellung:**
  * Export und Import aller Einstellungen, ignorierten Apps und Versionen als JSON-Datei.
* **🔒 Privatsphäre garantiert:**
  * Keine Werbung. Kein Tracking. Keine Telemetrie. Vollständig Open Source.

### 🛠️ Technische Basis
* **Sprache:** Kotlin 2.3+
* **UI:** Jetpack Compose & Material 3 Expressive
* **Architektur:** MVVM mit Kotlin Coroutines & Reactive StateFlow
* **Dependency Injection:** Koin
* **Networking & Parsing:** Retrofit 2, OkHttp 5, Jsoup
* **Rootless ADB / Elevated Privileges:** Shizuku API 13.x
* **Background Processing:** AndroidX WorkManager

### 📥 Voraussetzungen
* **Mindestversion:** Android 10 (API Level 29) oder höher.
* **Empfohlen:** Android 12+ für adaptive Dynamic Color Akzente und [Shizuku](https://shizuku.rikka.app/) für Silent Updates.

---

<a name="-license--credits"></a>
## 📜 License & Credits / Lizenz & Danksagung

This project is licensed under the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 DurchEins
Portions Copyright (c) 2017-2024 rumboalla (based on the original project APKUpdater)
```

> **Special Thanks / Besonderer Dank:**  
> Based on the original open-source project by **[rumboalla (APKUpdater)](https://github.com/rumboalla/apkupdater)**.
