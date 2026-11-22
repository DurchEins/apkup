<a id="top"></a>

# Security Policy

**Last Reviewed:** November 2026

> [!CAUTION]
> **Security vulnerabilities must NEVER be opened as public GitHub Issues, Discussions, or social media posts.**
> Disclosing unpatched issues publicly puts ApkUp users at risk. Use GitHub's confidential advisory workflow below instead.
>
> <a href="https://github.com/DurchEins/apkup/security/advisories/new"><img src="https://img.shields.io/badge/Report%20a%20Vulnerability-Private%20Advisory-DC2626?style=flat&logo=github" alt="Report a Vulnerability via Private Advisory" /></a>

## Supported Versions

We actively provide security patches for the following versions of ApkUp:

| Version | Support Status | Scope |
| :--- | :--- | :--- |
| `1.45.x` (or newer) | ✅ **Full support** | Active bug fixes & security patches |
| `1.44.x` | ⚠️ **Security fixes only** | Critical security patches until next minor release |
| `< 1.44` | ❌ **Unsupported** | Please update to the latest release |

---

## 🔐 Official APK Signing Verification & Single-Artifact Principle

All official **ApkUp** releases follow the **Single-Artifact Principle**: only the release APK built, signed, and published by automated GitHub Actions (`release.yml`) is canonical. All official mirrors (**GitLab Releases**, **Zapstore**, **itch.io**, **APKPure**, **Appteka**, **Komi Store**) and VirusTotal scans distribute and verify this exact same binary.

Always verify that the signing certificate SHA-256 fingerprint matches ApkUp's official key before trusting an APK:

```text
8e9b512371ee965afc7cc91f23490d3a514623b90d483e638bba8a070a2170b4
```

