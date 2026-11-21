<p align="center">
  <img src=".github/assets/header-banner.svg" width="100%" alt="ApkUp — Multi-Source • Verified • Silent" />
</p>

<p align="center">
  <img src=".github/assets/logo.png" width="84" height="84" alt="ApkUp Icon" />
</p>

<p align="center">
  <b>A modern open-source Android application for discovering, verifying, and autonomously installing app updates across multiple sources (Google Play, F-Droid &amp; IzzyOnDroid, APKMirror, GitHub, GitLab &amp; Codeberg/Forgejo) featuring Material 3 Expressive design, cryptographic APK signature verification, Watchlist tracking, APK export, and unattended Shizuku &amp; optional Root background updates.</b>
</p>

<p align="center">
  <a href="https://durcheins.github.io/apkup/">Homepage</a> • <a href="#-features">Features</a> • <a href="#-screenshots">Screenshots</a> • <a href="#-features">Sources</a> • <a href="#-quick-guides">Security</a> • <a href="#-downloads">Install</a> • <a href="#-troubleshooting--wiki">Wiki</a>
</p>

<p align="center">
  <a href="https://github.com/DurchEins/apkup/releases/latest"><img src="https://img.shields.io/github/downloads/DurchEins/apkup/total?style=flat&label=GitHub%20Downloads&color=F59E0B&logo=github" alt="GitHub Downloads" /></a>
  <a href="https://github.com/DurchEins/apkup/releases/latest"><img src="https://img.shields.io/badge/Version-1.45.1%20(223)-38BDF8?style=flat" alt="Version" /></a>
  <img src="https://img.shields.io/badge/Min%20SDK-29%20(Android%2010%2B)-3DDC84?style=flat&logo=android" alt="Min SDK 29" />
  <img src="https://img.shields.io/badge/Privacy-No%20Ads%20%7C%20No%20Tracking-2EA44F?style=flat" alt="Privacy: No Ads | No Tracking" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat" alt="License GPL-3.0" /></a>
</p>

---

## ✨ Features

