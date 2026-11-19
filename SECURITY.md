# Security Policy

---

### English

## Supported Versions

We actively provide security patches for the following versions of ApkUp:

| Version | Supported |
| --- | --- |
| 1.45.x (or newer) | :white_check_mark: |
| 1.44.x | :white_check_mark: |
| < 1.44 | :x: |

## Security Architecture Highlights

ApkUp enforces defense-in-depth verification across every update lifecycle stage:
- **Cryptographic APK Signature Verification:** Before handing any downloaded APK or `.apkm` split bundle to `PackageInstaller` or Shizuku, `ApkIntegrityVerifier` verifies APK Signature Scheme v2/v3 signing certificates against the currently installed application and blocks certificate mismatches or version downgrades.
- **Signed F-Droid / IzzyOnDroid Indexes & SHA-256 Checksums:** Repository indexes (`index-v1.jar`) are verified against pinned 64-hex SHA-256 X.509 signing certificate fingerprints, and downloaded APKs (including ApkUp self-updates via `SHA256SUMS`) are validated against expected SHA-256 digests.
- **Hardened Archive & Storage Isolation:** All downloads reside in private internal app storage (`cacheDir`), `.apkm` split bundles enforce strict Zip-Slip and decompression-bomb limits, and cleartext HTTP traffic as well as cloud backups are disabled.
- **Encrypted Credentials & Host Isolation:** Local preferences and optional tokens are encrypted at rest using Google Tink (`AES-256-GCM` via Android Keystore). Authorization headers are pinned strictly to their target API hosts and stripped across cross-host redirects.

## Reporting a Vulnerability

If you discover a potential security vulnerability within ApkUp (especially regarding APK signature verification, `.apkm` archive extraction, Shizuku IPC / optional Root `su` execution, credential storage, or network transport), please do **NOT** disclose it via public GitHub issues, discussions, or social media.

Instead, please report it confidentially via GitHub's **Private Vulnerability Reporting**:
1. Navigate to [Report a Vulnerability](https://github.com/DurchEins/apkup/security/advisories/new).
2. Detail the issue, steps to reproduce, affected versions, and include a proof-of-concept if available.

We will review incoming reports promptly, investigate the scope, and release a patch before any public disclosure occurs.

---

### Deutsch

## Unterstützte Versionen

Sicherheitsupdates werden aktiv für folgende Versionen von ApkUp bereitgestellt:

| Version | Unterstützt |
| --- | --- |
| 1.45.x (oder neuer) | :white_check_mark: |
| 1.44.x | :white_check_mark: |
| < 1.44 | :x: |

## Sicherheitsarchitektur im Überblick

ApkUp schützt den gesamten Update-Prozess mehrstufig ab:
- **Kryptografische APK-Signaturprüfung:** Vor jeder Übergabe an den Android-`PackageInstaller` oder Shizuku prüft `ApkIntegrityVerifier` die APK-Signaturzertifikate (v2/v3 Signing Scheme) gegen die bereits installierte App und blockiert Signaturabweichungen sowie Versions-Downgrades.
- **Signierte F-Droid-/IzzyOnDroid-Indizes & SHA-256-Prüfsummen:** Repository-Indizes (`index-v1.jar`) werden kryptografisch gegen gepinnte SHA-256-Zertifikats-Fingerprints geprüft; heruntergeladene APKs (einschließlich Selbst-Updates über `SHA256SUMS`) werden gegen erwartete SHA-256-Prüfsummen validiert.
- **Isolierter Speicher & Archiv-Härtung:** Downloads liegen ausschließlich im privaten internen App-Speicher (`cacheDir`), `.apkm`-Bundles sind gegen Zip-Slip und Entpackbomben gehärtet, Klartext-HTTP sowie Cloud-Backups sind deaktiviert.
- **Verschlüsselte Zugangsdaten & Host-Isolierung:** Lokale Einstellungen und optionale API-Tokens werden per Google Tink (`AES-256-GCM` im Android Keystore) verschlüsselt gespeichert und niemals an fremde Redirect-Domains weitergeleitet.

## Sicherheitslücken melden

Solltest du eine Sicherheitslücke in ApkUp entdecken (insbesondere bei der APK-Signaturprüfung, der `.apkm`-Archiv-Entpackung, der Shizuku- / optionalen Root-(`su`)-Ausführung, der Token-Speicherung oder dem Netzwerk-Transport), erstelle bitte **KEIN** öffentliches Issue.

Nutze stattdessen vertraulich GitHubs **Private Vulnerability Reporting**:
1. Öffne [Sicherheitslücke melden](https://github.com/DurchEins/apkup/security/advisories/new).
2. Beschreibe die Schwachstelle mit Reproduktionsschritten, betroffenen Versionen sowie ggf. einem Proof-of-Concept.

Meldungen werden schnellstmöglich geprüft und behoben, bevor Details öffentlich geteilt werden.