Per-release `.sha256` checksum files (`SHA256SUMS`) are published alongside each release binary on **[GitHub Releases](https://github.com/DurchEins/apkup/releases)** and on the **[ApkUp Website](https://durcheins.github.io/apkup/)**.

---

## 🛡️ Security Architecture & Threat Model

ApkUp enforces defense-in-depth verification across every stage of checking, downloading, and installing updates:

| Layer | Protection Mechanism |
| :--- | :--- |
| **APK Integrity** | `ApkIntegrityVerifier` checks v2/v3 signing certificates against the installed app and blocks mismatches or version downgrades before handing files to `PackageInstaller`, Shizuku, or Root. |
| **Signed Indexes & Hashes** | F-Droid / IzzyOnDroid indexes (`index-v1.jar`) verify against pinned 64-hex SHA-256 X.509 fingerprints; self-updates verify against signed `SHA256SUMS`. |
| **Credentials at Rest** | Preferences and optional API tokens are encrypted locally via **Google Tink (`AES-256-GCM`)** backed by the hardware **Android Keystore**. |
| **Network & Redirects** | HTTPS-only transport (`cleartextTrafficPermitted="false"`). Personal Access Tokens are strictly host-isolated and stripped across cross-host redirects. |

<details>
<summary><b>🔍 Detailed Threat Model, Archive Limits &amp; Data Isolation</b></summary>
<br>

### Threat Model Defenses
- **Split-Bundle Hardening (`.apkm`):** Archive extraction enforces strict canonical path validation against **Zip-Slip** directory traversal as well as hard per-entry and total **decompression-bomb byte limits**.
- **UI & Process Protections:** Sensitive confirmation controls filter obscured touches against **Tapjacking**, external intents harden against Background Activity Launch (**BAL**) restrictions, and local diagnostic logs (`AppJournal`) scrub credentials automatically before export.
- **Privilege Boundaries:** Standard Android `PackageInstaller` requires explicit system confirmation; **Shizuku** runs via scoped ADB binder permissions; optional **Root (`su`)** execution performs explicit pre-checks on target package paths.

### Local Storage & Data Isolation
- **No Cloud Backups:** Cloud backup and device-to-device data extraction are disabled (`android:allowBackup="false"`).
- **Private Cache Isolation:** Downloaded APKs reside exclusively in private internal app storage (`cacheDir`) and are cleaned up automatically unless explicitly exported via *Save APK*.
- **Zero Telemetry:** ApkUp operates strictly client-side with zero analytics SDKs or developer servers. Full data handling details are documented in **[PRIVACY.md](PRIVACY.md)**.

</details>

---

## 📩 Reporting a Vulnerability

If you discover a potential security vulnerability in ApkUp (especially regarding APK signature checks, `.apkm` extraction, Shizuku IPC / optional Root `su` execution, token storage, or network transport), please report it privately via **[GitHub Private Vulnerability Reporting](https://github.com/DurchEins/apkup/security/advisories/new)**.

### What to Include in a Report
- **ApkUp version & Android OS version** (e.g., `1.45.2 (227)` on Android 15)
- **Affected component or update source** (e.g., `ApkIntegrityVerifier`, F-Droid index parser, Shizuku installer, Tink storage)
- **Step-by-step reproduction** or Proof-of-Concept (PoC)
- **Expected security impact** (e.g., signature bypass, arbitrary file write, credential leak)

- **Coordinated Disclosure Policy:** Best-effort acknowledgement within **7 days**; we kindly ask researchers to allow coordinated disclosure until a patched release is published (typically within **30–90 days**).

---

<details>
<summary><b>🇩🇪 Deutsche Zusammenfassung (German Summary)</b></summary>
<br>

> [!CAUTION]
> **Melde Sicherheitslücken NIEMALS als öffentliches GitHub-Issue!** Nutze immer vertraulich das **[private Meldeformular auf GitHub](https://github.com/DurchEins/apkup/security/advisories/new)**.

### Unterstützte Versionen
| Version | Status | Hinweis |
| :--- | :--- | :--- |
| `1.45.x` (oder neuer) | ✅ **Voller Support** | Aktive Bugfixes & Sicherheitsupdates |
| `1.44.x` | ⚠️ **Nur Sicherheitsfixes** | Kritische Sicherheitsupdates bis zum nächsten Minor-Release |
| `< 1.44` | ❌ **Nicht unterstützt** | Bitte auf die aktuellste Version aktualisieren |

### Offizieller Signatur-Fingerprint (SHA-256) & Single-Artifact-Prinzip
Nur die über GitHub Actions signierten Release-APKs auf **GitHub Releases** sind kanonisch (identische Binärdatei & Prüfsumme auf allen offiziellen Mirrors). Offizieller Zertifikat-SHA-256-Fingerprint:

```text
8e9b512371ee965afc7cc91f23490d3a514623b90d483e638bba8a070a2170b4
```

### Sicherheitsarchitektur im Überblick
- **Signatur- & Prüfsummenschutz:** `ApkIntegrityVerifier` prüft v2/v3-Signaturzertifikate vor jeder Installation und blockiert Downgrades sowie abweichende Signaturen; F-Droid-/IzzyOnDroid-Indizes und Selbst-Updates (`SHA256SUMS`) werden kryptografisch geprüft.
- **Verschlüsselte Speicherung & Datenisolierung:** Einstellungen und optionale Tokens liegen per **Google Tink (`AES-256-GCM`)** im Android Keystore verschlüsselt, Cloud-Backups sind deaktiviert (`allowBackup="false"`), Downloads liegen im privaten `cacheDir`. Weitere Details stehen in **[PRIVACY.md](PRIVACY.md)**.
- **Archiv-, Netzwerk- & UI-Härtung:** Schutz vor Zip-Slip und Entpackbomben bei `.apkm`-Bundles, ausschließlich HTTPS mit strikt host-isolierten Tokens, Tapjacking-Schutz und automatische Redaktion sensibler Daten in Diagnose-Logs.
- **Checkliste für Meldungen:** Bitte nenne betroffene ApkUp-/Android-Version, betroffene Komponente/Quelle, Reproduktionsschritte bzw. PoC und mögliche Auswirkungen.
- **Koordinierte Offenlegung (Coordinated Disclosure):** Best-Effort-Rückmeldung innerhalb von **7 Tagen**; wir bitten Sicherheitsforscher um eine koordinierte Offenlegung, bis ein gepatchtes Release veröffentlicht ist (üblicherweise innerhalb von **30–90 Tagen**).

</details>

<p align="right"><kbd><a href="#top">↑ Back to top</a></kbd></p>