* **🚀 Multi-Source Update Engine & Self-Updater:**
  * **F-Droid & IzzyOnDroid Multi-Repo Engine:** Native support for official F-Droid, IzzyOnDroid, and custom HTTPS F-Droid repositories (`?fingerprint=…`) with cryptographic JAR certificate verification (`entry.jar` / `index-v1.jar`), incremental Index-V2 diffs (`RFC 7396`) with per-diff SHA-256 verification, and per-APK SHA-256 checksum validation ([Index-V2 Guide](https://github.com/DurchEins/apkup/wiki/F-Droid-%26-Index-V2-Repositories)).
  * **Google Play Store:** Anonymous store delta queries with automatic HTTP 429 rate-limit recovery and privacy-minimized device compatibility profiles.
  * **APKMirror In-App Downloads:** Direct in-app downloading and `.apkm` split-bundle installation with automatic in-app Cloudflare WebView verification, intelligent concurrency throttling, 10-minute per-app cooldown, and seamless browser fallback on anti-bot challenges.
  * **GitHub, GitLab & Codeberg / Forgejo:** Built-in mappings for popular FOSS apps, batched GraphQL update queries when a GitHub token is configured, and 3-way custom repository bindings (`owner/repo`, self-hosted GitLab/Forgejo instances, multi-level subgroup paths, token verification sheets, optional asset regex filtering, and FOSS flavor preference) with strict host-isolated token handling.
  * **Configurable Source Priority & Per-App Pinning:** Reorder update sources via drag-and-drop or arrow controls in *Setup*, pin individual apps to a preferred source with automatic fallback when no update is available there, and track active F-Droid repositories in a compact grouped scan tray.
  * **Pre-Release Channels & Background Time Window:** Selectively enable **Beta**, **RC**, **Alpha**, or **Nightly** channels globally or per app, and configure quiet hours or active time windows for background checks and auto-updates with automatic catch-up scheduling.
  * **Automatic Rollback Backups & Compatibility Guard:** Optionally back up installed APKs (including splits) before updating for in-place Root (`-r -d`) or guided non-Root rollbacks, with pre-install `minSdk` / `ABI` checks and a *Source Health & Cooldowns* sheet in *Setup* ([Troubleshooting](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors)).
  * **Watch Uninstalled Apps & App Watcher Import:** Track releases for apps that are not yet installed via the **`+`** button in *Apps* and the *Watched* filter, mark versions as *Seen*, or import watchlists directly from **App Watcher** exports.
  * **Obtainium Interoperability with Preview:** Import app configurations, custom F-Droid repositories, and watchlist candidates from Obtainium exports via an interactive preview sheet, and export custom bindings back to Obtainium JSON ([Obtainium Sync Guide](https://github.com/DurchEins/apkup/wiki/Obtainium-Migration-%26-Sync-Guide)).
  * **Verified Self-Updater with Optional Beta Channel:** Checks official GitHub releases (stable-only by default, with an opt-in *Receive ApkUp beta updates* toggle under *Setup ➔ About* that cleanly returns to stable on opt-out) and validates `SHA256SUMS` digests plus APK signing certificates before installing updates.
* **🛡️ Defense-in-Depth Security & Privacy:**
  * **Pre-Install APK Signature & Downgrade Verification:** Cryptographically verifies APK Signature Scheme v2/v3 certificates against the installed app before launching `PackageInstaller`, Shizuku, or Root (`su`), blocking certificate mismatches and version downgrades ([SHA-256 & Signature Guide](https://github.com/DurchEins/apkup/wiki/SHA-256-%26-Signature-Verification)).
  * **Onboarding Source Consent:** Transparent first-launch source selection ensures zero external network requests happen before you choose your preferred update sources.
  * **Encrypted Local Storage:** Preferences and optional API tokens are encrypted at rest via **Google Tink (AES-256-GCM)** in Android Keystore, with cloud backups and cleartext HTTP strictly disabled.
  * **Tapjacking & Archive Hardening:** Touch events are filtered when obscured by overlays, and `.apkm` extraction enforces strict Zip-Slip and decompression-bomb limits inside private internal storage.
* **🎨 Material 3 Expressive Design & 9 Official Languages:**
  * Unified Brand-Amber color scheme with a soft off-white light theme, optional Dynamic Color (Material You) toggle, dark/light/system themes, floating capsule navigation dock (with automatic `NavigationRail` on tablets/wider displays), wavy progress indicators, split-button actions, collapsible scroll headers, unified modal bottom sheets, and full reduced-motion accessibility support.
  * Instant search bars and flexible sorting across both **Updates** (*Name A–Z / Z–A, Source, Newest Release Date*) and **Apps** (*Name A–Z / Z–A, Source, Recently Updated*).
  * Localized in **9 official languages**: English, German, Spanish, French, Portuguese, Italian, Russian, Simplified Chinese, and Japanese.
  * Multiple display modes (*Compact Grid Tiles* vs. *Detailed List*), quick-filter chips (including *GitHub / Sideload* and *Watched*), and monthly-grouped in-app changelogs.
* **⚡ Reliable WorkManager Downloads, APK Export, 24h Cache & Autonomous Installs:**
  * **User-Initiated Data Transfer Worker & Batch ETA:** Interactive downloads, multi-select batch installs (long-press any update card), and filtered batch updates (*Install All*) run reliably via AndroidX `WorkManager` (`UpdateDownloadWorker`) with live overall batch percentage, smoothed transfer speed, ETA, and instant per-item or global cancellation.
  * **Save APK & Split `.apks` Bundles:** Export single `.apk` installers or bundled `.apks` split packages directly into `Download/ApkUp/` (or any custom SAF folder configured in Setup) without installing.
  * **Autonomous Background Updates (Shizuku & Optional Root `su`):** Optional zero-touch Shizuku or Root (`su`) pipeline with live availability checks, configurable **Wi-Fi only** and **While charging** guards, consolidated summary notifications, a sanitized **Diagnostic & Update Log** (`.txt`), and a persistent **Update History** sheet ([Background Install Guide](https://github.com/DurchEins/apkup/wiki/Shizuku-%26-Background-Installation)).
  * **24-Hour Smart Cache & Instant Resume:** Verified APKs in private cache can be installed without re-downloading, with automatic Android 14+ Background Activity Launch (BAL) prompt fallback.

---

## 📥 Requirements

* **Minimum:** Android 10.0 (API Level 29) or higher.
* **Recommended:** Android 12+ for Dynamic Color theming and [Shizuku](https://shizuku.rikka.app/) for silent background installations.
* **Sideloading Note:** Need help with Android restricted settings or Play Protect? See the [Google Lock & Sideloading Guide](https://durcheins.github.io/apkup/sideloading/google-lock/).

---

## 🛠️ Tech Stack

<details>
<summary><strong>🛠️ Tech Stack &amp; Architecture</strong></summary>

* **Language:** Kotlin 2.3+
* **UI Framework:** Jetpack Compose & Material 3 Expressive
* **Architecture:** MVVM with Coroutines & Reactive StateFlow
* **Dependency Injection:** Koin 4.2
* **Cryptography & Storage:** Google Tink Android 1.20 (`AES-256-GCM`), `apksig` v2/v3 verification
* **Network & Parsing:** Retrofit 3, OkHttp 5, Jsoup
* **Privileged Install:** Shizuku API 13.x & optional `su` Root session installer
* **Background Tasks:** AndroidX WorkManager 2.11 (`User-Initiated Data Transfer` & Periodic Workers)
* **SDK Compatibility:** Min SDK 29 (Android 10.0) | Target & Compile SDK 37 (Android 17)

</details>

---

## 📸 Screenshots

<p align="center">
  <img src=".github/assets/screenshots/01_en_updates.png" width="260" alt="Updates Screen" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/02_en_update_details.png" width="260" alt="Update Details Sheet" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/03_en_apps_list.png" width="260" alt="Installed Apps List" />
</p>

<p align="center">
  <img src=".github/assets/screenshots/04_en_apps_grid.png" width="260" alt="Apps Search & Grid" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/05_en_apps_menu.png" width="260" alt="App Details & Context Actions" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/06_en_settings_sources.png" width="260" alt="Settings & Sources" />
</p>

<p align="center">
  <img src=".github/assets/screenshots/07_en_settings_about.png" width="260" alt="About ApkUp & Backup" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/08_en_update_history.png" width="260" alt="Update History & Rollback Logs" />
  &nbsp;&nbsp;
  <img src=".github/assets/screenshots/09_en_whats_new.png" width="260" alt="What's New & Changelog" />
</p>

---

## 📲 Downloads

Get the latest release of ApkUp from your preferred platform:

<p align="center">
  <a href="https://github.com/DurchEins/apkup/releases/latest"><img src="https://img.shields.io/badge/GitHub-Direct%20APK%20(Recommended)-181717?style=flat&logo=github" alt="GitHub Releases" /></a>&nbsp;&nbsp;
  <a href="https://zapstore.dev/apps/com.apkup"><img src="https://img.shields.io/badge/Zapstore-Available-7C3AED?style=flat&logo=lightning&logoColor=white" alt="Zapstore" /></a>&nbsp;&nbsp;
  <a href="https://gitlab.com/DurchEins1/apkup/-/releases"><img src="https://img.shields.io/badge/GitLab-Releases-FC6D26?style=flat&logo=gitlab&logoColor=white" alt="GitLab Releases" /></a>&nbsp;&nbsp;
  <a href="https://apkpure.com/apkup/com.apkup"><img src="https://img.shields.io/badge/APKPure-Available-24C270?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjxwYXRoIGQ9Ik01LjIgMi40QzQuMiAxLjggMyAyLjUgMyAzLjd2MTYuNmMwIDEuMiAxLjIgMS45IDIuMiAxLjNsMTQuNi04LjNjMS0uNiAxLTIgMC0yLjZMNS4yIDIuNHptNC45IDUuNWw0LjkgNC4xLTQuOSA0LjFWNy45eiIvPjwvc3ZnPg%3D%3D" alt="APKPure" /></a>&nbsp;&nbsp;
  <a href="https://alternativeto.net/software/apkup/about/"><img src="https://img.shields.io/badge/AlternativeTo-Listed-0066CC?style=flat&logo=alternativeto&logoColor=white" alt="AlternativeTo" /></a>&nbsp;&nbsp;
  <a href="https://durcheins.itch.io/apkup"><img src="https://img.shields.io/badge/itch.io-Available-FA5C5C?style=flat&logo=itchdotio&logoColor=white" alt="itch.io" /></a>&nbsp;&nbsp;
  <a href="https://appteka.store/app/c0cr341070"><img src="https://img.shields.io/badge/Appteka-Available-0EA5E9?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNjAgMjYwIiBmaWxsPSJ3aGl0ZSI%2BPHBhdGggZD0iTTIzLjkyNzYgMTIwLjMyTDEyMC4zMiAyMy45Mjc2QzE1Mi4zNiAtNy45NzU4OCAyMDQuMDMyIC03Ljk3NTg4IDIzNi4wNzIgMjMuOTI3NkMyNjcuOTc2IDU1LjgzMTEgMjY3Ljk3NiAxMDcuNjQgMjM2LjA3MiAxMzkuNjhMMTM5LjY4IDIzNi4wNzJDMTA3LjY0IDI2Ny45NzYgNTUuODMxMSAyNjcuOTc2IDIzLjkyNzYgMjM2LjA3MkMtNy45NzU4OCAyMDQuMDMyIC03Ljk3NTg4IDE1Mi4zNiAyMy45Mjc2IDEyMC4zMlpNNDMuMjg3OSAxMzkuNjhDMjguOTcyMiAxNTMuODU5IDI0LjIwMDMgMTc0LjE3NCAyOS4xMDg1IDE5Mi4zMDdMMTEwLjc3NiAxMTAuNzc2TDE2OC41ODQgMTY4LjU4NEwyMTYuNzEyIDEyMC4zMkMyMzguMTE3IDk5LjA1MDkgMjM4LjExNyA2NC41NTY5IDIxNi43MTIgNDMuMjg3OUMxOTUuNDQzIDIxLjg4MjUgMTYwLjk0OSAyMS44ODI1IDEzOS42OCA0My4yODc5TDQzLjI4NzkgMTM5LjY4WiIvPjwvc3ZnPg%3D%3D" alt="Appteka" /></a>&nbsp;&nbsp;
  <a href="https://komistore.app"><img src="https://img.shields.io/badge/Komi%20Store-Available-6366F1?style=flat&logo=data%3Aimage%2Fpng%3Bbase64%2CiVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAUxSURBVHhe7ZptyN9TGMeHmIfFmJmnF4jkob0goiYhmYfRPBPWojzV5uGFhxdEjRdD1EghjGgYtVAKmU3L5PGFyZqIEstks83D9rn0%2FbnO3bmv%2Ff737v%2B9uv%2Fnvne%2Bdbp%2F%2F3PO75xzXed6%2Ft1jxlRUVFRUVFRUVFRUVFRUVAwFwOHAEuBZ4IQ4PuoBzLUMwAvAAXHe1gBMAuYAp5vZDnG8SJjZTsDXLgEXAwudCb8A58T5nSCigZ%2BBjcANwFgzGxfnFQfdNPAXMDXru0x9zojZ3rcHcCBwNHCUme2vvmz%2Bv8BHwEHqc8Y%2BI6Zm25UHM5sCrAeOzPuBU4G%2Fgc3Ah8BK4FdgLfCHS8i3wLvAJmCFme0a1lgAvJ73FQfgAmfApKxvKvCm9290SRCRYohEXBKj500%2Bpt9rzOwDSUO2zoPAW32blQhgOrDBzHYxs%2FHA%2FMwY%2FtlFW5%2B99zYwEbhDEhL3LArA2c6AS4CPnYANLQQOtjWMAL7SesBrcc%2BiYGbHu6j%2Fw%2F9Y10JUt01rJPV4Ku5ZFIB93LhJxyMh29rkGWQcx8d9iwFwpR9UUhAPLw8wGHXI9V%2Fv9UmR990d9y0CitaAZcHgiWBhhQdIkgypRyfVSB7hVTN7Dvg%2BrKf3VxUZFAHHOXHplpMtmGNmh8ivA9Pc54vISLzeU5sNHONrKrBSVCnpyaVgety%2F5zCzO8NtCcoDLgdWp1sDbg3zcsKeV9zgz4f6%2FFNcFRrG%2BtiTcf%2Bew8xeyQnz5%2Fv8Fmea2c4%2Bb0qa56qu583%2Bd55LipjQzBcjXGWkHmndZXH%2FngNY2iIBWwQuwC0%2BT2rwMnAPsNj7VprZ7mH%2BbWFdMWI1cD9wUT63JwDO8Jv%2BKei2bICMlkJYpbZ7AlcAv8s2OFFNqAs8nknDO8BpwGTgdr99hcw5A9YAJytJiucZdnjYe1hm5XPdTpGcUtsfXCpk0MSs%2BUpulDgByz3bW%2Bfzm3X8WQTnXkPMUAJ1k%2ByLme0dzzSsAE4Crga%2BaWFAavIGKT5I7QHgRkV3wAw3mJqn%2BTJ4%2Ba3nTfjEJUAh98R4pmEFcKYXMGb4jXUV7OQYID7o956nzTOlVvE8PYHHADJK7%2FsBO93etrRE%2FCJgAnApcG48y7BDVhu4HjhCv4GHshuNIfGQWrbei0VGgRGeFi%2FJDi7vsDXxjq0pnvj7MrDXxH2KB3C%2B4nrgx0HahtQkOb8B73kQ1a88NuLglSK5v0GphN%2F6XXGdIjGYmr1cnBO1BbFtzeeWXf8TFKYCnwPXxbEE4Fj3Ct14BgU%2Fa5VFxvWKgmp0maGaFscF4LFubj81f%2BfauF5RMLN9Vef3wy5sGR%2BnosYAEWLH5mu%2BFNcsDnJ5ftjlZrZjGLtwKLfvTUZTlaSx%2BZrFwZMZ3fAXMTtL3wVaiItN3iFVfZQzpMqS2uR8zeKgCNAP%2FFnOAGA3%2FwTWJv65O5TB%2Bw542pn1pRtW%2FHfvc%2F6BoPqd354koM8lAieGGmFkQIoONa7fKp0pTZ7r8b7GHwb2679jYQAOdgJU99sr67%2B5g%2FiLWY%2F4156m7O3zlBbPA%2B4FHlWC1X%2BnQuGfrtNnMFV2Ui2vqfK0MEB4QwUNH09ZnsbO03%2BYxD2Kh74Kiwgn5FOv7siCN4XM2Hye8vkF%2FixXOUvVpbj2iAFwVfqQkTGjLQNUn9RAtUHpvUS%2B73P6iIZ%2FEte%2FxswCnmhJgJK4q1%2F1wKaGMGrhhi759%2BTWlgJnxbmjElKLTB1WKWmK0eKohnuIxa4OE%2BL4doERX82pqKioqKjYPvAfwsicZeOJHu4AAAAASUVORK5CYII%3D" alt="Komi Store" /></a>&nbsp;&nbsp;
  <a href="https://www.amazon.com/gp/product/B0HR5TXL8R"><img src="https://img.shields.io/badge/Amazon%20Appstore-Outdated-B91C1C?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjxwYXRoIGQ9Ik0xMy45NTggMTAuMDljMCAxLjIzMi4wMjkgMi4yNTYtLjU5MSAzLjM1MS0uNTAyLjg5MS0xLjMwMSAxLjQzOC0yLjE4NiAxLjQzOC0xLjIxNCAwLTEuOTIyLS45MjQtMS45MjItMi4yOTIgMC0yLjY5MiAyLjQxNS0zLjE4MiA0LjctMy4xODJ2LjY4NXptMy4xODYgNy43MDVhLjY2LjY2IDAgMCAxLS43NTMuMDc3Yy0xLjA1OS0uODc5LTEuMjQ4LTEuMjg3LTEuODI5LTIuMTI1LTEuNzQ3IDEuNzgxLTIuOTg0IDIuMzEzLTUuMjQ5IDIuMzEzLTIuNjc5IDAtNC43NjUtMS42NTMtNC43NjUtNC45NjIgMC0yLjU4NSAxLjQwMi00LjM0NSAzLjM5Ny01LjIwNiAxLjcyOS0uNzU5IDQuMTQ3LS44OTYgNS45OTktMS4xMDZ2LS40MTNjMC0uNzU5LjA1OC0xLjY1Ni0uMzg5LTIuMzEzLS4zODktLjU4OC0xLjEzMi0uODI5LTEuNzg3LS44MjktMS4yMTQgMC0yLjI5Ny42MjItMi41NjEgMS45MTItLjA1NC4yODctLjI2NS41Ny0uNTUzLjU4NGwtMy4wOTEtLjMzMmMtLjI2LS4wNTgtLjU0OS0uMjY5LS40NzYtLjY2OUM1Ljc2NCAxLjY1MyA5LjIxOC41IDEyLjIzOC41YzEuNTQ2IDAgMy41NjcuNDEyIDQuNzg2IDEuNTg0IDEuNTQ2IDEuNDQ1IDEuMzk4IDMuMzcgMS4zOTggNS40Njl2NC45NTNjMCAxLjQ5LjYxOSAyLjE0MiAxLjIwMSAyLjk0OC4yMDQuMjg3LjI0OS42MzEtLjAxMS44NDYtLjY1LjU0My0xLjgwNSAxLjU1MS0yLjQ0MSAyLjExNGwtLjAyNy0uMDE5em0yLjUxNSAyLjA5NEMxNy4wNzggMjEuNzk3IDEzLjM0IDIyLjggMTAuMTIzIDIyLjhjLTQuNTEzIDAtOC41NzMtMS42NjktMTEuNjQ0LTQuNDQ0LS4yNDEtLjIxOC0uMDI2LS41MTUuMjY1LS4zNDYgMy4zMTQgMS45MyA3LjQxMiAzLjA5MSAxMS42NDUgMy4wOTEgMi44NTUgMCA1Ljk5NS0uNTkxIDguODgxLTEuODE3LjQzNi0uMTg2LjgwMS4yODYuMzg5LjYwNXpNMjEuNDgzIDE4Ljg4NmMtLjMzLS40MjItMi4xODYtLjItMy4wMTktLjEwMS0uMjU0LjAzLS4yOTMtLjE5LS4wNjQtLjM1IDEuNDc3LTEuMDM5IDMuOS0uNzM5IDQuMTgzLS4zOTEuMjgzLjM1LS4wNzUgMi43NzgtMS40NjIgMy45MzctLjIxMy4xNzgtLjQxNS4wODMtLjMyMS0uMTUzLjMxMi0uNzguOTgzLTIuNTIuNjgzLTIuOTQyeiIvPjwvc3ZnPg%3D%3D" alt="Amazon Appstore (Outdated)" /></a>
</p>

---

## ⚡ Quick-Guides

<details>
<summary><b>⚡ Shizuku 3-Step Silent Update Setup</b></summary>

1. Install **[Shizuku](https://shizuku.rikka.app/)** and start it via Wireless Debugging (Android 11+) or ADB ([Setting up Shizuku](https://github.com/DurchEins/apkup/wiki/Setting-up-Shizuku)).
2. Open ApkUp **Setup ➔ Installation** and enable **Shizuku Auto-Update**.
3. Grant Shizuku permission when prompted — ApkUp now installs updates silently in the background ([Shizuku & Background Installation](https://github.com/DurchEins/apkup/wiki/Shizuku-%26-Background-Installation)).

</details>

<details>
<summary><b>🛡️ SHA-256 &amp; VirusTotal Verification</b></summary>

1. Download the `.apk` and `SHA256SUMS.txt` from **[GitHub Releases](https://github.com/DurchEins/apkup/releases/latest)**.
2. Verify locally:
   ```bash
   sha256sum -c SHA256SUMS.txt --ignore-missing
   ```
3. ApkUp also verifies APK Signature Scheme v2/v3 certificates and `SHA256SUMS` digests automatically during self-updates ([SHA-256 & Signature Verification Guide](https://github.com/DurchEins/apkup/wiki/SHA-256-%26-Signature-Verification)).

</details>

<details>
<summary><b>🔨 Local Build from Source</b></summary>

```bash
git clone https://github.com/DurchEins/apkup.git
cd apkup
./gradlew testDebugUnitTest assembleDebug
```

</details>

---

## 🌍 Community Translations

| Language | Status | Contribute |
| :--- | :---: | :--- |
| English (`EN`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values/strings.xml) |
| German (`DE`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-de/strings.xml) |
| Spanish (`ES`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-es/strings.xml) |
| French (`FR`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-fr/strings.xml) |
| Italian (`IT`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-it/strings.xml) |
| Japanese (`JA`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-ja/strings.xml) |
| Portuguese (`PT`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-pt/strings.xml) |
| Russian (`RU`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-ru/strings.xml) |
| Simplified Chinese (`ZH-CN`) | ✅ 100% | [Edit on GitHub](https://github.com/DurchEins/apkup/blob/main/app/src/main/res/values-zh-rCN/strings.xml) |

> **Spot a typo? Fix it in 30 seconds:** [Submit a translation improvement](.github/ISSUE_TEMPLATE/translation.yml)

---

## 📖 Troubleshooting & Wiki

Need help configuring sources or resolving connection errors? Visit the official **[ApkUp Wiki](https://github.com/DurchEins/apkup/wiki)** for step-by-step guides:

* [Troubleshooting Source Errors](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors)
* [Configuring GitHub, GitLab & Codeberg Personal Access Tokens](https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens)
* [Setting up Shizuku & Optional Root (`su`) for Silent Updates](https://github.com/DurchEins/apkup/wiki/Setting-up-Shizuku)
* [Obtainium Migration & Sync Guide](https://github.com/DurchEins/apkup/wiki/Obtainium-Migration-&-Sync-Guide)
* [F-Droid & Index V2 Repositories](https://github.com/DurchEins/apkup/wiki/F-Droid-&-Index-V2-Repositories)

---

## ☕ Support ApkUp

If ApkUp saves you time keeping your apps up to date, consider supporting its ongoing development. Every contribution directly helps fund new features, Android compatibility testing, and regular updates:

<br>

<p align="center">
  <a href="https://buymeacoffee.com/durcheins"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="203" height="57" /></a>&nbsp;&nbsp;&nbsp;&nbsp;
  <a href="https://coindrop.to/durcheins"><img src=".github/assets/coindrop-button.png" alt="Coindrop.to me" width="229" height="57" /></a>
</p>

<br>

> [!TIP]
> **Prefer to support ApkUp for free?** Every bit of visibility helps:
>
> * ⭐ **[Star the repository on GitHub](https://github.com/DurchEins/apkup)** (or **[GitLab](https://gitlab.com/DurchEins1/apkup)**)
> * 💬 **Rate & review** ApkUp on **[Zapstore](https://zapstore.dev/apps/com.apkup)**, **[AlternativeTo](https://alternativeto.net/software/apkup/about/)**, **[APKPure](https://apkpure.com/apkup/com.apkup)**, **[itch.io](https://durcheins.itch.io/apkup)**, or **[Appteka](https://appteka.store/app/c0cr341070)**
> * 🐛 **[Report bugs](https://github.com/DurchEins/apkup/issues)**, **[suggest features](https://github.com/DurchEins/apkup/issues)**, or share ApkUp with friends!

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

<p align="center">
  <sub>Special Thanks: Based on the original open-source project by <a href="https://github.com/rumboalla/apkupdater">rumboalla (APKUpdater)</a>.</sub>
</p>
