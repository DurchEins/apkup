# ApkUp

<p align="center">
  <img src="design/ic_launcher_512.png" width="96" height="96" alt="ApkUp Icon" />
</p>

<p align="center">
  <b>Eine moderne Open-Source-Android-App zur Verwaltung von App-Updates (Google Play & APKMirror) mit Material 3 Expressive Design, Shizuku-Unterstützung und minimalistischem Pixel-Look.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.26%20Beta-6750A4?style=flat-square" alt="Version" />
  <img src="https://img.shields.io/badge/Android-10.0%2B%20(API%2029%2B)-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License" />
  <img src="https://img.shields.io/badge/Shizuku-Supported-00897B?style=flat-square" alt="Shizuku" />
</p>

---

## ✨ Features

* **🚀 Leistungsstarke Update-Quellen:**
  * **Google Play Store:** Offizielle Store-Abfrage mit Delta-Erkennung.
  * **APKMirror Turbo:** Paralleler, optimierter Web-Scraper für schnelle Erkennung neuester Releases.
* **🎨 Material 3 Expressive Design:**
  * Native Ästhetik im Stil von Android 16/17 für Google Pixel und moderne Geräte.
  * Vollständige Unterstützung von **Dynamic Color (Material You)**, Dark-, Light- und System-Theming.
  * Wählbare Layout-Modi für die Update-Ansicht (*Kompakte Kacheln* vs. *Detaillierte Liste*).
* **⚡ Nahtlose Installation mit Shizuku:**
  * Vollautomatische Hintergrund-Installation ohne manuelle Bestätigungs-Dialoge bei aktiver Shizuku-Verbindung.
  * Integrierter Standard-Installer mit Android `PackageInstaller`-Session-Support als Fallback.
* **🛡️ Präziser Versionsabgleich & Filter:**
  * **Keine False-Positives:** Exakte Erkennung höherer Versionsnummern und Build-Codes (`versionCode`).
  * **Übersprungene Einzel-Updates:** Fehlerhafte Versionen gezielt einmalig ignorieren – bei einem neueren Update wird wieder benachrichtigt.
  * **Dauerhaft verborgene Apps:** Apps dauerhaft aus dem Update-Zyklus ausschließen und komfortabel verwalten.
  * **System-Apps-Filter:** Flexible Ein- oder Ausblendung von vorinstallierten Systemanwendungen.
* **⏰ Zuverlässige Hintergrundprüfung & Benachrichtigungen:**
  * Periodische Update-Prüfung via Android `WorkManager` (wählbare Intervalle: 6h, 12h, 24h, 48h oder 7 Tage).
  * Informative System-Benachrichtigung bei neuen Updates mit Direkt-Sprung in die App.
* **💾 Backup & Wiederherstellung:**
  * Sicherung aller Einstellungen, ignorierten Apps und Versionen als JSON-Datei.
* **💖 Pixel-Details:**
  * Taktiles Doppelschlag-Herzschlag-Haptik-Feedback auf unterstützten Vibrationsmotoren.
  * Physikalischer Overscroll-Pull-Effekt.

---

## 🛠️ Technische Basis

* **Sprache:** Kotlin 2.3+
* **UI:** Jetpack Compose & Material 3 Expressive
* **Architektur:** MVVM mit Kotlin Coroutines & Reactive StateFlow
* **Dependency Injection:** Koin
* **Networking & Parsing:** Retrofit 2, OkHttp 5, Jsoup
* **Rootless ADB / Elevated Privileges:** Shizuku API 13.x
* **Background Processing:** AndroidX WorkManager

---

## 📥 Installation & Voraussetzungen

1. **Mindestversion:** Android 10 (API Level 29) oder höher.
2. **Empfohlen:** Android 12+ für adaptive Dynamic Color Akzente und [Shizuku](https://shizuku.rikka.app/) für Silent Updates.

---

## 📜 Lizenz & Danksagung

Dieses Projekt steht unter der **[MIT License](LICENSE)**.

```
Copyright (c) 2026 DuehEins
Portions Copyright (c) 2017-2024 rumboalla (based on the original project APKUpdater)
```

> **Besonderer Dank:**  
> Basierend auf dem geschätzten Open-Source-Originalprojekt von **[rumboalla (APKUpdater)](https://github.com/rumboalla/apkupdater)**.
